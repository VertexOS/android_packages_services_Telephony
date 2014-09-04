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
import android.telecomm.PhoneCapabilities;

import android.telephony.DisconnectCause;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;

/**
 * Manages a single phone call handled by CDMA.
 */
final class CdmaConnection extends TelephonyConnection {

    private static final int MSG_CALL_WAITING_MISSED = 1;
    private static final int TIMEOUT_CALL_WAITING_MILLIS = 20 * 1000;

    private final Handler mHandler = new Handler() {

        /** ${inheritDoc} */
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CALL_WAITING_MISSED:
                    hangupCallWaiting(DisconnectCause.INCOMING_MISSED);
                    break;
                default:
                    break;
            }
        }

    };

    /**
     * {@code True} if the CDMA connection should allow mute.
     */
    private final boolean mAllowMute;
    private final boolean mIsOutgoing;

    private boolean mIsCallWaiting;

    CdmaConnection(Connection connection, boolean allowMute, boolean isOutgoing) {
        super(connection);
        mAllowMute = allowMute;
        mIsOutgoing = isOutgoing;
        mIsCallWaiting = connection != null && connection.getState() == Call.State.WAITING;
        if (mIsCallWaiting) {
            startCallWaitingTimer();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onPlayDtmfTone(char digit) {
        // TODO: There are conditions where we should play dtmf tones with different
        // timeouts.
        // TODO: We get explicit response from the phone via a Message when the burst
        // tone has completed. During this time we can get subsequent requests. We need to stop
        // passing in null as the message and start handling it to implement a queue.
        if (getPhone() != null) {
            getPhone().sendBurstDtmf(Character.toString(digit), 0, 0, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onStopDtmfTone() {
        // no-op, we only play timed dtmf tones for cdma.
    }

    @Override
    public void onReject() {
        Connection connection = getOriginalConnection();
        if (connection != null) {
            switch (connection.getState()) {
                case INCOMING:
                    // Normal ringing calls are handled the generic way.
                    super.onReject();
                    break;
                case WAITING:
                    hangupCallWaiting(DisconnectCause.INCOMING_REJECTED);
                    break;
                default:
                    Log.e(this, new Exception(), "Rejecting a non-ringing call");
                    // might as well hang this up, too.
                    super.onReject();
                    break;
            }
        }
    }

    @Override
    public void onAnswer() {
        mHandler.removeMessages(MSG_CALL_WAITING_MISSED);
        super.onAnswer();
    }

    @Override
    public void onSetState(int state) {
        Connection originalConnection = getOriginalConnection();
        mIsCallWaiting = originalConnection != null &&
                originalConnection.getState() == Call.State.WAITING;
        super.onSetState(state);
    }

    @Override
    protected int buildCallCapabilities() {
        int capabilities = 0;
        if (mAllowMute) {
            capabilities = PhoneCapabilities.MUTE;
        }
        return capabilities;
    }

    void forceAsDialing(boolean isDialing) {
        if (isDialing) {
            setDialing();
        } else {
            updateState();
        }
    }

    boolean isOutgoing() {
        return mIsOutgoing;
    }

    boolean isCallWaiting() {
        return mIsCallWaiting;
    }

    /**
     * We do not get much in the way of confirmation for Cdma call waiting calls. There is no
     * indication that a rejected call succeeded, a call waiting call has stopped. Instead we
     * simulate this for the user. We allow TIMEOUT_CALL_WAITING_MILLIS milliseconds before we
     * assume that the call was missed and reject it ourselves. reject the call automatically.
     */
    private void startCallWaitingTimer() {
        mHandler.sendEmptyMessageDelayed(MSG_CALL_WAITING_MISSED, TIMEOUT_CALL_WAITING_MILLIS);
    }

    private void hangupCallWaiting(int disconnectCause) {
        Connection originalConnection = getOriginalConnection();
        if (originalConnection != null) {
            try {
                originalConnection.hangup();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to hangup call waiting call");
            }
            setDisconnected(disconnectCause, null);
        }
    }
}
