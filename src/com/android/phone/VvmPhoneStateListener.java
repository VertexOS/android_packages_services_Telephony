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
package com.android.phone;

import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;

import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncService;
import com.android.phone.vvm.omtp.sync.VoicemailStatusQueryHelper;

/**
 * Check if service is lost and indicate this in the voicemail status.
 */
public class VvmPhoneStateListener extends PhoneStateListener {

    private PhoneAccountHandle mPhoneAccount;
    private Context mContext;
    public VvmPhoneStateListener(Context context, PhoneAccountHandle accountHandle) {
        super(PhoneUtils.getSubIdForPhoneAccountHandle(accountHandle));
        mContext = context;
        mPhoneAccount = accountHandle;
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            VoicemailStatusQueryHelper voicemailStatusQueryHelper =
                    new VoicemailStatusQueryHelper(mContext);
            if (voicemailStatusQueryHelper.isVoicemailSourceConfigured(mPhoneAccount)) {
                if (!voicemailStatusQueryHelper.isNotificationsChannelActive(mPhoneAccount)) {
                    VoicemailContract.Status.setStatus(mContext, mPhoneAccount,
                            VoicemailContract.Status.CONFIGURATION_STATE_OK,
                            VoicemailContract.Status.DATA_CHANNEL_STATE_OK,
                            VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_OK);
                    PhoneGlobals.getInstance().notificationMgr.updateMwi(
                            PhoneUtils.getSubIdForPhoneAccountHandle(mPhoneAccount), false);
                }
            }

            if (OmtpVvmSourceManager.getInstance(mContext).isVvmSourceRegistered(mPhoneAccount)) {
                // If the source is already registered, run a full sync in case something was missed
                // while signal was down.
                Intent serviceIntent = OmtpVvmSyncService.getSyncIntent(
                        mContext, OmtpVvmSyncService.SYNC_FULL_SYNC, mPhoneAccount,
                        true /* firstAttempt */);
                mContext.startService(serviceIntent);
            } else {
                // Otherwise initiate an activation because this means that an OMTP source was
                // recognized but either the activation text was not successfully sent or a response
                // was not received.
                OmtpVvmCarrierConfigHelper carrierConfigHelper = new OmtpVvmCarrierConfigHelper(
                        mContext, PhoneUtils.getSubIdForPhoneAccountHandle(mPhoneAccount));
                carrierConfigHelper.startActivation();
            }
        } else {
            VoicemailContract.Status.setStatus(mContext, mPhoneAccount,
                    VoicemailContract.Status.CONFIGURATION_STATE_OK,
                    VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION,
                    VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        }
    }
}
