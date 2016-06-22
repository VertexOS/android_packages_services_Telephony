/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.phone.vvm.omtp.sms;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.Voicemail;

import com.android.phone.PhoneGlobals;
import com.android.phone.settings.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpEvents;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncService;
import com.android.phone.vvm.omtp.sync.VoicemailsQueryHelper;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Receive SMS messages and send for processing by the OMTP visual voicemail source.
 */
public class OmtpMessageReceiver extends BroadcastReceiver {
    private static final String TAG = "OmtpMessageReceiver";

    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!UserManager.get(context).isUserUnlocked()) {
            VvmLog.i(TAG, "Received message on locked device");
            // A full sync will happen after the device is unlocked, so nothing need to be done.
            return;
        }

        mContext = context;
        int subId = intent.getExtras().getInt(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID);
        PhoneAccountHandle phone = PhoneAccountHandleConverter.fromSubId(subId);

        if (phone == null) {
            VvmLog.i(TAG, "Received message for null phone account");
            return;
        }

        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(mContext, subId);
        if (!VisualVoicemailSettingsUtil.isVisualVoicemailEnabled(mContext, phone)) {
            if (helper.isLegacyModeEnabled()) {
                LegacyModeSmsHandler.handle(context, intent, phone);
            } else {
                VvmLog.i(TAG, "Received vvm message for disabled vvm source.");
            }
            return;
        }

        String eventType = intent.getExtras()
                .getString(VoicemailContract.EXTRA_VOICEMAIL_SMS_PREFIX);
        Bundle data = intent.getExtras().getBundle(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS);

        if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
            SyncMessage message = new SyncMessage(data);

            VvmLog.v(TAG, "Received SYNC sms for " + subId +
                    " with event " + message.getSyncTriggerEvent());
            processSync(phone, message);
        } else if (eventType.equals(OmtpConstants.STATUS_SMS_PREFIX)) {
            VvmLog.v(TAG, "Received Status sms for " + subId);
            StatusMessage message = new StatusMessage(data);
            if (message.getProvisioningStatus().equals(OmtpConstants.SUBSCRIBER_READY)) {
                updateSource(phone, subId, message);
            } else {
                VvmLog.v(TAG, "Subscriber not ready, start provisioning");
                mContext.startService(OmtpProvisioningService.getProvisionIntent(mContext, intent));
            }
        } else {
            VvmLog.e(TAG, "Unknown prefix: " + eventType);
        }
    }

    /**
     * A sync message has two purposes: to signal a new voicemail message, and to indicate the
     * voicemails on the server have changed remotely (usually through the TUI). Save the new
     * message to the voicemail provider if it is the former case and perform a full sync in the
     * latter case.
     *
     * @param message The sync message to extract data from.
     */
    private void processSync(PhoneAccountHandle phone, SyncMessage message) {
        Intent serviceIntent = null;
        switch (message.getSyncTriggerEvent()) {
            case OmtpConstants.NEW_MESSAGE:
                Voicemail.Builder builder = Voicemail.createForInsertion(
                        message.getTimestampMillis(), message.getSender())
                        .setPhoneAccount(phone)
                        .setSourceData(message.getId())
                        .setDuration(message.getLength())
                        .setSourcePackage(mContext.getPackageName());
                Voicemail voicemail = builder.build();

                VoicemailsQueryHelper queryHelper = new VoicemailsQueryHelper(mContext);
                if (queryHelper.isVoicemailUnique(voicemail)) {
                    Uri uri = VoicemailContract.Voicemails.insert(mContext, voicemail);
                    voicemail = builder.setId(ContentUris.parseId(uri)).setUri(uri).build();
                    serviceIntent = OmtpVvmSyncService.getSyncIntent(mContext,
                            OmtpVvmSyncService.SYNC_DOWNLOAD_ONE_TRANSCRIPTION, phone,
                            voicemail, true /* firstAttempt */);
                }
                break;
            case OmtpConstants.MAILBOX_UPDATE:
                serviceIntent = OmtpVvmSyncService.getSyncIntent(
                        mContext, OmtpVvmSyncService.SYNC_DOWNLOAD_ONLY, phone,
                        true /* firstAttempt */);
                break;
            case OmtpConstants.GREETINGS_UPDATE:
                // Not implemented in V1
                break;
            default:
                VvmLog.e(TAG,
                        "Unrecognized sync trigger event: " + message.getSyncTriggerEvent());
               break;
        }

        if (serviceIntent != null) {
            mContext.startService(serviceIntent);
        }
    }

    private void updateSource(PhoneAccountHandle phone, int subId, StatusMessage message) {
        OmtpVvmSourceManager vvmSourceManager =
                OmtpVvmSourceManager.getInstance(mContext);

        if (OmtpConstants.SUCCESS.equals(message.getReturnCode())) {
            OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(mContext, subId);
            helper.handleEvent(OmtpEvents.CONFIG_REQUEST_STATUS_SUCCESS);

            // Save the IMAP credentials in preferences so they are persistent and can be retrieved.
            VisualVoicemailSettingsUtil.setVisualVoicemailCredentialsFromStatusMessage(
                    mContext,
                    phone,
                    message);

            // Add the source to indicate that it is active.
            vvmSourceManager.addSource(phone);

            Intent serviceIntent = OmtpVvmSyncService.getSyncIntent(
                    mContext, OmtpVvmSyncService.SYNC_FULL_SYNC, phone,
                    true /* firstAttempt */);
            mContext.startService(serviceIntent);

            PhoneGlobals.getInstance().clearMwiIndicator(subId);
        } else {
            VvmLog.e(TAG, "Visual voicemail not available for subscriber.");
        }
    }
}
