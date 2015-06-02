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
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.vvm.omtp.sms.OmtpCvvmMessageSender;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;
import com.android.phone.vvm.omtp.sms.OmtpStandardMessageSender;

/**
 * Handle activation and deactivation of a visual voicemail source. This class is necessary to
 * retrieve carrier vvm configuration details before sending the appropriate texts.
 */
public class OmtpVvmCarrierConfigHelper {
    private static final String TAG = "OmtpVvmCarrierConfigHelper";

    public static void startActivation(Context context, int subId) {
        OmtpMessageSender messageSender = getMessageSender(context, subId);
        if (messageSender != null) {
            Log.i(TAG, "Requesting VVM activation for subId: " + subId);
            messageSender.requestVvmActivation(null);
        }
    }

    public static void startDeactivation(Context context, int subId) {
        OmtpMessageSender messageSender = getMessageSender(context, subId);
        if (messageSender != null) {
            Log.i(TAG, "Requesting VVM deactivation for subId: " + subId);
            messageSender.requestVvmDeactivation(null);
        }
    }

    private static OmtpMessageSender getMessageSender(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(TAG, "Invalid subscriptionId or subscriptionId not provided in intent.");
            return null;
        }

        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager == null) {
            Log.w(TAG, "No carrier config service found.");
            return null;
        }

        PersistableBundle carrierConfig = carrierConfigManager.getConfigForSubId(subId);
        if (carrierConfig == null) {
            Log.w(TAG, "Empty carrier config.");
            return null;
        }

        String vvmType = carrierConfig.getString(
                CarrierConfigManager.STRING_VVM_TYPE, null);

        if (!(TelephonyManager.VVM_TYPE_OMTP.equals(vvmType) ||
                TelephonyManager.VVM_TYPE_CVVM.equals(vvmType))) {
            // This is not an OMTP visual voicemail compatible carrier.
            return null;
        }

        int applicationPort = carrierConfig.getInt(
                CarrierConfigManager.INT_VVM_PORT_NUMBER, 0);
        String destinationNumber = carrierConfig.getString(
                CarrierConfigManager.STRING_VVM_DESTINATION_NUMBER);
        if (TextUtils.isEmpty(destinationNumber)) {
            Log.w(TAG, "No destination number for this carrier.");
            return null;
        }

        OmtpMessageSender messageSender = null;
        SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        switch (vvmType) {
            case TelephonyManager.VVM_TYPE_OMTP:
                messageSender = new OmtpStandardMessageSender(smsManager, (short) applicationPort,
                        destinationNumber, null, OmtpConstants.PROTOCOL_VERSION1_1, null);
                break;
            case TelephonyManager.VVM_TYPE_CVVM:
                messageSender = new OmtpCvvmMessageSender(smsManager, (short) applicationPort,
                        destinationNumber);
                break;
            default:
                Log.w(TAG, "Unexpected visual voicemail type: "+vvmType);
        }

        return messageSender;
    }
}