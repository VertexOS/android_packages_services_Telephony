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
import android.net.Uri;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.Constants;

import java.util.Collection;
import java.util.HashSet;

import android.telecomm.CallState;
import android.telecomm.ConnectionRequest;
import android.telecomm.Response;

/**
 * Connnection service that uses GSM.
 */
public class GsmConnectionService extends PstnConnectionService {

    private final android.telecomm.Connection.Listener mConnectionListener =
            new android.telecomm.Connection.ListenerBase() {
                @Override
                public void onStateChanged(android.telecomm.Connection c, int state) {
                    // No need to recalculate for conference calls, just traditional calls.
                    if (c != mConferenceConnection) {
                        recalculateConferenceState();
                    }
                }

                /** ${inheritDoc} */
                @Override
                public void onDisconnected(
                        android.telecomm.Connection c, int cause, String message) {
                    // When a connection disconnects, make sure to release its parent reference
                    // so that the parent can move to disconnected as well.
                    c.setParentConnection(null);
                }

            };

    /** The conferenc connection object. */
    private ConferenceConnection mConferenceConnection;

    /** {@inheritDoc} */
    @Override
    protected Phone getPhone() {
        return PhoneFactory.getDefaultPhone();
    }

    /** {@inheritDoc} */
    @Override
    protected boolean canCall(Uri handle) {
        return canCall(this, handle);
    }

    // TODO: Refactor this out when CallServiceSelector is deprecated
    /* package */ static boolean canCall(Context context, Uri handle) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM
                && Constants.SCHEME_TEL.equals(handle.getScheme());
    }

    /** {@inheritDoc} */
    @Override
    protected TelephonyConnection onCreateTelephonyConnection(
            ConnectionRequest request, Connection connection) {
        return new GsmConnection(getPhone(), connection);
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionAdded(android.telecomm.Connection connection) {
        connection.addConnectionListener(mConnectionListener);
        recalculateConferenceState();
    }

    /** {@inheritDoc} */
    @Override
    public void onConnectionRemoved(android.telecomm.Connection connection) {
        connection.removeConnectionListener(mConnectionListener);
        recalculateConferenceState();
    }

    /** {@inheritDoc} */
    @Override
    public void onCreateConferenceConnection(
            String token,
            android.telecomm.Connection telecommConnection,
            Response<String, android.telecomm.Connection> callback) {
        if (mConferenceConnection == null) {
            mConferenceConnection = new ConferenceConnection();
            Log.d(this, "creating the conference connection: %s", mConferenceConnection);
        }
        callback.onResult(token, mConferenceConnection);
        telecommConnection.conference();
    }

    /**
     * Calculates the conference-capable state of all connections in this connection service.
     */
    private void recalculateConferenceState() {
        Log.v(this, "recalculateConferenceState");
        Collection<android.telecomm.Connection> allConnections = this.getAllConnections();
        for (android.telecomm.Connection connection : new HashSet<>(allConnections)) {
            Log.d(this, "recalc - %s", connection);
            if (connection instanceof GsmConnection) {
                boolean isConferenceCapable = false;
                Connection radioConnection = ((GsmConnection) connection).getOriginalConnection();
                if (radioConnection != null) {

                    // First calculate to see if we are in the conference call. We only support a
                    // single active conference call on PSTN, which makes things a little easier.
                    if (mConferenceConnection != null) {
                        if (radioConnection.getCall().isMultiparty()) {
                            connection.setParentConnection(mConferenceConnection);
                        } else {
                            connection.setParentConnection(null);
                        }
                    }

                    boolean callIsActive = radioConnection.getState() == Call.State.ACTIVE;
                    boolean isConferenced =
                            callIsActive && radioConnection.getCall().isMultiparty();
                    boolean hasBackgroundCall = getPhone().getBackgroundCall().hasConnections();
                    Log.d(this, "recalc: active: %b, is_conf: %b, has_bkgd: %b",
                            callIsActive, isConferenced, hasBackgroundCall);
                    // We only set conference capable on:
                    // 1) Active calls,
                    // 2) which are not already part of a conference call
                    // 3) and there exists a call on HOLD
                    isConferenceCapable = callIsActive && !isConferenced && hasBackgroundCall;
                }

                ((GsmConnection) connection).setIsConferenceCapable(isConferenceCapable);
            }
        }
    }
}
