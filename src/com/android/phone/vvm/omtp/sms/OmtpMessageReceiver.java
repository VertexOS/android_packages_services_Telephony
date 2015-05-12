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

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.Voicemail;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneUtils;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmSyncAccountManager;
import com.android.phone.vvm.omtp.OmtpVvmSyncService.OmtpVvmSyncAdapter;

/**
 * Receive SMS messages and send for processing by the OMTP visual voicemail source.
 */
public class OmtpMessageReceiver extends BroadcastReceiver {
    private static final String TAG = "OmtpMessageReceiver";

    private Context mContext;
    private PhoneAccountHandle mPhoneAccount;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        mPhoneAccount = PhoneUtils.makePstnPhoneAccountHandle(
                intent.getExtras().getInt(PhoneConstants.PHONE_KEY));

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        StringBuilder messageBody = new StringBuilder();

        for (int i = 0; i < messages.length; i++) {
            messageBody.append(messages[i].getMessageBody());
        }

        WrappedMessageData messageData = OmtpSmsParser.parse(messageBody.toString());
        if (messageData != null) {
            if (messageData.getPrefix() == OmtpConstants.SYNC_SMS_PREFIX) {
                SyncMessage message = new SyncMessage(messageData);
                processSync(message);
            } else if (messageData.getPrefix() == OmtpConstants.STATUS_SMS_PREFIX) {
                StatusMessage message = new StatusMessage(messageData);
                updateAccount(message);
            } else {
                Log.e(TAG, "This should never have happened");
            }
        }
        // Let this fall through: this is not a message we're interested in.
    }

    /**
     * A sync message has two purposes: to signal a new voicemail message, and to indicate the
     * voicemails on the server have changed remotely (usually through the TUI). Save the new
     * message to the voicemail provider if it is the former case and perform a full sync in the
     * latter case.
     *
     * @param message The sync message to extract data from.
     */
    private void processSync(SyncMessage message) {
        switch (message.getSyncTriggerEvent()) {
            case OmtpConstants.NEW_MESSAGE:
                Voicemail voicemail = Voicemail.createForInsertion(
                        message.getTimestampMillis(), message.getSender())
                        .setSourceData(message.getId())
                        .setDuration(message.getLength())
                        .setSourcePackage(mContext.getPackageName())
                        .build();

                VoicemailContract.Voicemails.insert(mContext, voicemail);
                break;
            case OmtpConstants.MAILBOX_UPDATE:
                // Needs a total resync
                Bundle bundle = new Bundle();
                bundle.putBoolean(OmtpVvmSyncAdapter.SYNC_EXTRAS_DOWNLOAD, true);
                ContentResolver.requestSync(
                        new Account(mPhoneAccount.getId(), OmtpVvmSyncAccountManager.ACCOUNT_TYPE),
                        VoicemailContract.AUTHORITY, bundle);
                break;
            case OmtpConstants.GREETINGS_UPDATE:
                // Not implemented in V1
                break;
           default:
               Log.e(TAG, "Unrecognized sync trigger event: " + message.getSyncTriggerEvent());
               break;
        }
    }

    private void updateAccount(StatusMessage message) {
        OmtpVvmSyncAccountManager vvmAccountSyncManager =
                OmtpVvmSyncAccountManager.getInstance(mContext);
        Account account = new Account(mPhoneAccount.getId(),
                OmtpVvmSyncAccountManager.ACCOUNT_TYPE);

        if (!vvmAccountSyncManager.isAccountRegistered(account)) {
            // If the account has not been previously registered, it means that this STATUS sms
            // is a result of the ACTIVATE sms, so register the voicemail source.
            vvmAccountSyncManager.createSyncAccount(account);
            VoicemailContract.Status.setStatus(mContext, mPhoneAccount,
                    VoicemailContract.Status.CONFIGURATION_STATE_OK,
                    VoicemailContract.Status.DATA_CHANNEL_STATE_OK,
                    VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK);

            Bundle bundle = new Bundle();
            bundle.putBoolean(OmtpVvmSyncAdapter.SYNC_EXTRAS_DOWNLOAD, true);
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true);
            ContentResolver.requestSync(account, VoicemailContract.AUTHORITY, bundle);
        }

        // Save the IMAP credentials in the corresponding account object so they are
        // persistent and can be retrieved.
        vvmAccountSyncManager.setAccountCredentialsFromStatusMessage(account, message);
    }
}
