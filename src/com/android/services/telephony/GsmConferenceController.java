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

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;

import android.telecomm.Connection;

/**
 * Maintains a list of all the known GSM connections and implements GSM-specific conference
 * call functionality.
 */
final class GsmConferenceController {
    private static GsmConferenceController sInstance;

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
                @Override
                public void onStateChanged(Connection c, int state) {
                    // No need to recalculate for conference calls, just traditional calls.
                    if (c != mGsmConferenceConnection) {
                        recalculate();
                    }
                }

                /** ${inheritDoc} */
                @Override
                public void onDisconnected(Connection c, int cause, String message) {
                    // When a connection disconnects, make sure to release its parent reference
                    // so that the parent can move to disconnected as well.
                    c.setParentConnection(null);
                }
            };

    /** The known GSM connections. */
    private final List<GsmConnection> mGsmConnections = new ArrayList<>();

    /** The GSM conference connection object. */
    private ConferenceConnection mGsmConferenceConnection;

    static void add(GsmConnection connection) {
        if (sInstance == null) {
            sInstance = new GsmConferenceController();
        }
        connection.addConnectionListener(sInstance.mConnectionListener);
        sInstance.mGsmConnections.add(connection);
        sInstance.recalculate();
    }

    static void remove(GsmConnection connection) {
        if (sInstance != null) {
            connection.removeConnectionListener(sInstance.mConnectionListener);
            sInstance.mGsmConnections.remove(connection);
            sInstance.recalculate();

            if (sInstance.mGsmConnections.size() == 0 &&
                    sInstance.mGsmConferenceConnection == null) {
                sInstance = null;
            }
        }
    }

    static ConferenceConnection createConferenceConnection(Connection rootConnection) {
        if (sInstance != null) {
            if (sInstance.mGsmConferenceConnection == null) {
                sInstance.mGsmConferenceConnection = new ConferenceConnection();
                Log.d(sInstance, "creating the conference connection: %s",
                        sInstance.mGsmConferenceConnection);
            }
            if (rootConnection instanceof GsmConnection) {
                ((GsmConnection) rootConnection).performConference();
            }
            return sInstance.mGsmConferenceConnection;
        }
        return null;
    }

    private void recalculate() {
        recalculateConferenceable();
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mGsmConnections.size());

        List<Connection> activeConnections = new ArrayList<>(mGsmConnections.size());
        List<Connection> backgroundConnections = new ArrayList<>(mGsmConnections.size());

        // Loop through and collect all calls which are active or holding
        for (GsmConnection connection : mGsmConnections) {
            com.android.internal.telephony.Connection radioConnection =
                    connection.getOriginalConnection();
            Log.d(this, "recalc - %s %s",
                    radioConnection == null ? null : radioConnection.getState(), connection);

            if (radioConnection != null) {
                switch(radioConnection.getState()) {
                    case ACTIVE:
                        activeConnections.add(connection);
                        break;
                    case HOLDING:
                        backgroundConnections.add(connection);
                        break;
                    default:
                        connection.setConferenceableConnections(
                                Collections.<Connection>emptyList());
                        break;
                }
            }
        }

        Log.v(this, "active: %d, holding: %d",
                activeConnections.size(), backgroundConnections.size());

        // Go through all the active connections and set the background connections as
        // conferenceable.
        for (Connection connection : activeConnections) {
            connection.setConferenceableConnections(backgroundConnections);
        }

        // Go through all the background connections and set the active connections as
        // conferenceable.
        for (Connection connection : backgroundConnections) {
            connection.setConferenceableConnections(activeConnections);
        }
    }
}
