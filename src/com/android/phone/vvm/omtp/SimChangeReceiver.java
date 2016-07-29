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
import android.content.pm.IPackageManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.VoicemailStatus;
import com.android.phone.settings.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.omtp.sync.OmtpVvmSourceManager;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * This class listens to the {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} and {@link
 * TelephonyIntents#ACTION_SIM_STATE_CHANGED} to determine when a SIM is added, replaced, or
 * removed.
 *
 * When a SIM is added, send an activate SMS. When a SIM is removed, remove the sync accounts and
 * change the status in the voicemail_status table.
 */
public class SimChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "VvmSimChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            VvmLog.w(TAG, "Null action for intent.");
            return;
        }

        switch (action) {
            case TelephonyIntents.ACTION_SIM_STATE_CHANGED:
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE))) {
                    VvmLog.i(TAG, "Sim removed, removing inactive accounts");
                    OmtpVvmSourceManager.getInstance(context).removeInactiveSources();
                }
                break;
            case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                    VvmLog.i(TAG, "Received SIM change for invalid subscription id.");
                    return;
                }
                VvmLog.d(TAG, "Carrier config changed");
                if (UserManager.get(context).isUserUnlocked() && !isCryptKeeperMode()) {
                    processSubId(context, subId);
                } else {
                    VvmLog.d(TAG, "User locked, activation request delayed until unlock");
                    // After the device is unlocked, VvmBootCompletedReceiver will iterate through
                    // all call capable subIds, nothing need to be done here.
                }
                break;
        }
    }

    public static void processSubId(Context context, int subId) {
        PhoneAccountHandle phoneAccount = PhoneAccountHandleConverter.fromSubId(subId);
        if (phoneAccount == null) {
            // This should never happen
            VvmLog.e(TAG, "unable to convert subId " + subId + " to PhoneAccountHandle");
            return;
        }

        OmtpVvmCarrierConfigHelper carrierConfigHelper =
                new OmtpVvmCarrierConfigHelper(context, subId);
        if (carrierConfigHelper.isValid()) {
            if (VisualVoicemailSettingsUtil.isEnabled(context, phoneAccount)) {
                VvmLog.i(TAG, "Sim state or carrier config changed for " + subId);
                // Add a phone state listener so that changes to the communication channels
                // can be recorded.
                OmtpVvmSourceManager.getInstance(context).addPhoneStateListener(
                        phoneAccount);
                carrierConfigHelper.startActivation();
            } else {
                if (carrierConfigHelper.isLegacyModeEnabled()) {
                    // SMS still need to be filtered under legacy mode.
                    VvmLog.i(TAG, "activating SMS filter for legacy mode");
                    carrierConfigHelper.activateSmsFilter();
                }
                // It may be that the source was not registered to begin with but we want
                // to run through the steps to remove the source just in case.
                OmtpVvmSourceManager.getInstance(context).removeSource(phoneAccount);
                VvmLog.v(TAG, "Sim change for disabled account.");
            }
        } else {
            String mccMnc = context.getSystemService(TelephonyManager.class).getSimOperator(subId);
            VvmLog.d(TAG,
                    "visual voicemail not supported for carrier " + mccMnc + " on subId " + subId);
            VoicemailStatus.disable(context, phoneAccount);
        }
    }

    /**
     * CryptKeeper mode is the pre-file based encryption locked state, when the user has selected
     * "Require password to boot" and the device hasn't been unlocked yet during a reboot. {@link
     * UserManager#isUserUnlocked()} will still return true in this mode, but storage in /data and
     * all content providers will not be available(including SharedPreference).
     */
    private static boolean isCryptKeeperMode() {
        try {
            return IPackageManager.Stub.asInterface(ServiceManager.getService("package")).
                    isOnlyCoreApps();
        } catch (RemoteException e) {
        }
        return false;
    }
}