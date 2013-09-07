/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.phone;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.phone.CallGatewayManager.RawGatewayInfo;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;
import com.android.services.telephony.common.Call.State;

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates a Call model from Call state and data received from the telephony
 * layer. The telephony layer maintains 3 conceptual objects: Phone, Call,
 * Connection.
 *
 * Phone represents the radio and there is an implementation per technology
 * type such as GSMPhone, SipPhone, CDMAPhone, etc. Generally, we will only ever
 * deal with one instance of this object for the lifetime of this class.
 *
 * There are 3 Call instances that exist for the lifetime of this class which
 * are created by CallTracker. The three are RingingCall, ForegroundCall, and
 * BackgroundCall.
 *
 * A Connection most closely resembles what the layperson would consider a call.
 * A Connection is created when a user dials and it is "owned" by one of the
 * three Call instances.  Which of the three Calls owns the Connection changes
 * as the Connection goes between ACTIVE, HOLD, RINGING, and other states.
 *
 * This class models a new Call class from Connection objects received from
 * the telephony layer. We use Connection references as identifiers for a call;
 * new reference = new call.
 *
 * TODO(klp): Create a new Call class to replace the simple call Id ints
 * being used currently.
 *
 * The new Call models are parcellable for transfer via the CallHandlerService
 * API.
 */
public class CallModeler extends Handler {

    private static final String TAG = CallModeler.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);

    private static final int CALL_ID_START_VALUE = 1;

    private final CallStateMonitor mCallStateMonitor;
    private final CallManager mCallManager;
    private final CallGatewayManager mCallGatewayManager;
    private final HashMap<Connection, Call> mCallMap = Maps.newHashMap();
    private final HashMap<Connection, Call> mConfCallMap = Maps.newHashMap();
    private final AtomicInteger mNextCallId = new AtomicInteger(CALL_ID_START_VALUE);
    private final ArrayList<Listener> mListeners = new ArrayList<Listener>();
    private RejectWithTextMessageManager mRejectWithTextMessageManager;

    public CallModeler(CallStateMonitor callStateMonitor, CallManager callManager,
            RejectWithTextMessageManager rejectWithTextMessageManager,
            CallGatewayManager callGatewayManager) {
        mCallStateMonitor = callStateMonitor;
        mCallManager = callManager;
        mRejectWithTextMessageManager = rejectWithTextMessageManager;
        mCallGatewayManager = callGatewayManager;

        mCallStateMonitor.addListener(this);
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case CallStateMonitor.PHONE_NEW_RINGING_CONNECTION:
                onNewRingingConnection((AsyncResult) msg.obj);
                break;
            case CallStateMonitor.PHONE_DISCONNECT:
                onDisconnect((AsyncResult) msg.obj);
                break;
            case CallStateMonitor.PHONE_STATE_CHANGED:
                onPhoneStateChanged((AsyncResult) msg.obj);
                break;
            case CallStateMonitor.PHONE_ON_DIAL_CHARS:
                onPostDialChars((AsyncResult) msg.obj, (char) msg.arg1);
                break;
            default:
                break;
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(mListeners);
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public List<Call> getFullList() {
        final List<Call> retval = Lists.newArrayList();
        doUpdate(true, retval);
        return retval;
    }

    public CallResult getCallWithId(int callId) {
        // max 8 connections, so this should be fast even through we are traversing the entire map.
        for (Entry<Connection, Call> entry : mCallMap.entrySet()) {
            if (entry.getValue().getCallId() == callId) {
                return new CallResult(entry.getValue(), entry.getKey());
            }
        }

        for (Entry<Connection, Call> entry : mConfCallMap.entrySet()) {
            if (entry.getValue().getCallId() == callId) {
                if (entry.getValue().getChildCallIds().size() == 0) {
                    return null;
                }
                final CallResult child = getCallWithId(entry.getValue().getChildCallIds().first());
                return new CallResult(entry.getValue(), child.getActionableCall(),
                        child.getConnection());
            }
        }
        return null;
    }

    public boolean hasLiveCall() {
        return hasLiveCallInternal(mCallMap) ||
            hasLiveCallInternal(mConfCallMap);
    }

    private boolean hasLiveCallInternal(HashMap<Connection, Call> map) {
        for (Call call : map.values()) {
            final int state = call.getState();
            if (state == Call.State.ACTIVE ||
                    state == Call.State.CALL_WAITING ||
                    state == Call.State.CONFERENCED ||
                    state == Call.State.DIALING ||
                    state == Call.State.INCOMING ||
                    state == Call.State.ONHOLD) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOutstandingActiveOrDialingCall() {
        return hasOutstandingActiveOrDialingCallInternal(mCallMap) ||
                hasOutstandingActiveOrDialingCallInternal(mConfCallMap);
    }

    private static boolean hasOutstandingActiveOrDialingCallInternal(
            HashMap<Connection, Call> map) {
        for (Call call : map.values()) {
            final int state = call.getState();
            if (state == Call.State.ACTIVE ||
                    state == Call.State.DIALING) {
                return true;
            }
        }

        return false;
    }


    /**
     * Handles the POST_ON_DIAL_CHARS message from the Phone (see our call to
     * mPhone.setOnPostDialCharacter() above.)
     *
     * TODO: NEED TO TEST THIS SEQUENCE now that we no longer handle "dialable" key events here in
     * the InCallScreen: we do directly to the Dialer UI instead.  Similarly, we may now need to go
     * directly to the Dialer to handle POST_ON_DIAL_CHARS too.
     */
    private void onPostDialChars(AsyncResult r, char ch) {
        final Connection c = (Connection) r.result;

        if (c != null) {
            final Connection.PostDialState state = (Connection.PostDialState) r.userObj;

            switch (state) {
                // TODO(klp): add other post dial related functions
                case WAIT:
                    final Call call = getCallFromMap(mCallMap, c, false);
                    if (call == null) {
                        Log.i(TAG, "Call no longer exists. Skipping onPostDialWait().");
                    } else {
                        for (Listener mListener : mListeners) {
                            mListener.onPostDialWait(call.getCallId(),
                                    c.getRemainingPostDialString());
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }

    private void onNewRingingConnection(AsyncResult r) {
        Log.i(TAG, "onNewRingingConnection");
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromMap(mCallMap, conn, true);

        updateCallFromConnection(call, conn, false);

        for (int i = 0; i < mListeners.size(); ++i) {
            if (call != null) {
              mListeners.get(i).onIncoming(call);
            }
        }
    }

    private void onDisconnect(AsyncResult r) {
        Log.i(TAG, "onDisconnect");
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromMap(mCallMap, conn, false);

        if (call != null) {
            final boolean wasConferenced = call.getState() == State.CONFERENCED;

            updateCallFromConnection(call, conn, false);

            for (int i = 0; i < mListeners.size(); ++i) {
                mListeners.get(i).onDisconnect(call);
            }

            // If it was a conferenced call, we need to run the entire update
            // to make the proper changes to parent conference calls.
            if (wasConferenced) {
                onPhoneStateChanged(null);
            }

            mCallMap.remove(conn);
        }

        // TODO(klp): Do a final check to see if there are any active calls.
        // If there are not, totally cancel all calls
    }

    /**
     * Called when the phone state changes.
     */
    private void onPhoneStateChanged(AsyncResult r) {
        Log.i(TAG, "onPhoneStateChanged: ");
        final List<Call> updatedCalls = Lists.newArrayList();
        doUpdate(false, updatedCalls);

        for (int i = 0; i < mListeners.size(); ++i) {
            mListeners.get(i).onUpdate(updatedCalls);
        }
    }


    /**
     * Go through the Calls from CallManager and return the list of calls that were updated.
     * Or, the full list if requested.
     */
    private void doUpdate(boolean fullUpdate, List<Call> out) {
        final List<com.android.internal.telephony.Call> telephonyCalls = Lists.newArrayList();
        telephonyCalls.addAll(mCallManager.getRingingCalls());
        telephonyCalls.addAll(mCallManager.getForegroundCalls());
        telephonyCalls.addAll(mCallManager.getBackgroundCalls());

        // Cycle through all the Connections on all the Calls. Update our Call objects
        // to reflect any new state and send the updated Call objects to the handler service.
        for (com.android.internal.telephony.Call telephonyCall : telephonyCalls) {

            for (Connection connection : telephonyCall.getConnections()) {
                // new connections return a Call with INVALID state, which does not translate to
                // a state in the internal.telephony.Call object.  This ensures that staleness
                // check below fails and we always add the item to the update list if it is new.
                final Call call = getCallFromMap(mCallMap, connection, true);

                boolean changed = updateCallFromConnection(call, connection, false);

                Log.i(TAG, "doUpdate: " + call);
                if (fullUpdate || changed) {
                    out.add(call);
                }
            }

            // We do a second loop to address conference call scenarios.  We do this as a separate
            // loop to ensure all child calls are up to date before we start updating the parent
            // conference calls.
            for (Connection connection : telephonyCall.getConnections()) {
                updateForConferenceCalls(connection, out);
            }

        }
    }

    /**
     * Checks to see if the connection is the first connection in a conference call.
     * If it is a conference call, we will create a new Conference Call object or
     * update the existing conference call object for that connection.
     * If it is not a conference call but a previous associated conference call still exists,
     * we mark it as idle and remove it from the map.
     * In both cases above, we add the Calls to be updated to the UI.
     * @param connection The connection object to check.
     * @param updatedCalls List of 'updated' calls that will be sent to the UI.
     */
    private boolean updateForConferenceCalls(Connection connection, List<Call> updatedCalls) {
        // We consider this connection a conference connection if the call it
        // belongs to is a multiparty call AND it is the first connection.
        final boolean isConferenceCallConnection = isPartOfLiveConferenceCall(connection) &&
                connection.getCall().getEarliestConnection() == connection;

        boolean changed = false;

        // If this connection is the main connection for the conference call, then create or update
        // a Call object for that conference call.
        if (isConferenceCallConnection) {
            final Call confCall = getCallFromMap(mConfCallMap, connection, true);
            changed = updateCallFromConnection(confCall, connection, true);

            if (changed) {
                updatedCalls.add(confCall);
            }

            if (DBG) Log.d(TAG, "Updating a conference call: " + confCall);

        // It is possible that through a conference call split, there may be lingering conference
        // calls where this connection was the main connection.  We clean those up here.
        } else {
            final Call oldConfCall = getCallFromMap(mConfCallMap, connection, false);

            // We found a conference call for this connection, which is no longer a conference call.
            // Kill it!
            if (oldConfCall != null) {
                if (DBG) Log.d(TAG, "Cleaning up an old conference call: " + oldConfCall);
                mConfCallMap.remove(connection);
                oldConfCall.setState(State.IDLE);
                changed = true;

                // add to the list of calls to update
                updatedCalls.add(oldConfCall);
            }
        }

        return changed;
    }

    /**
     * Sets the new call state onto the call and performs some additional logic
     * associated with setting the state.
     */
    private void setNewState(Call call, int newState, Connection connection) {
        Preconditions.checkState(call.getState() != newState);

        // When starting an outgoing call, we need to grab gateway information
        // for the call, if available, and set it.
        final RawGatewayInfo info = mCallGatewayManager.getGatewayInfo(connection);

        if (newState == Call.State.DIALING) {
            if (!info.isEmpty()) {
                call.setGatewayNumber(info.getFormattedGatewayNumber());
                call.setGatewayPackage(info.packageName);
            }
        } else if (!Call.State.isConnected(newState)) {
            mCallGatewayManager.clearGatewayData(connection);
        }

        call.setState(newState);
    }

    /**
     * Updates the Call properties to match the state of the connection object
     * that it represents.
     * @param call The call object to update.
     * @param connection The connection object from which to update call.
     * @param isForConference There are slight differences in how we populate data for conference
     *     calls. This boolean tells us which method to use.
     */
    private boolean updateCallFromConnection(Call call, Connection connection,
            boolean isForConference) {
        boolean changed = false;

        final int newState = translateStateFromTelephony(connection, isForConference);

        if (call.getState() != newState) {
            setNewState(call, newState, connection);
            changed = true;
        }

        final Call.DisconnectCause newDisconnectCause =
                translateDisconnectCauseFromTelephony(connection.getDisconnectCause());
        if (call.getDisconnectCause() != newDisconnectCause) {
            call.setDisconnectCause(newDisconnectCause);
            changed = true;
        }

        final long oldConnectTime = call.getConnectTime();
        if (oldConnectTime != connection.getConnectTime()) {
            call.setConnectTime(connection.getConnectTime());
            changed = true;
        }

        if (!isForConference) {
            // Number
            final String oldNumber = call.getNumber();
            String newNumber = connection.getAddress();
            RawGatewayInfo info = mCallGatewayManager.getGatewayInfo(connection);
            if (!info.isEmpty()) {
                newNumber = info.trueNumber;
            }
            if (TextUtils.isEmpty(oldNumber) || !oldNumber.equals(newNumber)) {
                call.setNumber(newNumber);
                changed = true;
            }

            // Number presentation
            final int newNumberPresentation = connection.getNumberPresentation();
            if (call.getNumberPresentation() != newNumberPresentation) {
                call.setNumberPresentation(newNumberPresentation);
                changed = true;
            }

            // Name
            final String oldCnapName = call.getCnapName();
            if (TextUtils.isEmpty(oldCnapName) || !oldCnapName.equals(connection.getCnapName())) {
                call.setCnapName(connection.getCnapName());
                changed = true;
            }

            // Name Presentation
            final int newCnapNamePresentation = connection.getCnapNamePresentation();
            if (call.getCnapNamePresentation() != newCnapNamePresentation) {
                call.setCnapNamePresentation(newCnapNamePresentation);
                changed = true;
            }
        } else {

            // update the list of children by:
            // 1) Saving the old set
            // 2) Removing all children
            // 3) Adding the correct children into the Call
            // 4) Comparing the new children set with the old children set
            ImmutableSortedSet<Integer> oldSet = call.getChildCallIds();
            call.removeAllChildren();

            if (connection.getCall() != null) {
                for (Connection childConn : connection.getCall().getConnections()) {
                    final Call childCall = getCallFromMap(mCallMap, childConn, false);
                    if (childCall != null && childConn.isAlive()) {
                        call.addChildId(childCall.getCallId());
                    }
                }
            }
            changed |= !oldSet.equals(call.getChildCallIds());
        }

        /**
         * !!! Uses values from connection and call collected above so this part must be last !!!
         */
        final int newCapabilities = getCapabilitiesFor(connection, call);
        if (call.getCapabilities() != newCapabilities) {
            call.setCapabilities(newCapabilities);
            changed = true;
        }

        return changed;
    }

    /**
     * Returns a mask of capabilities for the connection such as merge, hold, etc.
     */
    private int getCapabilitiesFor(Connection connection, Call call) {
        final boolean callIsActive = (call.getState() == Call.State.ACTIVE);
        final Phone phone = connection.getCall().getPhone();

        final boolean canHold = TelephonyCapabilities.supportsAnswerAndHold(phone);
        boolean canAddCall = false;
        boolean canMergeCall = false;
        boolean canSwapCall = false;
        boolean canRespondViaText = false;

        // only applies to active calls
        if (callIsActive) {
            canAddCall = PhoneUtils.okToAddCall(mCallManager);
            canMergeCall = PhoneUtils.okToMergeCalls(mCallManager);
            canSwapCall = PhoneUtils.okToSwapCalls(mCallManager);
        }

        canRespondViaText = RejectWithTextMessageManager.allowRespondViaSmsForCall(call,
                connection);

        // special rules section!
        // CDMA always has Add
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            canAddCall = true;
        } else {
            // if neither merge nor add is on...then allow add
            canAddCall |= !(canAddCall || canMergeCall);
        }

        int retval = 0x0;
        if (canHold) {
            retval |= Capabilities.HOLD;
        }
        if (canAddCall) {
            retval |= Capabilities.ADD_CALL;
        }
        if (canMergeCall) {
            retval |= Capabilities.MERGE_CALLS;
        }
        if (canSwapCall) {
            retval |= Capabilities.SWAP_CALLS;
        }

        if (canRespondViaText) {
            retval |= Capabilities.RESPOND_VIA_TEXT;
        }

        return retval;
    }

    /**
     * Returns true if the Connection is part of a multiparty call.
     * We do this by checking the isMultiparty() method of the telephony.Call object and also
     * checking to see if more than one of it's children is alive.
     */
    private boolean isPartOfLiveConferenceCall(Connection connection) {
        if (connection.getCall() != null && connection.getCall().isMultiparty()) {
            int count = 0;
            for (Connection currConn : connection.getCall().getConnections()) {
                if (currConn.isAlive()) {
                    count++;
                    if (count >= 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int translateStateFromTelephony(Connection connection, boolean isForConference) {

        int retval = State.IDLE;
        switch (connection.getState()) {
            case ACTIVE:
                retval = State.ACTIVE;
                break;
            case INCOMING:
                retval = State.INCOMING;
                break;
            case DIALING:
            case ALERTING:
                retval = State.DIALING;
                break;
            case WAITING:
                retval = State.CALL_WAITING;
                break;
            case HOLDING:
                retval = State.ONHOLD;
                break;
            case DISCONNECTED:
            case DISCONNECTING:
                retval = State.DISCONNECTED;
            default:
        }

        // If we are dealing with a potential child call (not the parent conference call),
        // the check to see if we have to set the state to CONFERENCED.
        if (!isForConference) {

            // if the connection is part of a multiparty call, and it is live,
            // annotate it with CONFERENCED state instead.
            if (isPartOfLiveConferenceCall(connection) && connection.isAlive()) {
                return State.CONFERENCED;
            }
        }

        return retval;
    }

    private final ImmutableMap<Connection.DisconnectCause, Call.DisconnectCause> CAUSE_MAP =
            ImmutableMap.<Connection.DisconnectCause, Call.DisconnectCause>builder()
                .put(Connection.DisconnectCause.BUSY, Call.DisconnectCause.BUSY)
                .put(Connection.DisconnectCause.CALL_BARRED, Call.DisconnectCause.CALL_BARRED)
                .put(Connection.DisconnectCause.CDMA_ACCESS_BLOCKED,
                        Call.DisconnectCause.CDMA_ACCESS_BLOCKED)
                .put(Connection.DisconnectCause.CDMA_ACCESS_FAILURE,
                        Call.DisconnectCause.CDMA_ACCESS_FAILURE)
                .put(Connection.DisconnectCause.CDMA_DROP, Call.DisconnectCause.CDMA_DROP)
                .put(Connection.DisconnectCause.CDMA_INTERCEPT, Call.DisconnectCause.CDMA_INTERCEPT)
                .put(Connection.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE,
                        Call.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE)
                .put(Connection.DisconnectCause.CDMA_NOT_EMERGENCY,
                        Call.DisconnectCause.CDMA_NOT_EMERGENCY)
                .put(Connection.DisconnectCause.CDMA_PREEMPTED, Call.DisconnectCause.CDMA_PREEMPTED)
                .put(Connection.DisconnectCause.CDMA_REORDER, Call.DisconnectCause.CDMA_REORDER)
                .put(Connection.DisconnectCause.CDMA_RETRY_ORDER,
                        Call.DisconnectCause.CDMA_RETRY_ORDER)
                .put(Connection.DisconnectCause.CDMA_SO_REJECT, Call.DisconnectCause.CDMA_SO_REJECT)
                .put(Connection.DisconnectCause.CONGESTION, Call.DisconnectCause.CONGESTION)
                .put(Connection.DisconnectCause.CS_RESTRICTED, Call.DisconnectCause.CS_RESTRICTED)
                .put(Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY,
                        Call.DisconnectCause.CS_RESTRICTED_EMERGENCY)
                .put(Connection.DisconnectCause.CS_RESTRICTED_NORMAL,
                        Call.DisconnectCause.CS_RESTRICTED_NORMAL)
                .put(Connection.DisconnectCause.ERROR_UNSPECIFIED,
                        Call.DisconnectCause.ERROR_UNSPECIFIED)
                .put(Connection.DisconnectCause.FDN_BLOCKED, Call.DisconnectCause.FDN_BLOCKED)
                .put(Connection.DisconnectCause.ICC_ERROR, Call.DisconnectCause.ICC_ERROR)
                .put(Connection.DisconnectCause.INCOMING_MISSED,
                        Call.DisconnectCause.INCOMING_MISSED)
                .put(Connection.DisconnectCause.INCOMING_REJECTED,
                        Call.DisconnectCause.INCOMING_REJECTED)
                .put(Connection.DisconnectCause.INVALID_CREDENTIALS,
                        Call.DisconnectCause.INVALID_CREDENTIALS)
                .put(Connection.DisconnectCause.INVALID_NUMBER,
                        Call.DisconnectCause.INVALID_NUMBER)
                .put(Connection.DisconnectCause.LIMIT_EXCEEDED, Call.DisconnectCause.LIMIT_EXCEEDED)
                .put(Connection.DisconnectCause.LOCAL, Call.DisconnectCause.LOCAL)
                .put(Connection.DisconnectCause.LOST_SIGNAL, Call.DisconnectCause.LOST_SIGNAL)
                .put(Connection.DisconnectCause.MMI, Call.DisconnectCause.MMI)
                .put(Connection.DisconnectCause.NORMAL, Call.DisconnectCause.NORMAL)
                .put(Connection.DisconnectCause.NOT_DISCONNECTED,
                        Call.DisconnectCause.NOT_DISCONNECTED)
                .put(Connection.DisconnectCause.NUMBER_UNREACHABLE,
                        Call.DisconnectCause.NUMBER_UNREACHABLE)
                .put(Connection.DisconnectCause.OUT_OF_NETWORK, Call.DisconnectCause.OUT_OF_NETWORK)
                .put(Connection.DisconnectCause.OUT_OF_SERVICE, Call.DisconnectCause.OUT_OF_SERVICE)
                .put(Connection.DisconnectCause.POWER_OFF, Call.DisconnectCause.POWER_OFF)
                .put(Connection.DisconnectCause.SERVER_ERROR, Call.DisconnectCause.SERVER_ERROR)
                .put(Connection.DisconnectCause.SERVER_UNREACHABLE,
                        Call.DisconnectCause.SERVER_UNREACHABLE)
                .put(Connection.DisconnectCause.TIMED_OUT, Call.DisconnectCause.TIMED_OUT)
                .put(Connection.DisconnectCause.UNOBTAINABLE_NUMBER,
                        Call.DisconnectCause.UNOBTAINABLE_NUMBER)
                .build();

    private Call.DisconnectCause translateDisconnectCauseFromTelephony(
            Connection.DisconnectCause causeSource) {

        if (CAUSE_MAP.containsKey(causeSource)) {
            return CAUSE_MAP.get(causeSource);
        }

        return Call.DisconnectCause.UNKNOWN;
    }

    /**
     * Gets an existing callId for a connection, or creates one if none exists.
     * This function does NOT set any of the Connection data onto the Call class.
     * A separate call to updateCallFromConnection must be made for that purpose.
     */
    private Call getCallFromMap(HashMap<Connection, Call> map, Connection conn,
            boolean createIfMissing) {
        Call call = null;

        // Find the call id or create if missing and requested.
        if (conn != null) {
            if (map.containsKey(conn)) {
                call = map.get(conn);
            } else if (createIfMissing) {
                call = createNewCall();
                map.put(conn, call);
            }
        }
        return call;
    }

    /**
     * Creates a brand new connection for the call.
     */
    private Call createNewCall() {
        int callId;
        int newNextCallId;
        do {
            callId = mNextCallId.get();

            // protect against overflow
            newNextCallId = (callId == Integer.MAX_VALUE ?
                    CALL_ID_START_VALUE : callId + 1);

            // Keep looping if the change was not atomic OR the value is already taken.
            // The call to containsValue() is linear, however, most devices support a
            // maximum of 7 connections so it's not expensive.
        } while (!mNextCallId.compareAndSet(callId, newNextCallId));

        return new Call(callId);
    }

    /**
     * Listener interface for changes to Calls.
     */
    public interface Listener {
        void onDisconnect(Call call);
        void onIncoming(Call call);
        void onUpdate(List<Call> calls);
        void onPostDialWait(int callId, String remainingChars);
    }

    /**
     * Result class for accessing a call by connection.
     */
    public static class CallResult {
        public Call mCall;
        public Call mActionableCall;
        public Connection mConnection;

        private CallResult(Call call, Connection connection) {
            this(call, call, connection);
        }

        private CallResult(Call call, Call actionableCall, Connection connection) {
            mCall = call;
            mActionableCall = actionableCall;
            mConnection = connection;
        }

        public Call getCall() {
            return mCall;
        }

        // The call that should be used for call actions like hanging up.
        public Call getActionableCall() {
            return mActionableCall;
        }

        public Connection getConnection() {
            return mConnection;
        }
    }
}
