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
import android.telecomm.CallAudioState;
import android.telecomm.CallServiceAdapter;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;

/**
 * Manages a single phone call. Listens to the call's state changes and updates the
 * CallServiceAdapter.
 */
class TelephonyCallConnection {
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 1;

    private final String mCallId;
    private final StateHandler mHandler = new StateHandler();

    private CallServiceAdapter mCallServiceAdapter;

    private Connection mOriginalConnection;
    private Call.State mState = Call.State.IDLE;

    TelephonyCallConnection(CallServiceAdapter callServiceAdapter, String callId,
            Connection connection) {
        mCallServiceAdapter = callServiceAdapter;
        mCallId = callId;
        mOriginalConnection = connection;
        mOriginalConnection.getCall().getPhone().registerForPreciseCallStateChanged(mHandler,
                EVENT_PRECISE_CALL_STATE_CHANGED, null);
        updateState();
    }

    String getCallId() {
        return mCallId;
    }

    Connection getOriginalConnection() {
        return mOriginalConnection;
    }

    void disconnect(boolean shouldAbort) {
        if (shouldAbort) {
            mCallServiceAdapter = null;
            close();
        }
        if (mOriginalConnection != null) {
            try {
                mOriginalConnection.hangup();
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
            }
        }
    }

    void hold() {
        if (Call.State.ACTIVE == mState) {
            try {
                // TODO: This doesn't handle multiple calls across call services yet
                mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }

    void unhold() {
        if (Call.State.HOLDING == mState) {
            try {
                // TODO: This doesn't handle multiple calls across call services yet
                mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    void onAudioStateChanged(CallAudioState audioState) {
        // TODO: update TTY mode.
        if (mOriginalConnection != null) {
            Call call = mOriginalConnection.getCall();
            if (call != null) {
                call.getPhone().setEchoSuppressionEnabled(
                        audioState.route == CallAudioState.ROUTE_SPEAKER);
            }
        }
    }

    private void updateState() {
        if (mOriginalConnection == null || mCallServiceAdapter == null) {
            return;
        }

        Call.State newState = mOriginalConnection.getState();
        if (mState == newState) {
            return;
        }

        mState = newState;
        switch (newState) {
            case IDLE:
                break;
            case ACTIVE:
                mCallServiceAdapter.setActive(mCallId);
                break;
            case HOLDING:
                mCallServiceAdapter.setOnHold(mCallId);
                break;
            case DIALING:
            case ALERTING:
                mCallServiceAdapter.setDialing(mCallId);
                break;
            case INCOMING:
            case WAITING:
                mCallServiceAdapter.setRinging(mCallId);
                break;
            case DISCONNECTED:
                mCallServiceAdapter.setDisconnected(
                        mCallId, mOriginalConnection.getDisconnectCause(), null);
                close();
                break;
            case DISCONNECTING:
                break;
        }
    }

    private void close() {
        if (mOriginalConnection != null) {
            Call call = mOriginalConnection.getCall();
            if (call != null) {
                call.getPhone().unregisterForPreciseCallStateChanged(mHandler);
            }
            mOriginalConnection = null;
        }
        CallRegistrar.unregister(mCallId);
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
