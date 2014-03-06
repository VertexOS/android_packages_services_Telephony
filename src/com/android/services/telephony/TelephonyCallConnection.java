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

import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telecomm.ICallServiceAdapter;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;

/**
 * Manages a single phone call. Listens to the call's state changes and updates the
 * ICallServiceAdapter.
 */
class TelephonyCallConnection {
    private static final String TAG = TelephonyCallConnection.class.getSimpleName();
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 1;

    private final String mCallId;
    private final StateHandler mHandler = new StateHandler();

    private ICallServiceAdapter mCallServiceAdapter;

    private Connection mConnection;
    private Call.State mOldState = Call.State.IDLE;

    TelephonyCallConnection(ICallServiceAdapter callServiceAdapter, String callId,
            Connection connection) {
        mCallServiceAdapter = callServiceAdapter;
        mCallId = callId;
        mConnection = connection;
        mConnection.getCall().getPhone().registerForPreciseCallStateChanged(mHandler,
                EVENT_PRECISE_CALL_STATE_CHANGED, null);
        updateState();
    }

    String getCallId() {
        return mCallId;
    }

    void disconnect(boolean shouldAbort) {
        if (shouldAbort) {
            mCallServiceAdapter = null;
        }
        if (mConnection != null) {
            try {
                mConnection.hangup();
            } catch (CallStateException e) {
                Log.e(TAG, "Call to Connection.hangup failed with exception", e);
            }
        }
    }

    private void updateState() {
        if (mConnection == null || mCallServiceAdapter == null) {
            return;
        }

        Call.State newState = mConnection.getState();
        if (mOldState == newState) {
            return;
        }

        mOldState = newState;
        try {
            switch (newState) {
                case IDLE:
                    break;
                case ACTIVE:
                    mCallServiceAdapter.setActive(mCallId);
                    break;
                case HOLDING:
                    break;
                case DIALING:
                    mCallServiceAdapter.setDialing(mCallId);
                    break;
                case ALERTING:
                    mCallServiceAdapter.setDialing(mCallId);
                    break;
                case INCOMING:
                    // Incoming calls not implemented.
                    break;
                case WAITING:
                    break;
                case DISCONNECTED:
                    mCallServiceAdapter.setDisconnected(mCallId);
                    close();
                    break;
                case DISCONNECTING:
                    break;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception", e);
        }
    }

    private void close() {
        if (mConnection != null) {
            Call call = mConnection.getCall();
            if (call != null) {
                call.getPhone().unregisterForPreciseCallStateChanged(mHandler);
            }
            mConnection = null;
        }
        BaseTelephonyCallService.onCallConnectionClosing(this);
    }

    private class StateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_PRECISE_CALL_STATE_CHANGED:
                    updateState();
                    break;
            }
        }
    }
}
