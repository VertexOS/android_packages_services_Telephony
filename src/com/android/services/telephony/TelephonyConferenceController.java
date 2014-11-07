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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.net.Uri;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.gsm.GsmConnection;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;

/**
 * Maintains a list of all the known TelephonyConnections connections and controls GSM and
 * default IMS conference call behavior. This functionality is characterized by the support of
 * two top-level calls, in contrast to a CDMA conference call which automatically starts a
 * conference when there are two calls.
 */
final class TelephonyConferenceController {
    private static final int TELEPHONY_CONFERENCE_MAX_SIZE = 5;

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            recalculate();
        }

        /** ${inheritDoc} */
        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }

        /**
         * Handles notifications from an connection that participant(s) in a conference have changed
         * state.
         *
         * @param c The connection.
         * @param participants The participant information.
         */
        @Override
        public void onConferenceParticipantsChanged(Connection c,
                List<ConferenceParticipant> participants) {

            if (c == null) {
                return;
            }
            Log.v(this, "onConferenceParticipantsChanged: %d participants", participants.size());
            TelephonyConnection telephonyConnection = (TelephonyConnection) c;
            handleConferenceParticipantsUpdate(telephonyConnection, participants);
        }
    };

    /** The known connections. */
    private final List<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    /**
     * The known conference participant connections.  The HashMap is keyed by endpoint Uri.
     */
    private final HashMap<Uri, ConferenceParticipantConnection> mConferenceParticipantConnections =
            new HashMap<>();

    private final TelephonyConnectionService mConnectionService;

    public TelephonyConferenceController(TelephonyConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /** The TelephonyConference connection object. */
    private TelephonyConference mTelephonyConference;

    void add(TelephonyConnection connection) {
        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculate();
    }

    void remove(Connection connection) {
        connection.removeConnectionListener(mConnectionListener);

        if (connection instanceof ConferenceParticipantConnection) {
            mConferenceParticipantConnections.remove(connection);
        } else {
            mTelephonyConnections.remove(connection);
        }

        recalculate();
    }

    private void recalculate() {
        recalculateConferenceable();
        recalculateConference();
    }

    private boolean isFullConference(Conference conference) {
        return conference.getConnections().size() >= TELEPHONY_CONFERENCE_MAX_SIZE;
    }

    private boolean participatesInFullConference(Connection connection) {
        return connection.getConference() != null &&
                isFullConference(connection.getConference());
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());

        List<Connection> activeConnections = new ArrayList<>(mTelephonyConnections.size());
        List<Connection> backgroundConnections = new ArrayList<>(mTelephonyConnections.size());

        // Loop through and collect all calls which are active or holding
        for (Connection connection : mTelephonyConnections) {
            Log.d(this, "recalc - %s %s", connection.getState(), connection);

            if (!participatesInFullConference(connection)) {
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
            }

            connection.setConferenceableConnections(Collections.<Connection>emptyList());
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

        // Set the conference as conferenceable with all the connections
        if (mTelephonyConference != null && !isFullConference(mTelephonyConference)) {
            List<Connection> nonConferencedConnections =
                    new ArrayList<>(mTelephonyConnections.size());
            for (TelephonyConnection c : mTelephonyConnections) {
                if (c.getConference() == null) {
                    nonConferencedConnections.add(c);
                }
            }
            mTelephonyConference.setConferenceableConnections(nonConferencedConnections);
        }

        // TODO: Do not allow conferencing of already conferenced connections.
    }

    private void recalculateConference() {
        Set<Connection> conferencedConnections = new HashSet<>();

        int numGsmConnections = 0;
        int numImsConnections = 0;

        for (TelephonyConnection connection : mTelephonyConnections) {
            com.android.internal.telephony.Connection radioConnection =
                connection.getOriginalConnection();

            if (radioConnection != null) {
                Call.State state = radioConnection.getState();
                Call call = radioConnection.getCall();
                if ((state == Call.State.ACTIVE || state == Call.State.HOLDING) &&
                        (call != null && call.isMultiparty())) {

                    if (radioConnection instanceof GsmConnection) {
                        numGsmConnections++;
                    } else if (radioConnection instanceof ImsPhoneConnection) {
                        numImsConnections++;
                    }
                    conferencedConnections.add(connection);
                }
            }
        }

        Log.d(this, "Recalculate conference calls %s %s.",
                mTelephonyConference, conferencedConnections);

        boolean wasParticipantsAdded = false;

        // If the number of telephony connections drops below the limit, the conference can be
        // considered terminated.
        // We must have less than 2 GSM connections and less than 1 IMS connection.
        if (numGsmConnections < 2 && numImsConnections < 1) {
            Log.d(this, "not enough connections to be a conference!");

            // The underlying telephony connections have been disconnected -- disconnect the
            // conference participants now.
            disconnectConferenceParticipants();

            // No more connections are conferenced, destroy any existing conference.
            if (mTelephonyConference != null) {
                Log.d(this, "with a conference to destroy!");
                mTelephonyConference.destroy();
                mTelephonyConference = null;
            }
        } else {
            if (mTelephonyConference != null) {
                List<Connection> existingConnections = mTelephonyConference.getConnections();
                // Remove any that no longer exist
                for (Connection connection : existingConnections) {
                    if (connection instanceof TelephonyConnection &&
                            !conferencedConnections.contains(connection)) {
                        mTelephonyConference.removeConnection(connection);
                    }
                }

                // Add any new ones
                for (Connection connection : conferencedConnections) {
                    if (!existingConnections.contains(connection)) {
                        mTelephonyConference.addConnection(connection);
                    }
                }

                // Add new conference participants
                for (Connection conferenceParticipant :
                        mConferenceParticipantConnections.values()) {

                    if (conferenceParticipant.getState() == Connection.STATE_NEW) {
                        if (!existingConnections.contains(conferenceParticipant)) {
                            wasParticipantsAdded = true;
                            mTelephonyConference.addConnection(conferenceParticipant);
                        }
                    }
                }
            } else {
                mTelephonyConference = new TelephonyConference(null);
                for (Connection connection : conferencedConnections) {
                    Log.d(this, "Adding a connection to a conference call: %s %s",
                            mTelephonyConference, connection);
                    mTelephonyConference.addConnection(connection);
                }

                // Add the conference participants
                for (Connection conferenceParticipant :
                        mConferenceParticipantConnections.values()) {
                    wasParticipantsAdded = true;
                    mTelephonyConference.addConnection(conferenceParticipant);
                }

                mConnectionService.addConference(mTelephonyConference);
            }

            // If we added conference participants (e.g. via an IMS conference event package),
            // notify the conference so that the MANAGE_CONFERENCE capability can be added.
            if (wasParticipantsAdded) {
                mTelephonyConference.setParticipantsReceived();
            }

            // Set the conference state to the same state as its child connections.
            Connection conferencedConnection = mTelephonyConference.getPrimaryConnection();
            switch (conferencedConnection.getState()) {
                case Connection.STATE_ACTIVE:
                    mTelephonyConference.setActive();
                    break;
                case Connection.STATE_HOLDING:
                    mTelephonyConference.setOnHold();
                    break;
            }
        }
    }

    /**
     * Disconnects all conference participants from the conference.
     */
    private void disconnectConferenceParticipants() {
        for (Connection connection : mConferenceParticipantConnections.values()) {
            // Disconnect listener so that the connection doesn't fire events on the conference
            // controller, causing a recursive call.
            connection.removeConnectionListener(mConnectionListener);
            mConferenceParticipantConnections.remove(connection);

            // Mark disconnect cause as cancelled to ensure that the call is not logged in the
            // call log.
            connection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
            connection.destroy();
        }
    }

    /**
     * Handles state changes for conference participant(s).
     *
     * @param parent The connection which was notified of the conference participant.
     * @param participants The conference participant information.
     */
    private void handleConferenceParticipantsUpdate(
            TelephonyConnection parent, List<ConferenceParticipant> participants) {

        boolean recalculateConference = false;
        ArrayList<ConferenceParticipant> newParticipants = new ArrayList<>(participants.size());

        for (ConferenceParticipant participant : participants) {
            Uri endpoint = participant.getEndpoint();
            if (!mConferenceParticipantConnections.containsKey(endpoint)) {
                createConferenceParticipantConnection(parent, participant);
                newParticipants.add(participant);
                recalculateConference = true;
            } else {
                ConferenceParticipantConnection connection =
                        mConferenceParticipantConnections.get(endpoint);
                connection.updateState(participant.getState());
            }
        }

        if (recalculateConference) {
            // Recalculate to add new connections to the conference.
            recalculateConference();

            // Now that conference is established, set the state for all participants.
            for (ConferenceParticipant newParticipant : newParticipants) {
                ConferenceParticipantConnection connection =
                        mConferenceParticipantConnections.get(newParticipant.getEndpoint());
                connection.updateState(newParticipant.getState());
            }
        }
    }

    /**
     * Creates a new {@link ConferenceParticipantConnection} to represent a
     * {@link ConferenceParticipant}.
     * <p>
     * The new connection is added to the conference controller and connection service.
     *
     * @param parent The connection which was notified of the participant change (e.g. the
     *                         parent connection).
     * @param participant The conference participant information.
     */
    private void createConferenceParticipantConnection(
            TelephonyConnection parent, ConferenceParticipant participant) {

        // Create and add the new connection in holding state so that it does not become the
        // active call.
        ConferenceParticipantConnection connection = new ConferenceParticipantConnection(
                parent, participant);
        connection.addConnectionListener(mConnectionListener);
        mConferenceParticipantConnections.put(participant.getEndpoint(), connection);
        PhoneAccountHandle phoneAccountHandle =
                 TelecomAccountRegistry.makePstnPhoneAccountHandle(parent.getPhone());
        mConnectionService.addExistingConnection(phoneAccountHandle, connection);
    }
}
