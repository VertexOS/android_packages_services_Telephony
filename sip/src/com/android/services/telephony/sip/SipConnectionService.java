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

package com.android.services.telephony.sip;

import android.content.Context;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.telecomm.Connection;
import android.telecomm.ConnectionRequest;
import android.telecomm.ConnectionService;
import android.telecomm.Response;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;

import java.util.HashMap;

public class SipConnectionService extends ConnectionService {
    private static final String PREFIX = "[SipConnectionService] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    private class GetSipProfileTask extends AsyncTask<Void, Void, SipProfile> {
        private final ConnectionRequest mRequest;
        private final OutgoingCallResponse mResponse;
        private final SipProfileDb mSipProfileDb;
        private final SipSharedPreferences mSipSharedPreferences;

        GetSipProfileTask(
                Context context,
                ConnectionRequest request,
                OutgoingCallResponse response) {
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

    @Override
    protected void onCreateConnections(
            ConnectionRequest request,
            OutgoingCallResponse<Connection> callback) {
        if (VERBOSE) log("onCreateConnections, request: " + request);
        new GetSipProfileTask(this, request, callback).execute();
    }

    @Override
    protected void onCreateConferenceConnection(
            String token,
            Connection connection,
            Response<String, Connection> callback) {
        if (VERBOSE) log("onCreateConferenceConnection, connection: " + connection);
    }

    @Override
    protected void onCreateIncomingConnection(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> callback) {
        if (VERBOSE) log("onCreateIncomingConnection, request: " + request);
    }

    @Override
    protected void onConnectionAdded(Connection connection) {
        if (VERBOSE) log("onConnectionAdded, connection: " + connection);
    }

    @Override
    protected void onConnectionRemoved(Connection connection) {
        if (VERBOSE) log("onConnectionRemoved, connection: " + connection);
    }

    private void onSipProfileChosen(
            SipProfile profile,
            ConnectionRequest request,
            OutgoingCallResponse response) {
        if (profile != null) {
            String sipUri = profile.getUriString();
            SipPhone phone = null;
            try {
                SipManager.newInstance(this).open(profile);
                phone = (SipPhone) PhoneFactory.makeSipPhone(sipUri);
                startCallWithPhone(phone, request, response);
            } catch (SipException e) {
                log("Failed to make a SIP phone: " + e);
                response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED,
                        "Failed to make a SIP phone: " + e);
            }
        } else {
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED,
                    "Failed to find SIP profile");
        }
    }

    protected void startCallWithPhone(
            Phone phone,
            ConnectionRequest request,
            OutgoingCallResponse<Connection> response) {
        String number = request.getHandle().getSchemeSpecificPart();
        try {
            com.android.internal.telephony.Connection connection =
                    phone.dial(number, request.getVideoState());
            SipConnection sipConnection = new SipConnection(connection);
            response.onSuccess(request, sipConnection);
        } catch (CallStateException e) {
            log("Call to Phone.dial failed with exception: " + e);
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED,
                    "Call to Phone.dial failed with exception: " + e);
        }
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
