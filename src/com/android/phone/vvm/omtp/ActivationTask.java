/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.vvm.omtp;

import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.telecom.PhoneAccountHandle;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.android.phone.Assert;
import com.android.phone.PhoneGlobals;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.protocol.VisualVoicemailProtocol;
import com.android.phone.vvm.omtp.scheduling.BaseTask;
import com.android.phone.vvm.omtp.scheduling.RetryPolicy;
import com.android.phone.vvm.omtp.sms.StatusMessage;
import com.android.phone.vvm.omtp.sms.StatusSmsFetcher;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncService;
import com.android.phone.vvm.omtp.sync.SyncTask;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Task to activate the visual voicemail service. A request to activate VVM will be sent to the
 * carrier, which will respond with a STATUS SMS. The credentials will be updated from the SMS. If
 * the user is not provisioned provisioning will be attempted. Activation happens when the phone
 * boots, the SIM is inserted, signal returned when VVM is not activated yet, and when the carrier
 * spontaneously sent a STATUS SMS.
 */
public class ActivationTask extends BaseTask {

    private static final String TAG = "VvmActivationTask";

    private static final int RETRY_TIMES = 4;
    private static final int RETRY_INTERVAL_MILLIS = 5_000;

    private static final String EXTRA_MESSAGE_DATA_BUNDLE = "extra_message_data_bundle";

    @Nullable
    private static DeviceProvisionedObserver sDeviceProvisionedObserver;

    private final RetryPolicy mRetryPolicy;

    private Bundle mMessageData;

    public ActivationTask() {
        super(TASK_ACTIVATION);
        mRetryPolicy = new RetryPolicy(RETRY_TIMES, RETRY_INTERVAL_MILLIS);
        addPolicy(mRetryPolicy);
    }

    /**
     * Has the user gone through the setup wizard yet.
     */
    private static boolean isDeviceProvisioned(Context context) {
        return Settings.Global.getInt(
            context.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) == 1;
    }

    /**
     * @param messageData The optional bundle from {@link android.provider.VoicemailContract#
     * EXTRA_VOICEMAIL_SMS_FIELDS}, if the task is initiated by a status SMS. If null the task will
     * request a status SMS itself.
     */
    public static void start(Context context, int subId, @Nullable Bundle messageData) {
        if (!isDeviceProvisioned(context)) {
            VvmLog.i(TAG, "Activation requested while device is not provisioned, postponing");
            // Activation might need information such as system language to be set, so wait until
            // the setup wizard is finished. The data bundle from the SMS will be re-requested upon
            // activation.
            queueActivationAfterProvisioned(context, subId);
            return;
        }

        Intent intent = BaseTask.createIntent(context, ActivationTask.class, subId);
        if (messageData != null) {
            intent.putExtra(EXTRA_MESSAGE_DATA_BUNDLE, messageData);
        }
        context.startService(intent);
    }

    public void onCreate(Context context, Intent intent, int flags, int startId) {
        super.onCreate(context, intent, flags, startId);
        mMessageData = intent.getParcelableExtra(EXTRA_MESSAGE_DATA_BUNDLE);
    }

    @Override
    public Intent createRestartIntent() {
        Intent intent = super.createRestartIntent();
        // mMessageData is discarded, request a fresh STATUS SMS for retries.
        return intent;
    }

    @Override
    @WorkerThread
    public void onExecuteInBackgroundThread() {
        Assert.isNotMainThread();
        int subId = getSubId();

        PhoneAccountHandle phoneAccountHandle = PhoneAccountHandleConverter.fromSubId(subId);
        if (phoneAccountHandle == null) {
            // This should never happen
            VvmLog.e(TAG, "null phone account for subId " + subId);
            return;
        }

        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(getContext(), subId);
        if (!helper.isValid()) {
            VvmLog.i(TAG, "VVM not supported on subId " + subId);
            VoicemailStatus.disable(getContext(), phoneAccountHandle);
            return;
        }

        // OmtpVvmCarrierConfigHelper can start the activation process; it will pass in a vvm
        // content provider URI which we will use.  On some occasions, setting that URI will
        // fail, so we will perform a few attempts to ensure that the vvm content provider has
        // a good chance of being started up.
        if (!VoicemailStatus.edit(getContext(), phoneAccountHandle)
            .setType(helper.getVvmType())
            .apply()) {
            VvmLog.e(TAG, "Failed to configure content provider - " + helper.getVvmType());
            fail();
        }
        VvmLog.i(TAG, "VVM content provider configured - " + helper.getVvmType());

        if (!OmtpVvmSourceManager.getInstance(getContext())
                .isVvmSourceRegistered(phoneAccountHandle)) {
            // This account has not been activated before during the lifetime of this boot.
            VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(getContext(),
                    phoneAccountHandle);
            if (preferences.getString(OmtpConstants.SERVER_ADDRESS, null) == null) {
                // Only show the "activating" message if activation has not been completed before.
                // Subsequent activations are more of a status check and usually does not
                // concern the user.
                helper.handleEvent(VoicemailStatus.edit(getContext(), phoneAccountHandle),
                        OmtpEvents.CONFIG_ACTIVATING);
            } else {
                // The account has been activated on this device before. Pretend it is already
                // activated. If there are any activation error it will overwrite this status.
                helper.handleEvent(VoicemailStatus.edit(getContext(), phoneAccountHandle),
                        OmtpEvents.CONFIG_ACTIVATING_SUBSEQUENT);
            }

        }
        if (!hasSignal(getContext(), subId)) {
            VvmLog.i(TAG, "Service lost during activation, aborting");
            // Restore the "NO SIGNAL" state since it will be overwritten by the CONFIG_ACTIVATING
            // event.
            helper.handleEvent(VoicemailStatus.edit(getContext(), phoneAccountHandle),
                    OmtpEvents.NOTIFICATION_SERVICE_LOST);
            // Don't retry, a new activation will be started after the signal returned.
            return;
        }

        helper.activateSmsFilter();
        VoicemailStatus.Editor status = mRetryPolicy.getVoicemailStatusEditor();

        VisualVoicemailProtocol protocol = helper.getProtocol();

        Bundle data;
        if (mMessageData != null) {
            // The content of STATUS SMS is provided to launch this task, no need to request it
            // again.
            data = mMessageData;
        } else {
            try (StatusSmsFetcher fetcher = new StatusSmsFetcher(getContext(), subId)) {
                protocol.startActivation(helper, fetcher.getSentIntent());
                // Both the fetcher and OmtpMessageReceiver will be triggered, but
                // OmtpMessageReceiver will just route the SMS back to ActivationTask, which will be
                // rejected because the task is still running.
                data = fetcher.get();
            } catch (TimeoutException e) {
                // The carrier is expected to return an STATUS SMS within STATUS_SMS_TIMEOUT_MILLIS
                // handleEvent() will do the logging.
                helper.handleEvent(status, OmtpEvents.CONFIG_STATUS_SMS_TIME_OUT);
                fail();
                return;
            } catch (CancellationException e) {
                VvmLog.e(TAG, "Unable to send status request SMS");
                fail();
                return;
            } catch (InterruptedException | ExecutionException | IOException e) {
                VvmLog.e(TAG, "can't get future STATUS SMS", e);
                fail();
                return;
            }
        }

        StatusMessage message = new StatusMessage(data);
        VvmLog.d(TAG, "STATUS SMS received: st=" + message.getProvisioningStatus()
                + ", rc=" + message.getReturnCode());

        if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_READY)) {
            VvmLog.d(TAG, "subscriber ready, no activation required");
            updateSource(getContext(), phoneAccountHandle, getSubId(), status, message);
        } else {
            if (helper.supportsProvisioning()) {
                VvmLog.i(TAG, "Subscriber not ready, start provisioning");
                helper.startProvisioning(this, phoneAccountHandle, status, message, data);

            } else if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_NEW)) {
                VvmLog.i(TAG, "Subscriber new but provisioning is not supported");
                // Ignore the non-ready state and attempt to use the provided info as is.
                // This is probably caused by not completing the new user tutorial.
                updateSource(getContext(), phoneAccountHandle, getSubId(), status, message);
            } else {
                VvmLog.i(TAG, "Subscriber not ready but provisioning is not supported");
                helper.handleEvent(status, OmtpEvents.CONFIG_SERVICE_NOT_AVAILABLE);
                PhoneGlobals.getInstance().setShouldCheckVisualVoicemailConfigurationForMwi(subId, false);
            }
        }
    }

    public static void updateSource(Context context, PhoneAccountHandle phone, int subId,
            VoicemailStatus.Editor status, StatusMessage message) {
        OmtpVvmSourceManager vvmSourceManager =
                OmtpVvmSourceManager.getInstance(context);

        if (OmtpConstants.SUCCESS.equals(message.getReturnCode())) {
            OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, subId);
            helper.handleEvent(status, OmtpEvents.CONFIG_REQUEST_STATUS_SUCCESS);

            // Save the IMAP credentials in preferences so they are persistent and can be retrieved.
            VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phone);
            message.putStatus(prefs.edit()).apply();

            // Add the source to indicate that it is active.
            vvmSourceManager.addSource(phone);

            SyncTask.start(context, phone, OmtpVvmSyncService.SYNC_FULL_SYNC);
            // Remove the message waiting indicator, which is a sticky notification for traditional
            // voicemails.
            PhoneGlobals.getInstance()
                    .setShouldCheckVisualVoicemailConfigurationForMwi(subId, true);
            PhoneGlobals.getInstance().clearMwiIndicator(subId);
        } else {
            VvmLog.e(TAG, "Visual voicemail not available for subscriber.");
        }
    }

    private static boolean hasSignal(Context context, int subId) {
        return context.getSystemService(TelephonyManager.class)
                .getServiceStateForSubscriber(subId).getState() == ServiceState.STATE_IN_SERVICE;
    }

    private static void queueActivationAfterProvisioned(Context context, int subId) {
        if (sDeviceProvisionedObserver == null) {
            sDeviceProvisionedObserver = new DeviceProvisionedObserver(context);
            context.getContentResolver()
                .registerContentObserver(Settings.Global.getUriFor(Global.DEVICE_PROVISIONED),
                    false, sDeviceProvisionedObserver);
        }
        sDeviceProvisionedObserver.addSubId(subId);
    }

    private static class DeviceProvisionedObserver extends ContentObserver {

        private final Context mContext;
        private final Set<Integer> mSubIds = new HashSet<>();

        private DeviceProvisionedObserver(Context context) {
            super(null);
            mContext = context;
        }

        public void addSubId(int subId) {
            mSubIds.add(subId);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (isDeviceProvisioned(mContext)) {
                VvmLog.i(TAG, "device provisioned, resuming activation");
                for (int subId : mSubIds) {
                    start(mContext, subId, null);
                }
                mContext.getContentResolver().unregisterContentObserver(sDeviceProvisionedObserver);
                sDeviceProvisionedObserver = null;
            }
        }
    }
}
