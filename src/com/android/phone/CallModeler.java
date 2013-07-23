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

import com.android.internal.telephony.Connection;

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
    private static final int INVALID_CALL_ID = -1;

    private CallStateMonitor mCallStateMonitor;
    private HashMap<Connection, Integer> mCallIdMap = Maps.newHashMap();
    private List<Listener> mListeners = Lists.newArrayList();
    private AtomicInteger mNextCallId = new AtomicInteger(CALL_ID_START_VALUE);

    public CallModeler(CallStateMonitor callStateMonitor) {
        mCallStateMonitor = callStateMonitor;

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
        final int callId = getCallId(conn, true);

        for (Listener l : mListeners) {
            l.onNewCall(callId);
        }
    }

    private void onDisconnect(AsyncResult r) {
        final Connection conn = (Connection) r.result;
        final int callId = getCallId(conn, false);

        if (INVALID_CALL_ID != callId) {
            mCallIdMap.remove(conn);

            for (Listener l : mListeners) {
                l.onDisconnect(callId);
            }
        }
    }

    /**
     * Gets an existing callId for a connection, or creates one
     * if none exists.
     */
    private int getCallId(Connection conn, boolean createIfMissing) {
        int callId = INVALID_CALL_ID;

        // Find the call id or create if missing and requested.
        if (conn != null) {
            if (mCallIdMap.containsKey(conn)) {
                callId = mCallIdMap.get(conn).intValue();
            } else if (createIfMissing) {
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
                        mCallIdMap.containsValue(callId));

                mCallIdMap.put(conn, callId);
            }
        }
        return callId;
    }

    /**
     * Listener interface for changes to Calls.
     */
    public interface Listener {
        void onNewCall(int callId);
        void onDisconnect(int callId);
    }
}
