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
 * limitations under the License
 */

package com.android.services.telephony;

import android.net.Uri;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.IConferenceable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Manages conferences for IMS connections.
 */
public class ImsConferenceController {

    /**
     * Conference listener; used to receive notification when a conference has been disconnected.
     */
    private final Conference.Listener mConferenceListener = new Conference.Listener() {
        @Override
        public void onDestroyed(Conference conference) {
            if (Log.VERBOSE) {
                Log.v(ImsConferenceController.class, "onDestroyed: %s", conference);
            }

            mImsConferences.remove(conference);
        }
    };

    /**
     * Ims conference controller connection listener.  Used to respond to changes in state of the
     * Telephony connections the controller is aware of.
     */
    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            Log.v(this, "onStateChanged: %s", Log.pii(c.getAddress()));
            recalculate();
        }

        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            Log.v(this, "onDisconnected: %s", Log.pii(c.getAddress()));
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }

        @Override
        public void onConferenceStarted() {
            Log.v(this, "onConferenceStarted");
            recalculateConference();
        }
    };

    /**
     * The current {@link ConnectionService}.
     */
    private final TelephonyConnectionService mConnectionService;

    /**
     * List of known {@link TelephonyConnection}s.
     */
    private final ArrayList<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    /**
     * List of known {@link ImsConference}s.  Realistically there will only ever be a single
     * concurrent IMS conference.
     */
    private final ArrayList<ImsConference> mImsConferences = new ArrayList<>(1);

    /**
     * Creates a new instance of the Ims conference controller.
     *
     * @param connectionService The current connection service.
     */
    public ImsConferenceController(TelephonyConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /**
     * Adds a new connection to the IMS conference controller.
     *
     * @param connection
     */
    void add(TelephonyConnection connection) {
        // Note: Wrap in Log.VERBOSE to avoid calling connection.toString if we are not going to be
        // outputting the value.
        if (Log.VERBOSE) {
            Log.v(this, "add connection %s", connection);
        }

        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculateConference();
    }

    /**
     * Removes a connection from the IMS conference controller.
     *
     * @param connection
     */
    void remove(Connection connection) {
        if (Log.VERBOSE) {
            Log.v(this, "remove connection: %s", connection);
        }

        mTelephonyConnections.remove(connection);
        recalculateConferenceable();
    }

    /**
     * Triggers both a re-check of conferenceable connections, as well as checking for new
     * conferences.
     */
    private void recalculate() {
        recalculateConferenceable();
        recalculateConference();
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());
        List<IConferenceable> activeConnections = new ArrayList<>(mTelephonyConnections.size());
        List<IConferenceable> backgroundConnections = new ArrayList<>(mTelephonyConnections.size());

        // Loop through and collect all calls which are active or holding
        for (Connection connection : mTelephonyConnections) {
            if (Log.DEBUG) {
                Log.d(this, "recalc - %s %s", connection.getState(), connection);
            }

            switch (connection.getState()) {
                case Connection.STATE_ACTIVE:
                    activeConnections.add(connection);
                    continue;
                case Connection.STATE_HOLDING:
                    backgroundConnections.add(connection);
                    continue;
                default:
                    break;
            }
            connection.setConferenceableConnections(Collections.<Connection>emptyList());
        }

        for (Conference conference : mImsConferences) {
            if (Log.DEBUG) {
                Log.d(this, "recalc - %s %s", conference.getState(), conference);
            }

            switch (conference.getState()) {
                case Connection.STATE_ACTIVE:
                    activeConnections.add(conference);
                    continue;
                case Connection.STATE_HOLDING:
                    backgroundConnections.add(conference);
                    continue;
                default:
                    break;
            }
        }

        Log.v(this, "active: %d, holding: %d", activeConnections.size(),
                backgroundConnections.size());

        // Go through all the active connections and set the background connections as
        // conferenceable.
        for (IConferenceable conferenceable : activeConnections) {
            if (conferenceable instanceof Connection) {
                Connection connection = (Connection) conferenceable;
                connection.setConferenceables(backgroundConnections);
            }
        }

        // Go through all the background connections and set the active connections as
        // conferenceable.
        for (IConferenceable conferenceable : backgroundConnections) {
            if (conferenceable instanceof Connection) {
                Connection connection = (Connection) conferenceable;
                connection.setConferenceables(activeConnections);
            }

        }

        // Set the conference as conferenceable with all the connections
        for (ImsConference conference : mImsConferences) {
            List<Connection> nonConferencedConnections =
                new ArrayList<>(mTelephonyConnections.size());
            for (Connection c : mTelephonyConnections) {
                if (c.getConference() == null) {
                    nonConferencedConnections.add(c);
                }
            }
            if (Log.VERBOSE) {
                Log.v(this, "conference conferenceable: %s", nonConferencedConnections);
            }
            conference.setConferenceableConnections(nonConferencedConnections);
        }
    }

    /**
     * Starts a new ImsConference for a connection which just entered a multiparty state.
     */
    private void recalculateConference() {
        Log.v(this, "recalculateConference");

        Iterator<TelephonyConnection> it = mTelephonyConnections.iterator();
        while (it.hasNext()) {
            TelephonyConnection connection = it.next();

            if (connection.isImsConnection() && connection.getOriginalConnection() != null &&
                    connection.getOriginalConnection().isMultiparty()) {

                startConference(connection);
                it.remove();
            }
        }
    }

    /**
     * Starts a new {@link ImsConference} for the given IMS connection.
     * <p>
     * Creates a new IMS Conference to manage the conference represented by the connection.
     * Internally the ImsConference wraps the radio connection with a new TelephonyConnection
     * which is NOT reported to the connection service and Telecom.
     * <p>
     * Once the new IMS Conference has been created, the connection passed in is held and removed
     * from the connection service (removing it from Telecom).  The connection is put into a held
     * state to ensure that telecom removes the connection without putting it into a disconnected
     * state first.
     *
     * @param connection The connection to the Ims server.
     */
    private void startConference(TelephonyConnection connection) {
        if (Log.VERBOSE) {
            Log.v(this, "Start new ImsConference - connection: %s", connection);
        }

        // Make a clone of the connection which will become the Ims conference host connection.
        // This is necessary since the Connection Service does not support removing a connection
        // from Telecom.  Instead we create a new instance and remove the old one from telecom.
        TelephonyConnection conferenceHostConnection = connection.cloneConnection();

        // Create conference and add to telecom
        ImsConference conference = new ImsConference(mConnectionService, conferenceHostConnection);
        conference.setState(connection.getState());
        mConnectionService.addConference(conference);
        conference.addListener(mConferenceListener);

        // Cleanup TelephonyConnection which backed the original connection and remove from telecom.
        // Use the "Other" disconnect cause to ensure the call is logged to the call log but the
        // disconnect tone is not played.
        connection.removeConnectionListener(mConnectionListener);
        connection.clearOriginalConnection();
        connection.setDisconnected(new DisconnectCause(DisconnectCause.OTHER));
        connection.destroy();
        mImsConferences.add(conference);
    }
}
