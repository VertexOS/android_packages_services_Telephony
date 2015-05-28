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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;
import com.android.phone.vvm.omtp.sms.OmtpStandardMessageSender;
import com.android.phone.vvm.omtp.sms.OmtpCvvmMessageSender;
import com.android.phone.vvm.omtp.sync.OmtpVvmSyncAccountManager;

/**
 * This class listens to the {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} and
 * {@link TelephonyIntents#ACTION_SIM_STATE_CHANGED} to determine when a SIM is added, replaced,
 * or removed.
 *
 * When a SIM is added, send an activate SMS. When a SIM is removed, remove the sync accounts and
 * change the status in the voicemail_status table.
 */
public class SimChangeReceiver extends BroadcastReceiver {
    private final String TAG = "SimChangeReceiver";
    // Whether CVVM is allowed, is currently false until settings to enable/disable vvm are added.
    private boolean CVVM_ALLOWED = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Null action for intent.");
            return;
        }

        switch (action) {
            case TelephonyIntents.ACTION_SIM_STATE_CHANGED:
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))) {
                    Log.i(TAG, "Sim removed, removing inactive accounts");
                    OmtpVvmSyncAccountManager.getInstance(context).removeInactiveAccounts();
                }
                break;
            case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                handleCarrierConfigChange(context, intent);
                break;
        }
    }

    private void handleCarrierConfigChange(Context context, Intent intent) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "Invalid subscriptionId or subscriptionId not provided in intent.");
            return;
        }

        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager == null) {
            Log.w(TAG, "No carrier config service found.");
            return;
        }

        PersistableBundle carrierConfig = carrierConfigManager.getConfigForSubId(subId);
        if (carrierConfig == null) {
            Log.w(TAG, "Empty carrier config.");
            return;
        }
        String vvmType = carrierConfig.getString(
                CarrierConfigManager.STRING_VVM_TYPE, null);

        if (!(TelephonyManager.VVM_TYPE_OMTP.equals(vvmType) ||
                TelephonyManager.VVM_TYPE_CVVM.equals(vvmType))) {
            // This is not an OMTP visual voicemail compatible carrier.
            return;
        }

        int applicationPort = carrierConfig.getInt(
                CarrierConfigManager.INT_VVM_PORT_NUMBER, 0);
        String destinationNumber = carrierConfig.getString(
                CarrierConfigManager.STRING_VVM_DESTINATION_NUMBER);
        if (TextUtils.isEmpty(destinationNumber)) {
            Log.w(TAG, "No destination number for this carrier.");
            return;
        }

        Log.i(TAG, "Requesting VVM activation for subId: " + subId);

        OmtpMessageSender messageSender = null;
        SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        switch (vvmType) {
            case TelephonyManager.VVM_TYPE_OMTP:
                messageSender = new OmtpStandardMessageSender(smsManager, (short) applicationPort,
                        destinationNumber, null, OmtpConstants.PROTOCOL_VERSION1_1, null);
                break;
            case TelephonyManager.VVM_TYPE_CVVM:
                if (CVVM_ALLOWED) {
                    messageSender = new OmtpCvvmMessageSender(smsManager, (short) applicationPort,
                            destinationNumber);
                }
                break;
            default:
                Log.w(TAG, "Unexpected visual voicemail type: "+vvmType);
        }

        // It should be impossible for the messageSender to be null because the two types of vvm
        // were checked earlier.
        if (messageSender != null) {
            messageSender.requestVvmActivation(null);
        }
    }
}