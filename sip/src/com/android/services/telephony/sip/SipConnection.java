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

package com.android.services.telephony.sip;

import android.telecomm.CallAudioState;
import android.telecomm.Connection;
import android.util.Log;

import java.util.List;

public class SipConnection extends Connection {
    private static final String PREFIX = "[SipConnection] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    private final com.android.internal.telephony.Connection mConnection;

    public SipConnection(com.android.internal.telephony.Connection connection) {
        if (VERBOSE) log("new SipConnection, connection: " + connection);
        mConnection = connection;
    }

    @Override
    protected void onSetAudioState(CallAudioState state) {
        if (VERBOSE) log("onSetAudioState: " + state);
    }

    @Override
    protected void onSetState(int state) {
        if (VERBOSE) log("onSetState, state: " + Connection.stateToString(state));
    }

    @Override
    protected void onPlayDtmfTone(char c) {
        if (VERBOSE) log("onPlayDtmfTone");
    }

    @Override
    protected void onStopDtmfTone() {
        if (VERBOSE) log("onStopDtmfTone");
    }

    @Override
    protected void onDisconnect() {
        if (VERBOSE) log("onDisconnect");
    }

    @Override
    protected void onSeparate() {
        if (VERBOSE) log("onSeparate");
    }

    @Override
    protected void onAbort() {
        if (VERBOSE) log("onAbort");
    }

    @Override
    protected void onHold() {
        if (VERBOSE) log("onHold");
    }

    @Override
    protected void onUnhold() {
        if (VERBOSE) log("onUnhold");
    }

    @Override
    protected void onAnswer() {
        if (VERBOSE) log("onAnswer");
    }

    @Override
    protected void onReject() {
        if (VERBOSE) log("onReject");
    }

    @Override
    protected void onPostDialContinue(boolean proceed) {
        if (VERBOSE) log("onPostDialContinue, proceed: " + proceed);
    }

    @Override
    protected void onChildrenChanged(List<Connection> children) {
        if (VERBOSE) log("onChildrenChanged, children: " + children);
    }

    @Override
    protected void onPhoneAccountClicked() {
        if (VERBOSE) log("onPhoneAccountClicked");
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
