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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.Uri;
import android.telecomm.Connection;
import android.telecomm.ConnectionRequest;
import android.telecomm.ConnectionService;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.Response;
import android.telephony.DisconnectCause;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;

public final class SipConnectionService extends ConnectionService {
    private static final String PREFIX = "[SipConnectionService] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    static PhoneAccountHandle getPhoneAccountHandle(Context context) {
        return new PhoneAccountHandle(
                new ComponentName(context, SipConnectionService.class),
                null /* id */);
    }

    @Override
    protected void onCreateOutgoingConnection(
            final ConnectionRequest request,
            final CreateConnectionResponse<Connection> response) {
        if (VERBOSE) log("onCreateOutgoingConnection, request: " + request);

        SipProfileChooser.Callback callback = new SipProfileChooser.Callback() {
            @Override
            public void onSipChosen(SipProfile profile) {
                if (VERBOSE) log("onCreateOutgoingConnection, onSipChosen: " + profile);
                SipConnection connection = createConnectionForProfile(profile, request);
                if (connection == null) {
                    response.onCancel(request);
                } else {
                    response.onSuccess(request, connection);
                }
            }

            @Override
            public void onSipNotChosen() {
                if (VERBOSE) log("onCreateOutgoingConnection, onSipNotChosen");
                response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, null);
            }

            @Override
            public void onCancelCall() {
                if (VERBOSE) log("onCreateOutgoingConnection, onCancelCall");
                response.onCancel(request);
            }
        };

        SipProfileChooser chooser = new SipProfileChooser(this, callback);
        chooser.start(request.getHandle(), request.getExtras());
    }

    @Override
    protected void onCreateConferenceConnection(
            String token,
            Connection connection,
            Response<String, Connection> response) {
        if (VERBOSE) log("onCreateConferenceConnection, connection: " + connection);
    }

    @Override
    protected void onCreateIncomingConnection(
            ConnectionRequest request,
            CreateConnectionResponse<Connection> response) {
        if (VERBOSE) log("onCreateIncomingConnection, request: " + request);

        if (request.getExtras() == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no extras");
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, null);
            return;
        }

        Intent sipIntent = (Intent) request.getExtras().getParcelable(
                SipUtil.EXTRA_INCOMING_CALL_INTENT);
        if (sipIntent == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no SIP intent");
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, null);
            return;
        }

        SipAudioCall sipAudioCall;
        try {
            sipAudioCall = SipManager.newInstance(this).takeAudioCall(sipIntent, null);
        } catch (SipException e) {
            log("onCreateIncomingConnection, takeAudioCall exception: " + e);
            response.onCancel(request);
            return;
        }

        SipPhone phone = findPhoneForProfile(sipAudioCall.getLocalProfile());
        if (phone == null) {
            phone = createPhoneForProfile(sipAudioCall.getLocalProfile());
        }
        if (phone != null) {
            com.android.internal.telephony.Connection originalConnection = phone.takeIncomingCall(
                    sipAudioCall);
            if (VERBOSE) log("onCreateIncomingConnection, new connection: " + originalConnection);
            if (originalConnection != null) {
                SipConnection connection = new SipConnection(originalConnection);
                response.onSuccess(getConnectionRequestForIncomingCall(request, originalConnection),
                        connection);
            } else {
                if (VERBOSE) log("onCreateIncomingConnection, takingIncomingCall failed");
                response.onCancel(request);
            }
        }
    }

    @Override
    protected void onConnectionAdded(Connection connection) {
        if (VERBOSE) log("onConnectionAdded, connection: " + connection);
        if (connection instanceof SipConnection) {
            ((SipConnection) connection).onAddedToCallService();
        }
    }

    @Override
    protected void onConnectionRemoved(Connection connection) {
        if (VERBOSE) log("onConnectionRemoved, connection: " + connection);
    }

    private SipConnection createConnectionForProfile(
            SipProfile profile,
            ConnectionRequest request) {
        SipPhone phone = findPhoneForProfile(profile);
        if (phone == null) {
            phone = createPhoneForProfile(profile);
        }
        if (phone != null) {
            return startCallWithPhone(phone, request);
        }
        return null;
    }

    private SipPhone findPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("findPhoneForProfile, profile: " + profile);
        for (Connection connection : getAllConnections()) {
            if (connection instanceof SipConnection) {
                SipPhone phone = ((SipConnection) connection).getPhone();
                if (phone != null && phone.getSipUri().equals(profile.getUriString())) {
                    if (VERBOSE) log("findPhoneForProfile, found existing phone: " + phone);
                    return phone;
                }
            }
        }
        if (VERBOSE) log("findPhoneForProfile, no phone found");
        return null;
    }

    private SipPhone createPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("createPhoneForProfile, profile: " + profile);
        try {
            SipManager.newInstance(this).open(profile);
            return (SipPhone) PhoneFactory.makeSipPhone(profile.getUriString());
        } catch (SipException e) {
            log("createPhoneForProfile, exception: " + e);
            return null;
        }
    }

    private SipConnection startCallWithPhone(SipPhone phone, ConnectionRequest request) {
        String number = request.getHandle().getSchemeSpecificPart();
        if (VERBOSE) log("startCallWithPhone, number: " + number);

        try {
            com.android.internal.telephony.Connection originalConnection =
                    phone.dial(number, request.getVideoState());
            return new SipConnection(originalConnection);
        } catch (CallStateException e) {
            log("startCallWithPhone, exception: " + e);
            return null;
        }
    }

    private ConnectionRequest getConnectionRequestForIncomingCall(ConnectionRequest request,
            com.android.internal.telephony.Connection connection) {
        Uri uri = Uri.fromParts(SipUtil.SCHEME_SIP, connection.getAddress(), null);
        return new ConnectionRequest(request.getAccountHandle(), request.getCallId(), uri,
                connection.getNumberPresentation(), request.getExtras(), 0);
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
