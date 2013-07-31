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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private CallStateMonitor mCallStateMonitor;
    private CallManager mCallManager;
    private HashMap<Connection, Call> mCallMap = Maps.newHashMap();
    private List<Listener> mListeners = Lists.newArrayList();
    private AtomicInteger mNextCallId = new AtomicInteger(CALL_ID_START_VALUE);

    public CallModeler(CallStateMonitor callStateMonitor, CallManager callManager) {
        mCallStateMonitor = callStateMonitor;
        mCallManager = callManager;

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
            default:
                break;
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);

        mListeners.add(listener);
    }

    private void onNewRingingConnection(AsyncResult r) {
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromConnection(conn, true);
        call.setState(Call.State.INCOMING);

        if (call != null) {
            for (Listener l : mListeners) {
                l.onNewCall(call);
            }
        }
    }

    private void onDisconnect(AsyncResult r) {
        final Connection conn = (Connection) r.result;
        final Call call = getCallFromConnection(conn, false);

        if (call != null) {
            mCallMap.remove(conn);

            for (Listener l : mListeners) {
                l.onDisconnect(call);
            }
        }
    }

    /**
     * Called when the phone state changes.
     * The telephony layer maintains a certain amount of preallocated telephony::Call instances
     * that change throughout the lifetime of the phone.  When we get an alert that the state
     * changes, we need to go through the telephony::Calls and determine what changed.
     */
    private void onPhoneStateChanged(AsyncResult r) {
        final List<com.android.internal.telephony.Call> telephonyCalls = Lists.newArrayList();
        telephonyCalls.addAll(mCallManager.getRingingCalls());
        telephonyCalls.addAll(mCallManager.getForegroundCalls());
        telephonyCalls.addAll(mCallManager.getBackgroundCalls());

        final List<Call> updatedCalls = Lists.newArrayList();

        // Cycle through all the Connections on all the Calls. Update our Call objects
        // to reflect any new state and send the updated Call objects to the handler service.
        for (com.android.internal.telephony.Call telephonyCall : telephonyCalls) {
            final int state = translateStateFromTelephony(telephonyCall.getState());

            for (Connection connection : telephonyCall.getConnections()) {
                final Call call = getCallFromConnection(connection, true);

                if (call.getState() != state) {
                    call.setState(state);
                    updatedCalls.add(call);
                }
            }
        }

        for (Listener l : mListeners) {
            l.onUpdate(updatedCalls);
        }
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
            default:
        }

        return retval;
    }

    /**
     * Gets an existing callId for a connection, or creates one
     * if none exists.
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
                call.setNumber(conn.getAddress());
                mCallMap.put(conn, call);
            }
        }
        return call;
    }

    /**
     * Listener interface for changes to Calls.
     */
    public interface Listener {
        void onNewCall(Call call);
        void onDisconnect(Call call);
        void onUpdate(List<Call> calls);
    }
}
