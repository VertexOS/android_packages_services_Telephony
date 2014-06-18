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
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.Constants;
import com.android.phone.PhoneUtils;
import com.android.phone.sip.SipProfileDb;
import com.android.phone.sip.SipSharedPreferences;
import android.telecomm.Connection;
import android.telecomm.ConnectionRequest;
import android.telecomm.Response;

import java.util.HashMap;

/**
 * Call service that uses the SIP phone.
 */
public class SipConnectionService extends TelephonyConnectionService {
    private static HashMap<String, SipPhone> sSipPhones = new HashMap<String, SipPhone>();

    /** {@inheritDoc} */
    @Override
    public void onCreateConnections(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> callback) {
        new GetSipProfileTask(this, request, callback).execute();
        super.onCreateConnections(request, callback);
    }

    /** {@inheritDoc} */
    @Override
    public void onCreateIncomingConnection(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> callback) {
        super.onCreateIncomingConnection(request, callback);
        // TODO: fill in
    }

    /** {@inheritDoc} */
    @Override
    protected boolean canCall(Uri handle) {
        return shouldUseSipPhone(handle.getScheme(), handle.getSchemeSpecificPart());
    }

    /** {@inheritDoc} */
    @Override
    protected TelephonyConnection onCreateTelephonyConnection(
            ConnectionRequest request,
            Phone phone,
            com.android.internal.telephony.Connection connection) {
        return new SipConnection(connection);
    }

    private boolean shouldUseSipPhone(String scheme, String number) {
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
        SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(this);
        String callOption = sipSharedPreferences.getSipCallOption();
        boolean isRegularNumber = Constants.SCHEME_TEL.equals(scheme)
                && !PhoneNumberUtils.isUriNumber(number);
        if (callOption.equals(Settings.System.SIP_ADDRESS_ONLY) && isRegularNumber) {
            return false;
        }

        // Check if no SIP profiles.
        SipProfileDb sipProfileDb = new SipProfileDb(this);
        if (sipProfileDb.getProfilesCount() == 0 && isRegularNumber) {
            return false;
        }

        return true;
    }

    /**
     * Asynchronously looks up the SIP profile to use for the given call.
     */
    private class GetSipProfileTask extends AsyncTask<Void, Void, SipProfile> {
        private final ConnectionRequest mRequest;
        private final Response<ConnectionRequest, Connection> mResponse;
        private final SipProfileDb mSipProfileDb;
        private final SipSharedPreferences mSipSharedPreferences;

        GetSipProfileTask(
                Context context,
                ConnectionRequest request,
                Response<ConnectionRequest, Connection> response) {
            mRequest = request;
            mResponse = response;
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
            onSipProfileChosen(profile, mRequest, mResponse);
        }
    }

    private void onSipProfileChosen(
            SipProfile profile,
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> response) {
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
        startCallWithPhone(phone, request, response);
    }
}
