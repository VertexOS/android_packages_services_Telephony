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
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneUtils;
import com.android.phone.settings.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;

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
                    OmtpVvmSourceManager.getInstance(context).removeInactiveSources();
                }
                break;
            case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                OmtpVvmCarrierConfigHelper carrierConfigHelper =
                        new OmtpVvmCarrierConfigHelper(context, subId);

                if (carrierConfigHelper.isOmtpVvmType()) {
                    PhoneAccountHandle phoneAccount = PhoneUtils.makePstnPhoneAccountHandle(
                            SubscriptionManager.getPhoneId(subId));

                    if (carrierConfigHelper.isEnabledByDefault()) {
                        VisualVoicemailSettingsUtil.setVisualVoicemailEnabled(
                                context, phoneAccount, true, false);
                    }

                    if (carrierConfigHelper.isEnabledByDefault() ||
                            VisualVoicemailSettingsUtil.isEnabledByUserOverride(
                                    context, phoneAccount)) {
                        carrierConfigHelper.startActivation();
                    } else {
                        // It may be that the source was not registered to begin with but we want
                        // to run through the steps to remove the source just in case.
                        VisualVoicemailSettingsUtil.setVisualVoicemailEnabled(
                                context, phoneAccount, false, false);
                        OmtpVvmSourceManager.getInstance(context).removeSource(phoneAccount);
                        carrierConfigHelper.startDeactivation();
                    }
                }
                break;
        }
    }
}