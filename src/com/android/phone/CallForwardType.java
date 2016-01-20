/* Copyright (c) 2015, 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.phone;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import android.preference.Preference.OnPreferenceClickListener;


public class CallForwardType extends PreferenceActivity {
    private static final String LOG_TAG = "CallForwardType";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String BUTTON_CF_KEY_VOICE = "button_cf_key_voice";
    private static final String BUTTON_CF_KEY_VIDEO = "button_cf_key_video";

    private Preference mVoicePreference;
    private Preference mVideoPreference;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.d(LOG_TAG, "onCreate..");
        /*Loading CallForward Setting page*/
        addPreferencesFromResource(R.xml.call_forward_type);
        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mPhone = mSubscriptionInfoHelper.getPhone();

        /*Voice Button*/
        mVoicePreference = (Preference) findPreference(BUTTON_CF_KEY_VOICE);
        mVoicePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            /*onClicking Voice Button*/
            public boolean onPreferenceClick(Preference pref) {
                Intent intent = mSubscriptionInfoHelper.getIntent(GsmUmtsCallForwardOptions.class);
                Log.d(LOG_TAG, "Voice button clicked!");
                intent.putExtra(PhoneUtils.SERVICE_CLASS,
                        CommandsInterface.SERVICE_CLASS_VOICE);
                startActivity(intent);
                return true;
            }
        });

         /*Video Button*/
         mVideoPreference = (Preference) findPreference(BUTTON_CF_KEY_VIDEO);
         mVideoPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

             /*onClicking Video Button*/
             public boolean onPreferenceClick(Preference pref) {
                 Intent intent = mSubscriptionInfoHelper.getIntent(GsmUmtsCallForwardOptions.class);
                 Log.d(LOG_TAG, "Video button clicked!");
                 intent.putExtra(PhoneUtils.SERVICE_CLASS,
                        (CommandsInterface.SERVICE_CLASS_DATA_SYNC +
                         CommandsInterface.SERVICE_CLASS_PACKET));
                 startActivity(intent);
                 return true;
             }
        });

        if (!(mPhone.isUtEnabled())) {
             Log.d(LOG_TAG, "Ut Service is not enabled");
            getPreferenceScreen().removePreference(mVideoPreference);
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
}
