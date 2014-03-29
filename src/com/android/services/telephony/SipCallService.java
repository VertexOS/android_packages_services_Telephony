/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import android.content.Context;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.telecomm.CallInfo;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.Constants;
import com.android.phone.PhoneUtils;
import com.android.phone.sip.SipProfileDb;
import com.android.phone.sip.SipSharedPreferences;

import java.util.HashMap;

/**
 * Call service that uses the SIP phone.
 */
public class SipCallService extends BaseTelephonyCallService {
    private static HashMap<String, SipPhone> sSipPhones = new HashMap<String, SipPhone>();

    static boolean shouldSelect(Context context, CallInfo callInfo) {
        Uri uri = callInfo.getHandle();
        return shouldUseSipPhone(context, uri.getScheme(), uri.getSchemeSpecificPart());
    }

    private static boolean shouldUseSipPhone(Context context, String scheme, String number) {
        // Scheme must be "sip" or "tel".
        boolean isKnownCallScheme = Constants.SCHEME_TEL.equals(scheme)
                || Constants.SCHEME_SIP.equals(scheme);
        if (!isKnownCallScheme) {
            return false;
        }

        // Is voip supported
        boolean voipSupported = PhoneUtils.isVoipSupported();
        if (!voipSupported) {
            return false;
        }

        // Check SIP address only
        SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(context);
        String callOption = sipSharedPreferences.getSipCallOption();
        boolean isRegularNumber = Constants.SCHEME_TEL.equals(scheme)
                && !PhoneNumberUtils.isUriNumber(number);
        if (callOption.equals(Settings.System.SIP_ADDRESS_ONLY) && isRegularNumber) {
            return false;
        }

        // Check if no SIP profiles.
        SipProfileDb sipProfileDb = new SipProfileDb(context);
        if (sipProfileDb.getProfilesCount() == 0 && isRegularNumber) {
            return false;
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void isCompatibleWith(CallInfo callInfo) {
        mCallServiceAdapter.setIsCompatibleWith(callInfo.getId(), shouldSelect(this, callInfo));
    }

    /** {@inheritDoc} */
    @Override
    public void call(CallInfo callInfo) {
        new GetSipProfileTask(this, callInfo).execute();
    }

    /** {@inheritDoc} */
    @Override
    public void setIncomingCallId(String callId, Bundle extras) {
        // TODO(santoscordon): fill in.
    }

    /** {@inheritDoc} */
    public void answer(String callId) {
        // TODO(santoscordon): fill in.
    }

    /** {@inheritDoc} */
    public void reject(String callId) {
        // TODO(santoscordon): fill in.
    }

    /**
     * Asynchronously looks up the SIP profile to use for the given call.
     */
    private class GetSipProfileTask extends AsyncTask<Void, Void, SipProfile> {
        private final CallInfo mCallInfo;
        private final SipProfileDb mSipProfileDb;
        private final SipSharedPreferences mSipSharedPreferences;

        GetSipProfileTask(Context context, CallInfo callInfo) {
            mCallInfo = callInfo;
            mSipProfileDb = new SipProfileDb(context);
            mSipSharedPreferences = new SipSharedPreferences(context);
        }

        @Override
        protected SipProfile doInBackground(Void... params) {
            String primarySipUri = mSipSharedPreferences.getPrimaryAccount();
            for (SipProfile profile : mSipProfileDb.retrieveSipProfileList()) {
                if (profile.getUriString().equals(primarySipUri)) {
                    return profile;
                }
            }
            // TODO(sail): Handle non-primary profiles by showing dialog.
            return null;
        }

        @Override
        protected void onPostExecute(SipProfile profile) {
            onSipProfileChosen(profile, mCallInfo);
        }
    }

    private void onSipProfileChosen(SipProfile profile, CallInfo callInfo) {
        SipPhone phone = null;
        if (profile != null) {
            String sipUri = profile.getUriString();
            phone = sSipPhones.get(sipUri);
            if (phone == null) {
                try {
                    SipManager.newInstance(this).open(profile);
                    phone = (SipPhone) PhoneFactory.makeSipPhone(sipUri);
                    sSipPhones.put(sipUri, phone);
                } catch (SipException e) {
                    Log.e(this, e, "Failed to make a SIP phone");
                }
            }
        }
        startCallWithPhone(phone, callInfo);
    }
}
