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

import android.os.Handler;
import android.os.Message;
import android.telecomm.CallAudioState;
import android.telecomm.CallCapabilities;
import android.telecomm.Connection;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.sip.SipPhone;

import java.util.List;

final class SipConnection extends Connection {
    private static final String PREFIX = "[SipConnection] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    updateState(false);
                    break;
            }
        }
    };

    private com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mOriginalConnectionState = Call.State.IDLE;

    SipConnection(com.android.internal.telephony.Connection connection) {
        if (VERBOSE) log("new SipConnection, connection: " + connection);
        mOriginalConnection = connection;
        if (getPhone() != null) {
            getPhone().registerForPreciseCallStateChanged(mHandler, MSG_PRECISE_CALL_STATE_CHANGED,
                    null);
        }
    }

    @Override
    public void onSetAudioState(CallAudioState state) {
        if (VERBOSE) log("onSetAudioState: " + state);
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onSetState(int state) {
        if (VERBOSE) log("onSetState, state: " + Connection.stateToString(state));
    }

    @Override
    public void onPlayDtmfTone(char c) {
        if (VERBOSE) log("onPlayDtmfTone");
        if (getPhone() != null) {
            getPhone().startDtmf(c);
        }
    }

    @Override
    public void onStopDtmfTone() {
        if (VERBOSE) log("onStopDtmfTone");
        if (getPhone() != null) {
            getPhone().stopDtmf();
        }
    }

    @Override
    public void onDisconnect() {
        if (VERBOSE) log("onDisconnect");
        try {
            if (getCall() != null && !getCall().isMultiparty()) {
                getCall().hangup();
            } else if (mOriginalConnection != null) {
                mOriginalConnection.hangup();
            }
        } catch (CallStateException e) {
            log("onDisconnect, exception: " + e);
        }
    }

    @Override
    public void onSeparate() {
        if (VERBOSE) log("onSeparate");
        try {
            if (mOriginalConnection != null) {
                mOriginalConnection.separate();
            }
        } catch (CallStateException e) {
            log("onSeparate, exception: " + e);
        }
    }

    @Override
    public void onAbort() {
        if (VERBOSE) log("onAbort");
        onDisconnect();
    }

    @Override
    public void onHold() {
        if (VERBOSE) log("onHold");
        try {
            if (getPhone() != null && getState() == State.ACTIVE) {
                getPhone().switchHoldingAndActive();
            }
        } catch (CallStateException e) {
            log("onHold, exception: " + e);
        }
    }

    @Override
    public void onUnhold() {
        if (VERBOSE) log("onUnhold");
        try {
            if (getPhone() != null && getState() == State.HOLDING) {
                getPhone().switchHoldingAndActive();
            }
        } catch (CallStateException e) {
            log("onUnhold, exception: " + e);
        }
    }

    @Override
    public void onAnswer(int videoState) {
        if (VERBOSE) log("onAnswer");
        try {
            if (isValidRingingCall() && getPhone() != null) {
                getPhone().acceptCall(videoState);
            }
        } catch (CallStateException e) {
            log("onAnswer, exception: " + e);
        }
    }

    @Override
    public void onReject() {
        if (VERBOSE) log("onReject");
        try {
            if (isValidRingingCall() && getPhone() != null) {
                getPhone().rejectCall();
            }
        } catch (CallStateException e) {
            log("onReject, exception: " + e);
        }
    }

    @Override
    public void onPostDialContinue(boolean proceed) {
        if (VERBOSE) log("onPostDialContinue, proceed: " + proceed);
        // SIP doesn't have post dial support.
    }

    @Override
    public void onSwapWithBackgroundCall() {
        if (VERBOSE) log("onSwapWithBackgroundCall");
        // TODO(sail): Implement swap.
    }

    @Override
    public void onChildrenChanged(List<Connection> children) {
        if (VERBOSE) log("onChildrenChanged, children: " + children);
    }

    @Override
    public void onPhoneAccountClicked() {
        if (VERBOSE) log("onPhoneAccountClicked");
    }

    private Call getCall() {
        if (mOriginalConnection != null) {
            return mOriginalConnection.getCall();
        }
        return null;
    }

    SipPhone getPhone() {
        Call call = getCall();
        if (call != null) {
            return (SipPhone) call.getPhone();
        }
        return null;
    }

    private boolean isValidRingingCall() {
        Call call = getCall();
        return call != null && call.getState().isRinging() &&
                call.getEarliestConnection() == mOriginalConnection;
    }

    private void updateState(boolean force) {
        if (mOriginalConnection == null) {
            return;
        }

        Call.State newState = mOriginalConnection.getState();
        if (VERBOSE) log("updateState, " + mOriginalConnectionState + " -> " + newState);
        if (force || mOriginalConnectionState != newState) {
            mOriginalConnectionState = newState;
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
                    close();
                    break;
                case DISCONNECTING:
                    break;
            }
            updateCallCapabilities(force);
        }
    }

    private int buildCallCapabilities() {
        int capabilities = CallCapabilities.MUTE | CallCapabilities.SUPPORT_HOLD;
        if (getState() == State.ACTIVE || getState() == State.HOLDING) {
            capabilities |= CallCapabilities.HOLD;
        }
        return capabilities;
    }

    void updateCallCapabilities(boolean force) {
        int newCallCapabilities = buildCallCapabilities();
        if (force || getCallCapabilities() != newCallCapabilities) {
            setCallCapabilities(newCallCapabilities);
        }
    }

    void onAddedToCallService() {
        if (VERBOSE) log("onAddedToCallService");
        updateState(true);
        updateCallCapabilities(true);
        setAudioModeIsVoip(true);
        if (mOriginalConnection != null) {
            setCallerDisplayName(mOriginalConnection.getCnapName(),
                    mOriginalConnection.getCnapNamePresentation());
        }
    }

    private void close() {
        if (getPhone() != null) {
            getPhone().unregisterForPreciseCallStateChanged(mHandler);
        }
        mOriginalConnection = null;
        destroy();
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
