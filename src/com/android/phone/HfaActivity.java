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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Starts and displays status for Hands Free Activation (HFA).
 *
 * This class operates with Hands Free Activation apps. It comes up during activation
 * requests that occur outside of setup wizard and so provides its own UI.
 * It uses {@link HfaLogic} to perform the actual activation and during the process
 * displays a "performing activation..." dialog.  This will remain up until the user
 * chooses to skip the activation (still happens in the background) or the activation
 * is successful.  Upon failure, the dialog also goes away but a subsequent dialog will
 * ask the user if they would like to try again or cancel.
 */
public class HfaActivity extends Activity {
    private static final String TAG = HfaActivity.class.getSimpleName();

    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    public static final int OTASP_UNKNOWN = 0;
    public static final int OTASP_USER_SKIPPED = 1;
    public static final int OTASP_SUCCESS = 2;
    public static final int OTASP_FAILURE = 3;

    private boolean mCanSkip;
    private AlertDialog mDialog;
    private HfaLogic mHfaLogic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (VERBOSE) Log.v(TAG, "onCreate");

        mHfaLogic = new HfaLogic(this.getApplicationContext(), new HfaLogic.HfaLogicCallback() {
            @Override
            public void onSuccess() {
                onHfaSuccess();
            }

            @Override
            public void onError(String error) {
                onHfaError(error);
            }
        });

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
    }

    private void startProvisioning() {
        buildAndShowDialog();
        mHfaLogic.start();
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
                                    sendFinalResponse(OTASP_USER_SKIPPED);
                                }
                            }})
                /*.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface di) {
                        sendFinalResponse(OTASP_USER_SKIPPED);
                    }})*/
                .create();

        // Do not allow user to dismiss dialog unless they are clicking "skip"
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setCancelable(false);

        if (VERBOSE) Log.v(TAG, "showing dialog");
        mDialog.show();
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
                            sendFinalResponse(OTASP_USER_SKIPPED);
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

        sendFinalResponse(OTASP_SUCCESS);
    }

    private void sendFinalResponse(int responseCode) {
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
}
