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

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;
import com.android.services.telephony.common.Call.State;

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

    private static final int CALL_ID_START_VALUE = 1;

    private final CallStateMonitor mCallStateMonitor;
    private final CallManager mCallManager;
    private final HashMap<Connection, Call> mCallMap = Maps.newHashMap();
    private final AtomicInteger mNextCallId = new AtomicInteger(CALL_ID_START_VALUE);
    private final ArrayList<Listener> mListeners = new ArrayList<Listener>();
    private RejectWithTextMessageManager mRejectWithTextMessageManager;

    public CallModeler(CallStateMonitor callStateMonitor, CallManager callManager,
            RejectWithTextMessageManager rejectWithTextMessageManager) {
        mCallStateMonitor = callStateMonitor;
        mCallManager = callManager;
        mRejectWithTextMessageManager = rejectWithTextMessageManager;

        mCallStateMonitor.addListener(this);
    }

    //@Override
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
        return null;
    }

    public boolean hasOutstandingActiveCall() {
        for (Call call : mCallMap.values()) {
            int state = call.getState();
            if (Call.State.INVALID != state &&
                    Call.State.IDLE != state &&
                    Call.State.INCOMING != state) {
                return true;
            }
        }

        return false;
    }

    private void onNewRingingConnection(AsyncResult r) {
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromConnection(conn, true);

        updateCallFromConnection(call, conn);
        call.setState(Call.State.INCOMING);

        for (int i = 0; i < mListeners.size(); ++i) {
            if (call != null) {
              mListeners.get(i).onIncoming(call,
                      mRejectWithTextMessageManager.loadCannedResponses());
            }
        }
    }

    private void onDisconnect(AsyncResult r) {
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromConnection(conn, false);

        updateCallFromConnection(call, conn);
        call.setState(Call.State.DISCONNECTED);

        if (call != null) {
            mCallMap.remove(conn);

            for (int i = 0; i < mListeners.size(); ++i) {
                mListeners.get(i).onDisconnect(call);
            }
        }
    }

    /**
     * Called when the phone state changes.
     */
    private void onPhoneStateChanged(AsyncResult r) {
        final List<Call> updatedCalls = Lists.newArrayList();
        doUpdate(false, updatedCalls);

        for (int i = 0; i < mListeners.size(); ++i) {
            mListeners.get(i).onUpdate(updatedCalls, false);
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
                final Call call = getCallFromConnection(connection, true);

                boolean changed = updateCallFromConnection(call, connection);

                if (fullUpdate || changed) {
                    out.add(call);
                }
            }
        }
    }

    /**
     * Updates the Call properties to match the state of the connection object
     * that it represents.
     */
    private boolean updateCallFromConnection(Call call, Connection connection) {
        boolean changed = false;

        com.android.internal.telephony.Call telephonyCall = connection.getCall();
        final int newState = translateStateFromTelephony(telephonyCall.getState());

        if (call.getState() != newState) {
            call.setState(newState);
            changed = true;
        }

        final String oldNumber = call.getNumber();
        if (TextUtils.isEmpty(oldNumber) || !oldNumber.equals(connection.getAddress())) {
            call.setNumber(connection.getAddress());
            changed = true;
        }

        final Call.DisconnectCause newDisconnectCause =
                translateDisconnectCauseFromTelephony(connection.getDisconnectCause());
        if (call.getDisconnectCause() != newDisconnectCause) {
            call.setDisconnectCause(newDisconnectCause);
            changed = true;
        }

        final int newNumberPresentation = connection.getNumberPresentation();
        if (call.getNumberPresentation() != newNumberPresentation) {
            call.setNumberPresentation(newNumberPresentation);
            changed = true;
        }

        final int newCnapNamePresentation = connection.getCnapNamePresentation();
        if (call.getCnapNamePresentation() != newCnapNamePresentation) {
            call.setCnapNamePresentation(newCnapNamePresentation);
            changed = true;
        }

        final String oldCnapName = call.getCnapName();
        if (TextUtils.isEmpty(oldCnapName) || !oldCnapName.equals(connection.getCnapName())) {
            call.setCnapName(connection.getCnapName());
            changed = true;
        }

        final long oldConnectTime = call.getConnectTime();
        if (oldConnectTime != connection.getConnectTime()) {
            call.setConnectTime(connection.getConnectTime());
            changed = true;
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

        // only applies to active calls
        if (callIsActive) {
            canAddCall = PhoneUtils.okToAddCall(mCallManager);
            canMergeCall = PhoneUtils.okToMergeCalls(mCallManager);
            canSwapCall = PhoneUtils.okToSwapCalls(mCallManager);
        }

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

        return retval;
    }

    private int translateStateFromTelephony(com.android.internal.telephony.Call.State teleState) {
        int retval = State.IDLE;
        switch (teleState) {
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
    private Call getCallFromConnection(Connection conn, boolean createIfMissing) {
        Call call = null;

        // Find the call id or create if missing and requested.
        if (conn != null) {
            if (mCallMap.containsKey(conn)) {
                call = mCallMap.get(conn);
            } else if (createIfMissing) {
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
                } while (!mNextCallId.compareAndSet(callId, newNextCallId) ||
                        mCallMap.containsValue(callId));

                call = new Call(callId);

                mCallMap.put(conn, call);
            }
        }
        return call;
    }

    /**
     * Listener interface for changes to Calls.
     */
    public interface Listener {
        void onDisconnect(Call call);
        void onIncoming(Call call, ArrayList<String> textReponses);
        void onUpdate(List<Call> calls, boolean fullUpdate);
    }

    /**
     * Result class for accessing a call by connection.
     */
    public static class CallResult {
        public Call mCall;
        public Connection mConnection;

        private CallResult(Call call, Connection connection) {
            mCall = call;
            mConnection = connection;
        }

        public Call getCall() {
            return mCall;
        }

        public Connection getConnection() {
            return mConnection;
        }
    }
}
