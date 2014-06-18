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

import com.android.internal.telephony.Call;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.List;

import android.telecomm.Connection;

/**
 * Maintains a list of all the known GSM connections and implements GSM-specific conference
 * call functionality.
 */
public class GsmConferenceController {

    private final Connection.Listener mConnectionListener =
            new Connection.ListenerBase() {
                @Override
                public void onStateChanged(Connection c, int state) {
                    // No need to recalculate for conference calls, just traditional calls.
                    if (c != mGsmConferenceConnection) {
                        recalculate();
                    }
                }

                /** ${inheritDoc} */
                @Override
                public void onDisconnected(
                        Connection c, int cause, String message) {
                    // When a connection disconnects, make sure to release its parent reference
                    // so that the parent can move to disconnected as well.
                    c.setParentConnection(null);
                }

            };

    /** The known GSM connections. */
    private final List<GsmConnection> mGsmConnections = new ArrayList<>();

    /** The GSM conference connection object. */
    private ConferenceConnection mGsmConferenceConnection;

    public void add(GsmConnection connection) {
        connection.addConnectionListener(mConnectionListener);
        mGsmConnections.add(connection);
        recalculate();
    }

    public void remove(GsmConnection connection) {
        connection.removeConnectionListener(mConnectionListener);
        mGsmConnections.remove(connection);
        recalculate();
    }

    public ConferenceConnection createConferenceConnection(Connection rootConnection) {
        if (mGsmConferenceConnection == null) {
            mGsmConferenceConnection = new ConferenceConnection();
            Log.d(this, "creating the conference connection: %s", mGsmConferenceConnection);
        }
        rootConnection.conference();
        return mGsmConferenceConnection;
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculate() {
        Log.v(this, "recalculateGsmConferenceState");
        for (GsmConnection connection : mGsmConnections) {
            Log.d(this, "recalc - %s", connection);
            boolean isConferenceCapable = false;
            com.android.internal.telephony.Connection radioConnection =
                    connection.getOriginalConnection();
            if (radioConnection != null) {

                // First calculate to see if we are in the conference call. We only support a
                // single active conference call on PSTN, which makes things a little easier.
                if (mGsmConferenceConnection != null) {
                    if (radioConnection.getCall().isMultiparty()) {
                        connection.setParentConnection(mGsmConferenceConnection);
                    } else {
                        connection.setParentConnection(null);
                    }
                }

                boolean callIsActive = radioConnection.getState() == Call.State.ACTIVE;
                boolean isConferenced =
                        callIsActive && radioConnection.getCall().isMultiparty();
                // TODO: The below does not work when we use PhoneFactory.getGsmPhone() -- the
                // phone from getGsmPhone() erroneously reports it has no background calls.
                boolean hasBackgroundCall =
                        radioConnection.getCall().getPhone().getBackgroundCall().hasConnections();
                Log.d(this, "recalc: active: %b, is_conf: %b, has_bkgd: %b",
                        callIsActive, isConferenced, hasBackgroundCall);
                // We only set conference capable on:
                // 1) Active calls,
                // 2) which are not already part of a conference call
                // 3) and there exists a call on HOLD
                isConferenceCapable = callIsActive && !isConferenced && hasBackgroundCall;
            }

            connection.setIsConferenceCapable(isConferenceCapable);
        }
    }
}
