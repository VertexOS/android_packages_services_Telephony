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

package com.android.services.telecomm;

import com.google.android.collect.Sets;

import android.net.Uri;
import android.os.Bundle;
import android.telecomm.CallAudioState;

import java.util.Set;

/**
 * Represents a connection to a remote endpoint that carries voice traffic.
 */
public abstract class Connection {

    public interface Listener {
        void onStateChanged(Connection c, int state);
        void onAudioStateChanged(Connection c, CallAudioState state);
        void onHandleChanged(Connection c, Uri newHandle);
        void onSignalChanged(Connection c, Bundle details);
        void onDestroyed(Connection c);
    }

    public final class State {
        private State() {}

        public static final int NEW = 0;
        public static final int RINGING = 1;
        public static final int DIALING = 2;
        public static final int ACTIVE = 3;
        public static final int HOLDING = 4;
        public static final int DISCONNECTED = 5;
    }

    private final Set<Listener> mListeners = Sets.newHashSet();
    private int mState = State.NEW;
    private CallAudioState mCallAudioState;
    private Uri mHandle;
    private long mStartTime = -1L;
    private long mEndTime = -1L;

    /**
     * Create a new Connection.
     */
    protected Connection() {
    }

    /**
     * @return The handle (e.g., phone number) to which this Connection
     *         is currently communicating.
     *
     * @hide
     */
    public final Uri getHandle() {
        return mHandle;
    }

    /**
     * @return The state of this Connection.
     *
     * @hide
     */
    public final int getState() {
        return mState;
    }

    /**
     * @return The audio state of the call, describing how its audio is currently
     *         being routed by the system. This is {@code null} if this Connection
     *         does not directly know about its audio state.
     *
     * @hide
     */
    public final CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    /**
     * Assign a listener to be notified of state changes.
     *
     * @param l A listener.
     *
     * @hide
     */
    public final void addConnectionListener(Listener l) {
        mListeners.add(l);
    }

    /**
     * Remove a previously assigned listener that was being notified of state changes.
     *
     * @param l A Listener.
     *
     * @hide
     */
    public final void removeConnectionListener(Listener l) {
        mListeners.remove(l);
    }

    /**
     * @return The system time at which this Connection transitioned into the
     *         {@link State#ACTIVE} state. This value is {@code -1L}
     *         if it has not been explicitly assigned.
     *
     * @hide
     */
    public final long getStartTime() {
        return mStartTime;
    }

    /**
     * @return The system time at which this Connection transitioned into the
     *         {@link State#DISCONNECTED} state. This value is
     *         {@code -1L} if it has not been explicitly assigned.
     *
     * @hide
     */
    public final long getEndTime() {
        return mEndTime;
    }

    /**
     * Play a DTMF tone in this Connection.
     *
     * @param c A DTMF character.
     *
     * @hide
     */
    public final void playDtmfTone(char c) {
        onPlayDtmfTone(c);
    }

    /**
     * Stop any DTMF tones which may be playing in this Connection.
     *
     * @hide
     */
    public final void stopDtmfTone() {
        onStopDtmfTone();
    }

    /**
     * Disconnect this Connection. If and when the Connection can comply with
     * this request, it will transition to the {@link State#DISCONNECTED}
     * state and notify its listeners.
     *
     * @hide
     */
    public final void disconnect() {
        onDisconnect();
    }

    /**
     * Abort this Connection. The Connection will immediately transition to
     * the {@link State#DISCONNECTED} state, and send no notifications of this
     * or any other future events.
     *
     * @hide
     */
    public final void abort() {
        onAbort();
    }

    /**
     * Place this Connection on hold. If and when the Connection can comply with
     * this request, it will transition to the {@link State#HOLDING}
     * state and notify its listeners.
     *
     * @hide
     */
    public final void hold() {
        onHold();
    }

    /**
     * Un-hold this Connection. If and when the Connection can comply with
     * this request, it will transition to the {@link State#ACTIVE}
     * state and notify its listeners.
     *
     * @hide
     */
    public final void unhold() {
        onUnhold();
    }

    /**
     * Accept a {@link State#RINGING} Connection. If and when the Connection
     * can comply with this request, it will transition to the {@link State#ACTIVE}
     * state and notify its listeners.
     *
     * @hide
     */
    public final void answer() {
        if (mState == State.RINGING) {
            onAnswer();
        }
    }

    /**
     * Reject a {@link State#RINGING} Connection. If and when the Connection
     * can comply with this request, it will transition to the {@link State#ACTIVE}
     * state and notify its listeners.
     *
     * @hide
     */
    public final void reject() {
        if (mState == State.RINGING) { onReject(); }
    }

    /**
     * Inform this Connection that the state of its audio output has been changed externally.
     *
     * @param state The new audio state.
     */
    public void setAudioState(CallAudioState state) {
        onSetAudioState(state);
    }

    /**
     * Notifies this Connection and listeners that the {@link #getHandle()} property
     * has a new value.
     *
     * @param handle The new handle.
     */
    protected void onSetHandle(Uri handle) {
        // TODO: Enforce super called
        mHandle = handle;
        for (Listener l : mListeners) {
            l.onHandleChanged(this, handle);
        }
    }

    /**
     * Notifies this Connection and listeners that the {@link #getState()} property
     * has a new value.
     *
     * @param state The new state.
     */
    protected void onSetState(int state) {
        // TODO: Enforce super called
        this.mState = state;
        // TODO: This can also check for only VALID state transitions
        if (state == State.ACTIVE) {
            mStartTime = System.currentTimeMillis();
        }
        if (state == State.DISCONNECTED) {
            mEndTime = System.currentTimeMillis();
        }
        for (Listener l : mListeners) {
            l.onStateChanged(this, state);
        }
    }

    /**
     * Notifies this Connection and listeners that the {@link #getState()} property
     * has a new value, and specifies a reason.
     *
     * TODO: needed for disconnect cause -- consider how that will be supported
     *
     * @param state The new state.
     * @param reason The reason for the change.
     */
    protected void onSetState(int state, String reason) {
        // TODO: Enforce super called
    }

    /**
     * Notifies this Connection and listeners that the {@link #getCallAudioState()} property
     * has a new value.
     *
     * @param state The new call audio state.
     */
    protected void onSetAudioState(CallAudioState state) {
        // TODO: Enforce super called
        this.mCallAudioState = state;
        for (Listener l : mListeners) {
            l.onAudioStateChanged(this, state);
        }
    }

    /**
     * Notifies this Connection and listeners of a change in the current signal levels
     * for the underlying data transport.
     *
     * @param details A {@link android.os.Bundle} containing details of the current level.
     */
    protected void onSetSignal(Bundle details) {
        // TODO: Enforce super called
        for (Listener l : mListeners) {
            l.onSignalChanged(this, details);
        }
    }

    /**
     * Notifies this Connection of a request to play a DTMF tone.
     *
     * @param c A DTMF character.
     */
    protected void onPlayDtmfTone(char c) {}

    /**
     * Notifies this Connection of a request to stop any currently playing DTMF tones.
     */
    protected void onStopDtmfTone() {}

    /**
     * Notifies this Connection of a request to disconnect.
     */
    protected void onDisconnect() {}

    /**
     * Notifies this Connection of a request to abort.
     */
    protected void onAbort() {}

    /**
     * Notifies this Connection of a request to hold.
     */
    protected void onHold() {}

    /**
     * Notifies this Connection of a request to exit a hold state.
     */
    protected void onUnhold() {}

    /**
     * Notifies this Connection, which is in {@link State#RINGING}, of
     * a request to accept.
     */
    protected void onAnswer() {}

    /**
     * Notifies this Connection, which is in {@link State#RINGING}, of
     * a request to reject.
     */
    protected void onReject() {}
}
