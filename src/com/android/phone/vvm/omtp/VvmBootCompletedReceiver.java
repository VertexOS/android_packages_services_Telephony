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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Upon boot iterate through all callable phone account to activate visual voicemail. This happens
 * after the device has been unlocked. {@link android.telephony.CarrierConfigManager#
 * ACTION_CARRIER_CONFIG_CHANGED} can also trigger activation upon boot but it can happen before the
 * device is unlocked and visual voicemail will not be activated.
 *
 * <p>TODO: An additional duplicated activation request will be sent as a result of this receiver,
 * but similar issues is already covered in b/28730056 and a scheduling system should be used to
 * resolve this.
 */
public class VvmBootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "VvmBootCompletedRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Listens to android.intent.action.BOOT_COMPLETED
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            return;
        }

        VvmLog.v(TAG, "processing subId list");
        for (PhoneAccountHandle handle : TelecomManager.from(context)
                .getCallCapablePhoneAccounts()) {
            int subId = PhoneAccountHandleConverter.toSubId(handle);
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                // getCallCapablePhoneAccounts() might return a PhoneAccountHandle with invalid
                // subId if no SIM is inserted. This is intended as it is for emergency calls.
                VvmLog.e(TAG, "phone account " + handle + " has invalid subId " + subId);
                continue;
            }
            VvmLog.v(TAG, "processing subId " + subId);
            SimChangeReceiver.processSubId(context, subId);
        }
    }
}
