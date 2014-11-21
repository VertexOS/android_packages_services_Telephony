/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.phone;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionController;

import java.util.List;

public class CdmaCallOptions extends PreferenceActivity {
    private static final String LOG_TAG = "CdmaCallOptions";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    public static final int CALL_WAITING = 7;
    private static final String BUTTON_VP_KEY = "button_voice_privacy_key";
    public static final String CALL_FORWARD_INTENT = "org.codeaurora.settings.CDMA_CALL_FORWARDING";
    public static final String CALL_WAITING_INTENT = "org.codeaurora.settings.CDMA_CALL_WAITING";


    private static boolean isActivityPresent(Context context, String intentName) {
        PackageManager pm = context.getPackageManager();
        // check whether the target handler exist in system
        Intent intent = new Intent(intentName);
        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : list){
            if ((resolveInfo.activityInfo.applicationInfo.flags &
                    ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCdmaCallForwardingActivityPresent(Context context) {
        return isActivityPresent(context, CALL_FORWARD_INTENT);
    }

    public static boolean isCdmaCallWaitingActivityPresent(Context context) {
        return isActivityPresent(context, CALL_WAITING_INTENT);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.cdma_call_privacy);

        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        subInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.labelCdmaMore_with_label);

        PersistableBundle carrierConfig;
        if (subInfoHelper.hasSubId()) {
            carrierConfig = PhoneGlobals.getInstance().getCarrierConfigForSubId(
                    subInfoHelper.getSubId());
        } else {
            carrierConfig = PhoneGlobals.getInstance().getCarrierConfig();
        }

        Phone phone = subInfoHelper.getPhone();
        Log.d(LOG_TAG, "sub id = " + subInfoHelper.getSubId() + " phone id = " +
                phone.getPhoneId());

        PreferenceScreen prefScreen = getPreferenceScreen();
        if (phone.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA
                || carrierConfig.getBoolean(CarrierConfigManager.KEY_VOICE_PRIVACY_DISABLE_UI_BOOL)) {
            CdmaVoicePrivacyCheckBoxPreference prefPri = (CdmaVoicePrivacyCheckBoxPreference)
                    prefScreen.findPreference("button_voice_privacy_key");
            if (prefPri != null) {
                prefPri.setEnabled(false);
            }
        }

        if (phone.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA
                || !carrierConfig.getBoolean(CarrierConfigManager.KEY_CDMA_CW_CF_ENABLED_BOOL)
                || !isCdmaCallWaitingActivityPresent(this)) {
            Log.d(LOG_TAG, "Disabled CW CF");
            PreferenceScreen prefCW = (PreferenceScreen)
                    prefScreen.findPreference("button_cw_key");
            if (prefCW != null) {
                prefCW.setEnabled(false);
            }
            PreferenceScreen prefCF = (PreferenceScreen)
                    prefScreen.findPreference("button_cf_expand_key");
            if (prefCF != null) {
                prefCF.setEnabled(false);
            }
        } else {
            Log.d(LOG_TAG, "Enabled CW CF");
            PreferenceScreen prefCW = (PreferenceScreen)
                    prefScreen.findPreference("button_cw_key");

            if (prefCW != null) {
                prefCW.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                Intent intent = new Intent(CALL_WAITING_INTENT);
                                intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, phone.getSubId());
                                startActivity(intent);
                                return true;
                            }
                        });
            }
            PreferenceScreen prefCF = (PreferenceScreen)
                    prefScreen.findPreference("button_cf_expand_key");
            if (prefCF != null) {
                prefCF.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                Intent intent = new Intent(CALL_FORWARD_INTENT);
                                intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, phone.getSubId());
                                startActivity(intent);
                                return true;
                            }
                        });
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(BUTTON_VP_KEY)) {
            return true;
        }
        return false;
    }

}
