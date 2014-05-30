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
import android.telephony.DisconnectCause;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import android.telecomm.Connection;

/**
 * Manages a single phone call in Telephony.
 */
class TelephonyConnection extends Connection {
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 1;

    private final StateHandler mHandler = new StateHandler();

    private com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mState = Call.State.IDLE;

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection) {
        mOriginalConnection = originalConnection;
        mOriginalConnection.getCall().getPhone().registerForPreciseCallStateChanged(mHandler,
                EVENT_PRECISE_CALL_STATE_CHANGED, null);
        updateState();
    }

    com.android.internal.telephony.Connection getOriginalConnection() {
        return mOriginalConnection;
    }

    @Override
    protected void onAbort() {
        hangup(DisconnectCause.LOCAL);
        super.onAbort();
    }

    @Override
    protected void onDisconnect() {
        hangup(DisconnectCause.LOCAL);
        super.onDisconnect();
    }

    @Override
    protected void onHold() {
        Log.d(this, "Attempting to put call on hold");
        // TODO(santoscordon): Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mState) {
            Log.v(this, "Holding active call");
            try {
                Phone phone = mOriginalConnection.getCall().getPhone();
                Call ringingCall = phone.getRingingCall();

                // Although the method says switchHoldingAndActive, it eventually calls a RIL method
                // called switchWaitingOrHoldingAndActive. What this means is that if we try to put
                // a call on hold while a call-waiting call exists, it'll end up accepting the
                // call-waiting call, which is bad if that was not the user's intention. We are
                // cheating here and simply skipping it because we know any attempt to hold a call
                // while a call-waiting call is happening is likely a request from Telecomm prior to
                // accepting the call-waiting call.
                // TODO(santoscordon): Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                }

                // TODO(santoscordon): Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
        super.onHold();
    }

    @Override
    protected void onUnhold() {
        Log.d(this, "Attempting to release call from hold");
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
        super.onUnhold();
    }

    @Override
    protected void onSetAudioState(CallAudioState audioState) {
        // TODO: update TTY mode.
        if (mOriginalConnection != null) {
            Call call = mOriginalConnection.getCall();
            if (call != null) {
                call.getPhone().setEchoSuppressionEnabled();
            }
        }
        super.onSetAudioState(audioState);
    }

    protected void hangup(int disconnectCause) {
        if (mOriginalConnection != null) {
            try {
                Call call = mOriginalConnection.getCall();
                if (call != null) {
                    call.hangup();
                }
                // Set state deliberately since we are going to close() and will no longer be
                // listening to state updates from mOriginalConnection
                setDisconnected(disconnectCause, null);
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
            }
        }
        close();
    }

    private void updateState() {
        if (mOriginalConnection == null) {
            return;
        }

        Call.State newState = mOriginalConnection.getState();
        if (mState == newState) {
            return;
        }

        Log.d(this, "mOriginalConnection new state = %s", newState);

        mState = newState;
        switch (newState) {
            case IDLE:
                break;
            case ACTIVE:
                setActive();
                break;
            case HOLDING:
                setOnHold();
                break;
            case DIALING:
            case ALERTING:
                setDialing();
                break;
            case INCOMING:
            case WAITING:
                setRinging();
                break;
            case DISCONNECTED:
                setDisconnected(mOriginalConnection.getDisconnectCause(), null);
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
