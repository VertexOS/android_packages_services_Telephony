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

import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telecomm.CallAudioState;
import android.telecomm.CallCapabilities;
import android.telephony.DisconnectCause;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection.PostDialListener;
import com.android.internal.telephony.Phone;
import com.android.phone.R;

import android.telecomm.Connection;
import android.telecomm.ConnectionService;

import java.lang.Override;
import java.util.List;
import java.util.Objects;

/**
 * Base class for CDMA and GSM connections.
 */
abstract class TelephonyConnection extends Connection {
    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;
    private static final int MSG_RINGBACK_TONE = 2;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_PRECISE_CALL_STATE_CHANGED");
                    updateState();
                    break;
                case MSG_RINGBACK_TONE:
                    Log.v(TelephonyConnection.this, "MSG_RINGBACK_TONE");
                    // TODO: This code assumes that there is only one connection in the foreground
                    // call, in other words, it punts on network-mediated conference calling.
                    if (getOriginalConnection() != getForegroundConnection()) {
                        Log.v(TelephonyConnection.this, "handleMessage, original connection is " +
                                "not foreground connection, skipping");
                        return;
                    }
                    setRequestingRingback((Boolean) ((AsyncResult) msg.obj).result);
                    break;
            }
        }
    };

    private final PostDialListener mPostDialListener = new PostDialListener() {
        @Override
        public void onPostDialWait() {
            Log.v(TelephonyConnection.this, "onPostDialWait");
            if (mOriginalConnection != null) {
                setPostDialWait(mOriginalConnection.getRemainingPostDialString());
            }
        }
    };

    /**
     * Listener for listening to events in the {@link com.android.internal.telephony.Connection}.
     */
    private final com.android.internal.telephony.Connection.Listener mOriginalConnectionListener =
            new com.android.internal.telephony.Connection.ListenerBase() {
        @Override
        public void onVideoStateChanged(int videoState) {
            setVideoState(videoState);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in local
         * video capability.
         *
         * @param capable True if capable.
         */
        @Override
        public void onLocalVideoCapabilityChanged(boolean capable) {
            setLocalVideoCapable(capable);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in remote
         * video capability.
         *
         * @param capable True if capable.
         */
        @Override
        public void onRemoteVideoCapabilityChanged(boolean capable) {
            setRemoteVideoCapable(capable);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in the
         * video call provider.
         *
         * @param videoCallProvider The video call provider.
         */
        @Override
        public void onVideoCallProviderChanged(
                ConnectionService.VideoCallProvider videoCallProvider) {
            setVideoCallProvider(videoCallProvider);
        }

        /**
         * Used by the {@link com.android.internal.telephony.Connection} to report a change in the
         * audio quality for the current call.
         *
         * @param audioQuality The audio quality.
         */
        @Override
        public void onAudioQualityChanged(int audioQuality) {
            setAudioQuality(audioQuality);
        }
    };

    private com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mOriginalConnectionState = Call.State.IDLE;

    /**
     * Determines if the {@link TelephonyConnection} has local video capabilities.
     * This is used when {@link TelephonyConnection#updateCallCapabilities}} is called,
     * ensuring the appropriate {@link CallCapabilities} are set.  Since {@link CallCapabilities}
     * can be rebuilt at any time it is necessary to track the video capabilities between rebuild.
     * The {@link CallCapabilities} (including video capabilities) are communicated to the telecomm
     * layer.
     */
    private boolean mLocalVideoCapable;

    /**
     * Determines if the {@link TelephonyConnection} has remote video capabilities.
     * This is used when {@link TelephonyConnection#updateCallCapabilities}} is called,
     * ensuring the appropriate {@link CallCapabilities} are set.  Since {@link CallCapabilities}
     * can be rebuilt at any time it is necessary to track the video capabilities between rebuild.
     * The {@link CallCapabilities} (including video capabilities) are communicated to the telecomm
     * layer.
     */
    private boolean mRemoteVideoCapable;

    /**
     * Determines the current audio quality for the {@link TelephonyConnection}.
     * This is used when {@link TelephonyConnection#updateCallCapabilities}} is called to indicate
     * whether a call has the {@link android.telecomm.CallCapabilities#VoLTE} capability.
     */
    private int mAudioQuality;

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection) {
        Log.v(this, "new TelephonyConnection, originalConnection: " + originalConnection);
        mOriginalConnection = originalConnection;
        getPhone().registerForPreciseCallStateChanged(
                mHandler, MSG_PRECISE_CALL_STATE_CHANGED, null);
        getPhone().registerForRingbackTone(mHandler, MSG_RINGBACK_TONE, null);
        mOriginalConnection.addPostDialListener(mPostDialListener);
        mOriginalConnection.addListener(mOriginalConnectionListener);

        // Set video state and capabilities
        setVideoState(mOriginalConnection.getVideoState());
        setLocalVideoCapable(mOriginalConnection.isLocalVideoCapable());
        setRemoteVideoCapable(mOriginalConnection.isRemoteVideoCapable());
        setVideoCallProvider(mOriginalConnection.getVideoCallProvider());
        setAudioQuality(mOriginalConnection.getAudioQuality());

        updateHandle();
    }

    @Override
    public void onSetAudioState(CallAudioState audioState) {
        // TODO: update TTY mode.
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onSetState(int state) {
        Log.v(this, "onSetState, state: " + Connection.stateToString(state));
    }

    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect");
        hangup(DisconnectCause.LOCAL);
    }

    @Override
    public void onSeparate() {
        Log.v(this, "onSeparate");
        if (mOriginalConnection != null) {
            try {
                mOriginalConnection.separate();
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.separate failed with exception");
            }
        }
    }

    @Override
    public void onAbort() {
        Log.v(this, "onAbort");
        hangup(DisconnectCause.LOCAL);
    }

    @Override
    public void onHold() {
        Log.v(this, "onHold");
        // TODO: Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mOriginalConnectionState) {
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
                // TODO: Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                }

                // TODO: Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }

    @Override
    public void onUnhold() {
        Log.v(this, "onUnhold");
        if (Call.State.HOLDING == mOriginalConnectionState) {
            try {
                // TODO: This doesn't handle multiple calls across connection services yet
                mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    @Override
    public void onAnswer(int videoState) {
        Log.v(this, "onAnswer");
        // TODO: Tons of hairy logic is missing here around multiple active calls on
        // CDMA devices. See {@link CallManager.acceptCall}.

        if (isValidRingingCall() && getPhone() != null) {
            try {
                getPhone().acceptCall(videoState);
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call.");
            }
        }
    }

    @Override
    public void onReject() {
        Log.v(this, "onReject");
        if (isValidRingingCall()) {
            hangup(DisconnectCause.INCOMING_REJECTED);
        }
        super.onReject();
    }

    @Override
    public void onPostDialContinue(boolean proceed) {
        Log.v(this, "onPostDialContinue, proceed: " + proceed);
        if (mOriginalConnection != null) {
            if (proceed) {
                mOriginalConnection.proceedAfterWaitChar();
            } else {
                mOriginalConnection.cancelPostDial();
            }
        }
    }

    @Override
    public void onSwapWithBackgroundCall() {
        Log.v(this, "onSwapWithBackgroundCall");
    }

    @Override
    public void onChildrenChanged(List<Connection> children) {
        Log.v(this, "onChildrenChanged, children: " + children);
    }

    @Override
    public void onPhoneAccountClicked() {
        Log.v(this, "onPhoneAccountClicked");
    }

    protected abstract int buildCallCapabilities();

    protected final void updateCallCapabilities() {
        int newCallCapabilities = buildCallCapabilities();
        newCallCapabilities = applyVideoCapabilities(newCallCapabilities);
        newCallCapabilities = applyAudioQualityCapabilities(newCallCapabilities);

        if (getCallCapabilities() != newCallCapabilities) {
            setCallCapabilities(newCallCapabilities);
        }
    }

    protected final void updateHandle() {
        updateCallCapabilities();
        if (mOriginalConnection != null) {
            Uri handle = getHandleFromAddress(mOriginalConnection.getAddress());
            int presentation = mOriginalConnection.getNumberPresentation();
            if (!Objects.equals(handle, getHandle()) ||
                    presentation != getHandlePresentation()) {
                Log.v(this, "updateHandle, handle changed");
                setHandle(handle, presentation);
            }

            String name = mOriginalConnection.getCnapName();
            int namePresentation = mOriginalConnection.getCnapNamePresentation();
            if (!Objects.equals(name, getCallerDisplayName()) ||
                    namePresentation != getCallerDisplayNamePresentation()) {
                Log.v(this, "updateHandle, caller display name changed");
                setCallerDisplayName(name, namePresentation);
            }
        }
    }

    void onRemovedFromCallService() {
        // Subclass can override this to do cleanup.
    }

    private void hangup(int disconnectCause) {
        if (mOriginalConnection != null) {
            try {
                Call call = mOriginalConnection.getCall();
                if (call != null && !call.isMultiparty()) {
                    call.hangup();
                } else {
                    mOriginalConnection.hangup();
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

    com.android.internal.telephony.Connection getOriginalConnection() {
        return mOriginalConnection;
    }

    protected Call getCall() {
        if (mOriginalConnection != null) {
            return mOriginalConnection.getCall();
        }
        return null;
    }

    Phone getPhone() {
        Call call = getCall();
        if (call != null) {
            return call.getPhone();
        }
        return null;
    }

    private com.android.internal.telephony.Connection getForegroundConnection() {
        if (getPhone() != null) {
            return getPhone().getForegroundCall().getEarliestConnection();
        }
        return null;
    }

    /**
     * Checks to see the original connection corresponds to an active incoming call. Returns false
     * if there is no such actual call, or if the associated call is not incoming (See
     * {@link Call.State#isRinging}).
     */
    private boolean isValidRingingCall() {
        if (getPhone() == null) {
            Log.v(this, "isValidRingingCall, phone is null");
            return false;
        }

        Call ringingCall = getPhone().getRingingCall();
        if (!ringingCall.getState().isRinging()) {
            Log.v(this, "isValidRingingCall, ringing call is not in ringing state");
            return false;
        }

        if (ringingCall.getEarliestConnection() != mOriginalConnection) {
            Log.v(this, "isValidRingingCall, ringing call connection does not match");
            return false;
        }

        Log.v(this, "isValidRingingCall, returning true");
        return true;
    }

    private void updateState() {
        if (mOriginalConnection == null) {
            return;
        }

        Call.State newState = mOriginalConnection.getState();
        Log.v(this, "Update state from %s to %s for %s", mOriginalConnectionState, newState, this);
        if (mOriginalConnectionState != newState) {
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
        }
        updateCallCapabilities();
        updateHandle();
    }

    private void close() {
        Log.v(this, "close");
        if (getPhone() != null) {
            getPhone().unregisterForPreciseCallStateChanged(mHandler);
            getPhone().unregisterForRingbackTone(mHandler);
        }
        mOriginalConnection = null;
        destroy();
    }

    /**
     * Applies the video capability states to the CallCapabilities bit-mask.
     *
     * @param capabilities The CallCapabilities bit-mask.
     * @return The capabilities with video capabilities applied.
     */
    private int applyVideoCapabilities(int capabilities) {
        int currentCapabilities = capabilities;
        if (mRemoteVideoCapable) {
            currentCapabilities = applyCapability(currentCapabilities,
                    CallCapabilities.SUPPORTS_VT_REMOTE);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CallCapabilities.SUPPORTS_VT_REMOTE);
        }

        if (mLocalVideoCapable) {
            currentCapabilities = applyCapability(currentCapabilities,
                    CallCapabilities.SUPPORTS_VT_LOCAL);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CallCapabilities.SUPPORTS_VT_LOCAL);
        }
        return currentCapabilities;
    }

    /**
     * Applies the audio capabilities to the {@code CallCapabilities} bit-mask.  A call with high
     * definition audio is considered to have the {@code VoLTE} call capability as VoLTE uses high
     * definition audio.
     *
     * @param callCapabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the audio capabilities applied.
     */
    private int applyAudioQualityCapabilities(int callCapabilities) {
        int currentCapabilities = callCapabilities;

        if (mAudioQuality ==
                com.android.internal.telephony.Connection.AUDIO_QUALITY_HIGH_DEFINITION) {
            currentCapabilities = applyCapability(currentCapabilities, CallCapabilities.VoLTE);
        } else {
            currentCapabilities = removeCapability(currentCapabilities, CallCapabilities.VoLTE);
        }

        return currentCapabilities;
    }

    /**
     * Returns the local video capability state for the connection.
     *
     * @return {@code True} if the connection has local video capabilities.
     */
    public boolean isLocalVideoCapable() {
        return mLocalVideoCapable;
    }

    /**
     * Returns the remote video capability state for the connection.
     *
     * @return {@code True} if the connection has remote video capabilities.
     */
    public boolean isRemoteVideoCapable() {
        return mRemoteVideoCapable;
    }

    /**
     * Sets whether video capability is present locally.  Used during rebuild of the
     * {@link CallCapabilities} to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setLocalVideoCapable(boolean capable) {
        mLocalVideoCapable = capable;
        updateCallCapabilities();
    }

    /**
     * Sets whether video capability is present remotely.  Used during rebuild of the
     * {@link CallCapabilities} to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setRemoteVideoCapable(boolean capable) {
        mRemoteVideoCapable = capable;
        updateCallCapabilities();
    }

    /**
     * Sets the current call audio quality.  Used during rebuild of the
     * {@link CallCapabilities} to set or unset the {@link CallCapabilities#VoLTE} capability.
     *
     * @param audioQuality The audio quality.
     */
    public void setAudioQuality(int audioQuality) {
        mAudioQuality = audioQuality;
        updateCallCapabilities();
    }

    private static Uri getHandleFromAddress(String address) {
        // Address can be null for blocked calls.
        if (address == null) {
            address = "";
        }
        return Uri.fromParts(TelephonyConnectionService.SCHEME_TEL, address, null);
    }

    /**
     * Applies a capability to a capabilities bit-mask.
     *
     * @param capabilities The capabilities bit-mask.
     * @param capability The capability to apply.
     * @return The capabilities bit-mask with the capability applied.
     */
    private int applyCapability(int capabilities, int capability) {
        int newCapabilities = capabilities | capability;
        return newCapabilities;
    }

    /**
     * Removes a capability from a capabilities bit-mask.
     *
     * @param capabilities The capabilities bit-mask.
     * @param capability The capability to remove.
     * @return The capabilities bit-mask with the capability removed.
     */
    private int removeCapability(int capabilities, int capability) {
        int newCapabilities = capabilities & ~capability;
        return newCapabilities;
    }
}
