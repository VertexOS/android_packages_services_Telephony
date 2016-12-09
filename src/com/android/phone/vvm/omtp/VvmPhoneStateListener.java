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
package com.android.phone.vvm.omtp;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;

import com.android.phone.PhoneUtils;
import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncService;
import com.android.phone.vvm.omtp.sync.SyncTask;
import com.android.phone.vvm.omtp.sync.VoicemailStatusQueryHelper;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Check if service is lost and indicate this in the voicemail status.
 */
public class VvmPhoneStateListener extends PhoneStateListener {

    private static final String TAG = "VvmPhoneStateListener";

    private PhoneAccountHandle mPhoneAccount;
    private Context mContext;
    private int mPreviousState = -1;

    public VvmPhoneStateListener(Context context, PhoneAccountHandle accountHandle) {
        super(PhoneUtils.getSubIdForPhoneAccountHandle(accountHandle));
        mContext = context;
        mPhoneAccount = accountHandle;
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
        int subId = PhoneAccountHandleConverter.toSubId(mPhoneAccount);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            VvmLog.e(TAG, "onServiceStateChanged on phoneAccount " + mPhoneAccount
                    + " with invalid subId, ignoring");
            return;
        }

        int state = serviceState.getState();
        if (state == mPreviousState || (state != ServiceState.STATE_IN_SERVICE
                && mPreviousState != ServiceState.STATE_IN_SERVICE)) {
            // Only interested in state changes or transitioning into or out of "in service".
            // Otherwise just quit.
            mPreviousState = state;
            return;
        }

        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(mContext, subId);

        if (state == ServiceState.STATE_IN_SERVICE) {
            VoicemailStatusQueryHelper voicemailStatusQueryHelper =
                    new VoicemailStatusQueryHelper(mContext);
            if (voicemailStatusQueryHelper.isVoicemailSourceConfigured(mPhoneAccount)) {
                if (!voicemailStatusQueryHelper.isNotificationsChannelActive(mPhoneAccount)) {
                    VvmLog
                            .v(TAG, "Notifications channel is active for " + subId);
                    helper.handleEvent(VoicemailStatus.edit(mContext, mPhoneAccount),
                        OmtpEvents.NOTIFICATION_IN_SERVICE);
                }
            }

            if (OmtpVvmSourceManager.getInstance(mContext).isVvmSourceRegistered(mPhoneAccount)) {
                VvmLog
                        .v(TAG, "Signal returned: requesting resync for " + subId);
                // If the source is already registered, run a full sync in case something was missed
                // while signal was down.
                SyncTask.start(mContext, mPhoneAccount, OmtpVvmSyncService.SYNC_FULL_SYNC);
            } else {
                VvmLog.v(TAG,
                        "Signal returned: reattempting activation for " + subId);
                // Otherwise initiate an activation because this means that an OMTP source was
                // recognized but either the activation text was not successfully sent or a response
                // was not received.
                helper.startActivation();
            }
        } else {
            VvmLog.v(TAG, "Notifications channel is inactive for " + subId);

            if (!OmtpVvmSourceManager.getInstance(mContext).isVvmSourceRegistered(mPhoneAccount)) {
                return;
            }
            helper.handleEvent(VoicemailStatus.edit(mContext, mPhoneAccount),
                OmtpEvents.NOTIFICATION_SERVICE_LOST);
        }
        mPreviousState = state;
    }
}
