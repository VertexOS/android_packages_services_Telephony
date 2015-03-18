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
import android.telecom.AudioState;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.PhoneAccount;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection.PostDialListener;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;

import java.lang.Override;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for CDMA and GSM connections.
 */
abstract class TelephonyConnection extends Connection {
    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;
    private static final int MSG_RINGBACK_TONE = 2;
    private static final int MSG_HANDOVER_STATE_CHANGED = 3;
    private static final int MSG_DISCONNECT = 4;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_PRECISE_CALL_STATE_CHANGED");
                    updateState();
                    break;
                case MSG_HANDOVER_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_HANDOVER_STATE_CHANGED");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    com.android.internal.telephony.Connection connection =
                         (com.android.internal.telephony.Connection) ar.result;
                    if ((connection.getAddress() != null &&
                                    mOriginalConnection.getAddress() != null &&
                            mOriginalConnection.getAddress().contains(connection.getAddress())) ||
                            connection.getStateBeforeHandover() == mOriginalConnection.getState()) {
                        Log.d(TelephonyConnection.this, "SettingOriginalConnection " +
                                mOriginalConnection.toString() + " with " + connection.toString());
                        setOriginalConnection(connection);
                    }
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
                    setRingbackRequested((Boolean) ((AsyncResult) msg.obj).result);
                    break;
                case MSG_DISCONNECT:
                    updateState();
                    break;
            }
        }
    };

    /**
     * A listener/callback mechanism that is specific communication from TelephonyConnections
     * to TelephonyConnectionService (for now). It is more specific that Connection.Listener
     * because it is only exposed in Telephony.
     */
    public abstract static class TelephonyConnectionListener {
        public void onOriginalConnectionConfigured(TelephonyConnection c) {}
    }

    private final PostDialListener mPostDialListener = new PostDialListener() {
        @Override
        public void onPostDialWait() {
            Log.v(TelephonyConnection.this, "onPostDialWait");
            if (mOriginalConnection != null) {
                setPostDialWait(mOriginalConnection.getRemainingPostDialString());
            }
        }

        @Override
        public void onPostDialChar(char c) {
            Log.v(TelephonyConnection.this, "onPostDialChar: %s", c);
            if (mOriginalConnection != null) {
                setNextPostDialWaitChar(c);
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
         * @param videoProvider The video call provider.
         */
        @Override
        public void onVideoProviderChanged(VideoProvider videoProvider) {
            setVideoProvider(videoProvider);
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

        /**
         * Handles a change in the state of conference participant(s), as reported by the
         * {@link com.android.internal.telephony.Connection}.
         *
         * @param participants The participant(s) which changed.
         */
        @Override
        public void onConferenceParticipantsChanged(List<ConferenceParticipant> participants) {
            updateConferenceParticipants(participants);
        }
    };

    private com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mOriginalConnectionState = Call.State.IDLE;

    private boolean mWasImsConnection;

    /**
     * Tracks the multiparty state of the ImsCall so that changes in the bit state can be detected.
     */
    private boolean mIsMultiParty = false;

    /**
     * Determines if the {@link TelephonyConnection} has local video capabilities.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities()}} is called,
     * ensuring the appropriate capabilities are set.  Since capabilities
     * can be rebuilt at any time it is necessary to track the video capabilities between rebuild.
     * The capabilities (including video capabilities) are communicated to the telecom
     * layer.
     */
    private boolean mLocalVideoCapable;

    /**
     * Determines if the {@link TelephonyConnection} has remote video capabilities.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities()}} is called,
     * ensuring the appropriate capabilities are set.  Since capabilities can be rebuilt at any time
     * it is necessary to track the video capabilities between rebuild. The capabilities (including
     * video capabilities) are communicated to the telecom layer.
     */
    private boolean mRemoteVideoCapable;

    /**
     * Determines the current audio quality for the {@link TelephonyConnection}.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities}} is called to
     * indicate whether a call has the {@link Connection#CAPABILITY_HIGH_DEF_AUDIO} capability.
     */
    private int mAudioQuality;

    /**
     * Listeners to our TelephonyConnection specific callbacks
     */
    private final Set<TelephonyConnectionListener> mTelephonyListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<TelephonyConnectionListener, Boolean>(8, 0.9f, 1));

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection) {
        if (originalConnection != null) {
            setOriginalConnection(originalConnection);
        }
    }

    /**
     * Creates a clone of the current {@link TelephonyConnection}.
     *
     * @return The clone.
     */
    public abstract TelephonyConnection cloneConnection();

    @Override
    public void onAudioStateChanged(AudioState audioState) {
        // TODO: update TTY mode.
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onStateChanged(int state) {
        Log.v(this, "onStateChanged, state: " + Connection.stateToString(state));
    }

    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect");
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    /**
     * Notifies this Connection of a request to disconnect a participant of the conference managed
     * by the connection.
     *
     * @param endpoint the {@link Uri} of the participant to disconnect.
     */
    @Override
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        Log.v(this, "onDisconnectConferenceParticipant %s", endpoint);

        if (mOriginalConnection == null) {
            return;
        }

        mOriginalConnection.onDisconnectConferenceParticipant(endpoint);
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
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    @Override
    public void onHold() {
        performHold();
    }

    @Override
    public void onUnhold() {
        performUnhold();
    }

    @Override
    public void onAnswer(int videoState) {
        Log.v(this, "onAnswer");
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
            hangup(android.telephony.DisconnectCause.INCOMING_REJECTED);
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

    public void performHold() {
        Log.v(this, "performHold");
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
                // while a call-waiting call is happening is likely a request from Telecom prior to
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

    public void performUnhold() {
        Log.v(this, "performUnhold");
        if (Call.State.HOLDING == mOriginalConnectionState) {
            try {
                // Here's the deal--Telephony hold/unhold is weird because whenever there exists
                // more than one call, one of them must always be active. In other words, if you
                // have an active call and holding call, and you put the active call on hold, it
                // will automatically activate the holding call. This is weird with how Telecom
                // sends its commands. When a user opts to "unhold" a background call, telecom
                // issues hold commands to all active calls, and then the unhold command to the
                // background call. This means that we get two commands...each of which reduces to
                // switchHoldingAndActive(). The result is that they simply cancel each other out.
                // To fix this so that it works well with telecom we add a minor hack. If we
                // have one telephony call, everything works as normally expected. But if we have
                // two or more calls, we will ignore all requests to "unhold" knowing that the hold
                // requests already do what we want. If you've read up to this point, I'm very sorry
                // that we are doing this. I didn't think of a better solution that wouldn't also
                // make the Telecom APIs very ugly.

                if (!hasMultipleTopLevelCalls()) {
                    mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
                } else {
                    Log.i(this, "Skipping unhold command for %s", this);
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    public void performConference(TelephonyConnection otherConnection) {
        Log.d(this, "performConference - %s", this);
        if (getPhone() != null) {
            try {
                // We dont use the "other" connection because there is no concept of that in the
                // implementation of calls inside telephony. Basically, you can "conference" and it
                // will conference with the background call.  We know that otherConnection is the
                // background call because it would never have called setConferenceableConnections()
                // otherwise.
                getPhone().conference();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to conference call.");
            }
        }
    }

    /**
     * Builds call capabilities common to all TelephonyConnections. Namely, apply IMS-based
     * capabilities.
     */
    protected int buildConnectionCapabilities() {
        int callCapabilities = 0;
        if (isImsConnection()) {
            if (mOriginalConnection.isIncoming()) {
                callCapabilities |= CAPABILITY_SPEED_UP_MT_AUDIO;
            }
            callCapabilities |= CAPABILITY_SUPPORT_HOLD;
            if (getState() == STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= CAPABILITY_HOLD;
            }
        }
        return callCapabilities;
    }

    protected final void updateConnectionCapabilities() {
        int newCapabilities = buildConnectionCapabilities();
        newCapabilities = applyVideoCapabilities(newCapabilities);
        newCapabilities = applyAudioQualityCapabilities(newCapabilities);
        newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);

        if (getConnectionCapabilities() != newCapabilities) {
            setConnectionCapabilities(newCapabilities);
        }
    }

    protected final void updateAddress() {
        updateConnectionCapabilities();
        if (mOriginalConnection != null) {
            Uri address = getAddressFromNumber(mOriginalConnection.getAddress());
            int presentation = mOriginalConnection.getNumberPresentation();
            if (!Objects.equals(address, getAddress()) ||
                    presentation != getAddressPresentation()) {
                Log.v(this, "updateAddress, address changed");
                setAddress(address, presentation);
            }

            String name = mOriginalConnection.getCnapName();
            int namePresentation = mOriginalConnection.getCnapNamePresentation();
            if (!Objects.equals(name, getCallerDisplayName()) ||
                    namePresentation != getCallerDisplayNamePresentation()) {
                Log.v(this, "updateAddress, caller display name changed");
                setCallerDisplayName(name, namePresentation);
            }
        }
    }

    void onRemovedFromCallService() {
        // Subclass can override this to do cleanup.
    }

    void setOriginalConnection(com.android.internal.telephony.Connection originalConnection) {
        Log.v(this, "new TelephonyConnection, originalConnection: " + originalConnection);
        clearOriginalConnection();

        mOriginalConnection = originalConnection;
        getPhone().registerForPreciseCallStateChanged(
                mHandler, MSG_PRECISE_CALL_STATE_CHANGED, null);
        getPhone().registerForHandoverStateChanged(
                mHandler, MSG_HANDOVER_STATE_CHANGED, null);
        getPhone().registerForRingbackTone(mHandler, MSG_RINGBACK_TONE, null);
        getPhone().registerForDisconnect(mHandler, MSG_DISCONNECT, null);
        mOriginalConnection.addPostDialListener(mPostDialListener);
        mOriginalConnection.addListener(mOriginalConnectionListener);

        // Set video state and capabilities
        setVideoState(mOriginalConnection.getVideoState());
        setLocalVideoCapable(mOriginalConnection.isLocalVideoCapable());
        setRemoteVideoCapable(mOriginalConnection.isRemoteVideoCapable());
        setVideoProvider(mOriginalConnection.getVideoProvider());
        setAudioQuality(mOriginalConnection.getAudioQuality());

        if (isImsConnection()) {
            mWasImsConnection = true;
        }
        mIsMultiParty = mOriginalConnection.isMultiparty();

        fireOnOriginalConnectionConfigured();
        updateAddress();
    }

    /**
     * Un-sets the underlying radio connection.
     */
    void clearOriginalConnection() {
        if (mOriginalConnection != null) {
            getPhone().unregisterForPreciseCallStateChanged(mHandler);
            getPhone().unregisterForRingbackTone(mHandler);
            getPhone().unregisterForHandoverStateChanged(mHandler);
            getPhone().unregisterForDisconnect(mHandler);
            mOriginalConnection = null;
        }
    }

    protected void hangup(int telephonyDisconnectCode) {
        if (mOriginalConnection != null) {
            try {
                // Hanging up a ringing call requires that we invoke call.hangup() as opposed to
                // connection.hangup(). Without this change, the party originating the call will not
                // get sent to voicemail if the user opts to reject the call.
                if (isValidRingingCall()) {
                    Call call = getCall();
                    if (call != null) {
                        call.hangup();
                    } else {
                        Log.w(this, "Attempting to hangup a connection without backing call.");
                    }
                } else {
                    // We still prefer to call connection.hangup() for non-ringing calls in order
                    // to support hanging-up specific calls within a conference call. If we invoked
                    // call.hangup() while in a conference, we would end up hanging up the entire
                    // conference call instead of the specific connection.
                    mOriginalConnection.hangup();
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
            }
        }
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

    private boolean hasMultipleTopLevelCalls() {
        int numCalls = 0;
        Phone phone = getPhone();
        if (phone != null) {
            if (!phone.getRingingCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getForegroundCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getBackgroundCall().isIdle()) {
                numCalls++;
            }
        }
        return numCalls > 1;
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

    void updateState() {
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
                    setActiveInternal();
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
                    setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                            mOriginalConnection.getDisconnectCause()));
                    close();
                    break;
                case DISCONNECTING:
                    break;
            }
        }
        updateConnectionCapabilities();
        updateAddress();
        updateMultiparty();
    }

    /**
     * Checks for changes to the multiparty bit.  If a conference has started, informs listeners.
     */
    private void updateMultiparty() {
        if (mOriginalConnection == null) {
            return;
        }

        if (mIsMultiParty != mOriginalConnection.isMultiparty()) {
            mIsMultiParty = mOriginalConnection.isMultiparty();

            if (mIsMultiParty) {
                notifyConferenceStarted();
            }
        }
    }

    private void setActiveInternal() {
        if (getState() == STATE_ACTIVE) {
            Log.w(this, "Should not be called if this is already ACTIVE");
            return;
        }

        // When we set a call to active, we need to make sure that there are no other active
        // calls. However, the ordering of state updates to connections can be non-deterministic
        // since all connections register for state changes on the phone independently.
        // To "optimize", we check here to see if there already exists any active calls.  If so,
        // we issue an update for those calls first to make sure we only have one top-level
        // active call.
        if (getConnectionService() != null) {
            for (Connection current : getConnectionService().getAllConnections()) {
                if (current != this && current instanceof TelephonyConnection) {
                    TelephonyConnection other = (TelephonyConnection) current;
                    if (other.getState() == STATE_ACTIVE) {
                        other.updateState();
                    }
                }
            }
        }
        setActive();
    }

    private void close() {
        Log.v(this, "close");
        if (getPhone() != null) {
            getPhone().unregisterForPreciseCallStateChanged(mHandler);
            getPhone().unregisterForRingbackTone(mHandler);
            getPhone().unregisterForHandoverStateChanged(mHandler);
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
                    CAPABILITY_SUPPORTS_VT_REMOTE);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_REMOTE);
        }

        if (mLocalVideoCapable) {
            currentCapabilities = applyCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_LOCAL);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_LOCAL);
        }
        return currentCapabilities;
    }

    /**
     * Applies the audio capabilities to the {@code CallCapabilities} bit-mask.  A call with high
     * definition audio is considered to have the {@code HIGH_DEF_AUDIO} call capability.
     *
     * @param capabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the audio capabilities applied.
     */
    private int applyAudioQualityCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        if (mAudioQuality ==
                com.android.internal.telephony.Connection.AUDIO_QUALITY_HIGH_DEFINITION) {
            currentCapabilities = applyCapability(currentCapabilities, CAPABILITY_HIGH_DEF_AUDIO);
        } else {
            currentCapabilities = removeCapability(currentCapabilities, CAPABILITY_HIGH_DEF_AUDIO);
        }

        return currentCapabilities;
    }

    /**
     * Applies capabilities specific to conferences termination to the
     * {@code CallCapabilities} bit-mask.
     *
     * @param capabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the IMS conference capabilities applied.
     */
    private int applyConferenceTerminationCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        // An IMS call cannot be individually disconnected or separated from its parent conference.
        // If the call was IMS, even if it hands over to GMS, these capabilities are not supported.
        if (!mWasImsConnection) {
            currentCapabilities |= CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            currentCapabilities |= CAPABILITY_SEPARATE_FROM_CONFERENCE;
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
     * capabilities to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setLocalVideoCapable(boolean capable) {
        mLocalVideoCapable = capable;
        updateConnectionCapabilities();
    }

    /**
     * Sets whether video capability is present remotely.  Used during rebuild of the
     * capabilities to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setRemoteVideoCapable(boolean capable) {
        mRemoteVideoCapable = capable;
        updateConnectionCapabilities();
    }

    /**
     * Sets the current call audio quality.  Used during rebuild of the capabilities
     * to set or unset the {@link Connection#CAPABILITY_HIGH_DEF_AUDIO} capability.
     *
     * @param audioQuality The audio quality.
     */
    public void setAudioQuality(int audioQuality) {
        mAudioQuality = audioQuality;
        updateConnectionCapabilities();
    }

    /**
     * Obtains the current call audio quality.
     */
    public int getAudioQuality() {
        return mAudioQuality;
    }

    void resetStateForConference() {
        if (getState() == Connection.STATE_HOLDING) {
            if (mOriginalConnection.getState() == Call.State.ACTIVE) {
                setActive();
            }
        }
    }

    boolean setHoldingForConference() {
        if (getState() == Connection.STATE_ACTIVE) {
            setOnHold();
            return true;
        }
        return false;
    }

    /**
     * Whether the original connection is an IMS connection.
     * @return {@code True} if the original connection is an IMS connection, {@code false}
     *     otherwise.
     */
    protected boolean isImsConnection() {
        return getOriginalConnection() instanceof ImsPhoneConnection;
    }

    /**
     * Whether the original connection was ever an IMS connection, either before or now.
     * @return {@code True} if the original connection was ever an IMS connection, {@code false}
     *     otherwise.
     */
    public boolean wasImsConnection() {
        return mWasImsConnection;
    }

    private static Uri getAddressFromNumber(String number) {
        // Address can be null for blocked calls.
        if (number == null) {
            number = "";
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
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

    /**
     * Register a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to add
     * @return The connection being listened to
     */
    public final TelephonyConnection addTelephonyConnectionListener(TelephonyConnectionListener l) {
        mTelephonyListeners.add(l);
        // If we already have an original connection, let's call back immediately.
        // This would be the case for incoming calls.
        if (mOriginalConnection != null) {
            fireOnOriginalConnectionConfigured();
        }
        return this;
    }

    /**
     * Remove a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to remove
     * @return The connection being listened to
     */
    public final TelephonyConnection removeTelephonyConnectionListener(
            TelephonyConnectionListener l) {
        if (l != null) {
            mTelephonyListeners.remove(l);
        }
        return this;
    }

    /**
     * Fire a callback to the various listeners for when the original connection is
     * set in this {@link TelephonyConnection}
     */
    private final void fireOnOriginalConnectionConfigured() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionConfigured(this);
        }
    }

    /**
     * Creates a string representation of this {@link TelephonyConnection}.  Primarily intended for
     * use in log statements.
     *
     * @return String representation of the connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[TelephonyConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" type:");
        if (isImsConnection()) {
            sb.append("ims");
        } else if (this instanceof com.android.services.telephony.GsmConnection) {
            sb.append("gsm");
        } else if (this instanceof CdmaConnection) {
            sb.append("cdma");
        }
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append(" capabilities:");
        sb.append(capabilitiesToString(getConnectionCapabilities()));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" originalConnection:");
        sb.append(mOriginalConnection);
        sb.append(" partOfConf:");
        if (getConference() == null) {
            sb.append("N");
        } else {
            sb.append("Y");
        }
        sb.append("]");
        return sb.toString();
    }
}
