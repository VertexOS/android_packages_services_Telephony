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
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneUtils;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmSyncAccountManager;

import java.io.UnsupportedEncodingException;

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
        StringBuilder userData = new StringBuilder();
        StringBuilder messageBody = new StringBuilder();

        for (int i = 0; i < messages.length; i++) {
            messageBody.append(messages[i].getMessageBody());
            userData.append(extractUserData(messages[i]));
        }

        WrappedMessageData messageData = OmtpSmsParser.parse(messageBody.toString());
        if (messageData != null) {
            if (messageData.getPrefix() == OmtpConstants.SYNC_SMS_PREFIX) {
                SyncMessage message = new SyncMessage(messageData);
                //TODO: handle message
            } else if (messageData.getPrefix() == OmtpConstants.STATUS_SMS_PREFIX) {
                StatusMessage message = new StatusMessage(messageData);
                handleStatusMessage(message);
            } else {
                Log.e(TAG, "This should never have happened");
            }
        }
        // Let this fall through: this is not a message we're interested in.
    }

    private String extractUserData(SmsMessage sms) {
        try {
            // OMTP spec does not tell about the encoding. We assume ASCII.
            // UTF-8 sounds safer as it can handle ascii as well as other charsets.
            return new String(sms.getUserData(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("This should have never happened", e);
        }
    }

    private void handleStatusMessage(StatusMessage message) {
        OmtpVvmSyncAccountManager vvmSyncManager = OmtpVvmSyncAccountManager.getInstance(mContext);
        Account account = new Account(mPhoneAccount.getId(),
                OmtpVvmSyncAccountManager.ACCOUNT_TYPE);

        if (!vvmSyncManager.isAccountRegistered(account)) {
            // If the account has not been previously registered, it means that this STATUS sms
            // is a result of the ACTIVATE sms, so register the voicemail source.
            vvmSyncManager.createSyncAccount(account);
            VoicemailContract.Status.setStatus(mContext, mPhoneAccount,
                    VoicemailContract.Status.CONFIGURATION_STATE_OK,
                    VoicemailContract.Status.DATA_CHANNEL_STATE_OK,
                    VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK);
        }
        ContentResolver.requestSync(account, VoicemailContract.AUTHORITY, new Bundle());
    }
}
