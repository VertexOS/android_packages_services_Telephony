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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.telecomm.Conference;
import android.telecomm.Connection;

import com.android.internal.telephony.Call;

/**
 * Maintains a list of all the known GSM connections and implements GSM-specific conference
 * call functionality.
 */
final class GsmConferenceController {
    private final Connection.Listener mConnectionListener = new Connection.Listener() {
                @Override
                public void onStateChanged(Connection c, int state) {
                    recalculate();
                }

                /** ${inheritDoc} */
                @Override
                public void onDisconnected(Connection c, int cause, String message) {
                    recalculate();
                }
            };

    /** The known GSM connections. */
    private final List<GsmConnection> mGsmConnections = new ArrayList<>();

    private final TelephonyConnectionService mConnectionService;

    public GsmConferenceController(TelephonyConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /** The GSM conference connection object. */
    private Conference mGsmConference;

    void add(GsmConnection connection) {
        mGsmConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculate();
    }

    void remove(GsmConnection connection) {
        connection.removeConnectionListener(mConnectionListener);
        mGsmConnections.remove(connection);
        recalculate();
    }

    private void recalculate() {
        recalculateConferenceable();
        recalculateConference();
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
        // TODO: Do not allow conferencing of already conferenced connections.
    }

    private void recalculateConference() {
        Set<GsmConnection> conferencedConnections = new HashSet<>();

        for (GsmConnection connection : mGsmConnections) {
            com.android.internal.telephony.Connection radioConnection =
                connection.getOriginalConnection();

            if (radioConnection != null) {
                Call.State state = radioConnection.getState();
                Call call = radioConnection.getCall();
                if ((state == Call.State.ACTIVE || state == Call.State.HOLDING) &&
                        (call != null && call.isMultiparty())) {
                    conferencedConnections.add(connection);
                }
            }
        }

        Log.d(this, "Recalculate conference calls %s %s.",
                mGsmConference, conferencedConnections);

        if (conferencedConnections.size() < 2) {
            Log.d(this, "less than two conference calls!");
            // No more connections are conferenced, destroy any existing conference.
            if (mGsmConference != null) {
                Log.d(this, "with a conference to destroy!");
                mGsmConference.destroy();
                mGsmConference = null;
            }
        } else {
            if (mGsmConference != null) {
                List<Connection> existingConnections = mGsmConference.getConnections();
                // Remove any that no longer exist
                for (Connection connection : existingConnections) {
                    if (!conferencedConnections.contains(connection)) {
                        mGsmConference.removeConnection(connection);
                    }
                }

                // Add any new ones
                for (Connection connection : conferencedConnections) {
                    if (!existingConnections.contains(connection)) {
                        mGsmConference.addConnection(connection);
                    }
                }
            } else {
                mGsmConference = new GsmConference(null);
                for (Connection connection : conferencedConnections) {
                    mGsmConference.addConnection(connection);
                }
                mConnectionService.addConference(mGsmConference);
            }
        }
    }
}
