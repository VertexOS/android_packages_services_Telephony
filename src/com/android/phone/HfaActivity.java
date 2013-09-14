/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

/**
 * Starts and displays status for Hands Free Activation (HFA).
 *
 * This class operates with Hands Free Activation apps.
 * It starts by broadcasting the intent com.android.action.START_HFA.
 * An HFA app will pick that up and start the HFA process.
 * If it fails it return ERROR_HFA Intent and upon success returns COMPLETE_HFA.
 *
 * If successful, we bounce the radio so that the service picks up the new number. This is also
 * necessary for the setup wizard to pick up the successful activation so that it can continue
 * past the welcome screen. Once the radio is back on we send back the pendingIntent to setup
 * wizard and destroy the activity.
 *
 * If there is an error, we do not bounce the radio but still send the pending intent back to
 * the wizard (with a failure code).
 *
 * The user has an option to skip activation. If that happens, we go back to the setup
 * wizard.
 *
 * TODO(klp): We need system-only permissions for the HFA intents.
 * TODO(klp): Should be full screen activity instead of dialogs.
 * TODO(klp): Currently display the error code instead of the error string resource.
 * TODO(klp): Need to check the system to ensure someone is listening for the intent
 *            before we send it.  Should there be a timeout?  5 minutes?
 */
public class HfaActivity extends Activity {
    private static final String TAG = HfaActivity.class.getSimpleName();

    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final String ACTION_START = "com.android.action.START_HFA";
    private static final String ACTION_ERROR = "com.android.action.ERROR_HFA";
    private static final String ACTION_CANCEL = "com.android.action.CANCEL_HFA";
    private static final String ACTION_COMPLETE = "com.android.action.COMPLETE_HFA";

    private static final int SERVICE_STATE_CHANGED = 1;

    public static final int OTASP_UNKNOWN = 0;
    public static final int OTASP_USER_SKIPPED = 1;
    public static final int OTASP_SUCCESS = 2;
    public static final int OTASP_FAILURE = 3;

    public static final int NOT_WAITING = 0;
    public static final int WAITING_FOR_RADIO_OFF = 1;
    public static final int WAITING_FOR_RADIO_ON = 2;

    private int mPhoneMonitorState = NOT_WAITING;
    private boolean mCanSkip;
    private AlertDialog mDialog;
    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (VERBOSE) Log.v(TAG, "onCreate");

        startHfaIntentReceiver();
        startProvisioning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (VERBOSE) Log.v(TAG, "onDestroy");

        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void startProvisioning() {
        buildAndShowDialog();

        sendHfaCommand(ACTION_START);
    }

    private void buildAndShowDialog() {
        mCanSkip = true;

        mDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.ota_hfa_activation_title)
                .setMessage(R.string.ota_hfa_activation_dialog_message)
                .setPositiveButton(R.string.ota_skip_activation_dialog_skip_label,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface di, int which) {
                                if (mCanSkip) {
                                    sendHfaCommand(ACTION_CANCEL);
                                    sendResponseToSetupWizard(OTASP_USER_SKIPPED);
                                }
                            }})
                /*.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface di) {
                        sendResponseToSetupWizard(OTASP_USER_SKIPPED);
                    }})*/
                .create();

        if (VERBOSE) Log.v(TAG, "showing dialog");
        mDialog.show();
    }

    private void sendHfaCommand(String action) {
        if (VERBOSE) Log.v(TAG, "Sending command: " + action);
        sendBroadcast(new Intent(action));
    }

    private void onHfaError(String errorMsg) {
        mDialog.dismiss();

        AlertDialog errorDialog = new AlertDialog.Builder(this)
            .setMessage(errorMsg)
            .setPositiveButton(R.string.ota_skip_activation_dialog_skip_label,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int which) {
                            di.dismiss();
                            sendResponseToSetupWizard(OTASP_USER_SKIPPED);
                        }
                    })
            .setNegativeButton(R.string.ota_try_again,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int which) {
                            di.dismiss();
                            startProvisioning();
                        }
                    })
            .create();

        errorDialog.show();
    }

    private void onHfaSuccess() {
        // User can no longer skip after success.
        mCanSkip = false;

        // We need to restart the modem upon successful activation
        // so that it can acquire a number and ensure setupwizard will
        // know about this success through phone state changes.

        bounceRadio();
    }

    private void bounceRadio() {
        final Phone phone = PhoneGlobals.getInstance().getPhone();
        phone.registerForServiceStateChanged(mHandler, SERVICE_STATE_CHANGED, null);

        mPhoneMonitorState = WAITING_FOR_RADIO_OFF;
        phone.setRadioPower(false);
        onServiceStateChange(phone.getServiceState());
    }

    private void onServiceStateChange(ServiceState state) {
        final boolean radioIsOff = state.getVoiceRegState() == ServiceState.STATE_POWER_OFF;
        final Phone phone = PhoneGlobals.getInstance().getPhone();

        if (VERBOSE) Log.v(TAG, "Radio is off: " + radioIsOff);

        if (mPhoneMonitorState == WAITING_FOR_RADIO_OFF) {
            if (radioIsOff) {
                mPhoneMonitorState = WAITING_FOR_RADIO_ON;
                phone.setRadioPower(true);
            }
        } else if (mPhoneMonitorState == WAITING_FOR_RADIO_ON) {
            if (!radioIsOff) {
                mPhoneMonitorState = NOT_WAITING;
                phone.unregisterForServiceStateChanged(mHandler);

                // We have successfully bounced the radio.
                // Time to go back to the setup wizard.
                sendResponseToSetupWizard(OTASP_SUCCESS);
            }
        }
    }

    private void sendResponseToSetupWizard(int responseCode) {
        final PendingIntent otaResponseIntent = getIntent().getParcelableExtra(
                OtaUtils.EXTRA_OTASP_RESULT_CODE_PENDING_INTENT);

        if (otaResponseIntent != null) {
            final Intent extraStuff = new Intent();
            extraStuff.putExtra(OtaUtils.EXTRA_OTASP_RESULT_CODE, responseCode);

            try {
                if (VERBOSE) Log.v(TAG, "Sending OTASP confirmation with result code: "
                        + responseCode);
                otaResponseIntent.send(this, 0 /* resultCode (not used) */, extraStuff);
            } catch (CanceledException e) {
                Log.e(TAG, "Pending Intent canceled");
            }
        }

        finish();
    }

    public void startHfaIntentReceiver() {
        final IntentFilter filter = new IntentFilter(ACTION_COMPLETE);
        filter.addAction(ACTION_ERROR);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(ACTION_ERROR)) {
                    onHfaError(intent.getStringExtra("errorCode"));
                } else if (action.equals(ACTION_COMPLETE)) {
                    if (VERBOSE) Log.v(TAG, "Hfa Successful");
                    onHfaSuccess();
                }
            }
        };

        registerReceiver(mReceiver, filter);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SERVICE_STATE_CHANGED:
                    ServiceState state = (ServiceState) ((AsyncResult) msg.obj).result;
                    onServiceStateChange(state);
                    break;
                default:
                    break;
            }
        }
    };

}
