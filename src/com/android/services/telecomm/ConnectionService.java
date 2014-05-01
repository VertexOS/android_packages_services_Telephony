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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import android.net.Uri;
import android.os.Bundle;
import android.telecomm.CallAudioState;
import android.telecomm.CallInfo;
import android.telecomm.CallService;
import android.telecomm.CallState;
import android.util.Log;

/**
 * Provides actual voice connections to the Android framework and to other processes
 * running on this device.
 */
public abstract class ConnectionService extends CallService {

    private static final String TAG = ConnectionService.class.getName();

    private static final Connection NULL_CONNECTION = new Connection() {};

    // Mappings from Connections to IDs as understood by the current CallService implementation
    private final BiMap<String, Connection> mConnectionById = HashBiMap.create();

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            switch (state) {
                case Connection.State.ACTIVE:
                    getAdapter().setActive(mConnectionById.inverse().get(c));
                    break;
                case Connection.State.DIALING:
                    getAdapter().setDialing(mConnectionById.inverse().get(c));
                    break;
                case Connection.State.DISCONNECTED:
                    // TODO: Disconnect cause and description
                    getAdapter().setDisconnected(mConnectionById.inverse().get(c), 0, null);
                    break;
                case Connection.State.HOLDING:
                    getAdapter().setOnHold(mConnectionById.inverse().get(c));
                    break;
                case Connection.State.NEW:
                    // Nothing to tell Telecomm
                    break;
                case Connection.State.RINGING:
                    getAdapter().setRinging(mConnectionById.inverse().get(c));
                    break;
            }
        }

        @Override
        public void onHandleChanged(Connection c, Uri newHandle) {
            // TODO: Unsupported yet
        }

        @Override
        public void onAudioStateChanged(Connection c, CallAudioState state) {
            // TODO: Unsupported yet
        }

        @Override
        public void onSignalChanged(Connection c, Bundle details) {
            // TODO: Unsupported yet
        }

        @Override
        public void onDestroyed(Connection c) {
            c.removeConnectionListener(this);
            String id = mConnectionById.inverse().get(c);
            mConnectionById.get(id).removeConnectionListener(mConnectionListener);
            mConnectionById.remove(id);
        }
    };

    @Override
    public final void isCompatibleWith(final CallInfo callInfo) {
        onFindSubscriptions(
                callInfo.getHandle(),
                new Response<Uri, Subscription>() {
                    @Override
                    public void onResult(Uri handle, Subscription... result) {
                        getAdapter().setIsCompatibleWith(callInfo.getId(), result.length > 0);
                    }

                    @Override
                    public void onError(Uri handle, String reason) {
                        Log.wtf(TAG, "Error in onFindSubscriptions " + callInfo.getHandle()
                                + " error: " + reason);
                    }
                }
        );
    }

    @Override
    public final void call(final CallInfo callInfo) {
        onCreateConnections(
                new ConnectionRequest(
                        callInfo.getHandle(),
                        callInfo.getExtras()),
                new Response<ConnectionRequest, Connection>() {
                    @Override
                    public void onResult(ConnectionRequest request, Connection... result) {
                        if (result.length != 1) {
                            getAdapter().handleFailedOutgoingCall(
                                    callInfo.getId(),
                                    "Created " + result.length + " Connections, expected 1");
                            for (int i = 0; i < result.length; i++) {
                                result[i].abort();
                            }
                        } else {
                            mConnectionById.put(callInfo.getId(), result[0]);
                            result[0].addConnectionListener(mConnectionListener);
                            getAdapter().handleSuccessfulOutgoingCall(callInfo.getId());
                        }
                    }

                    @Override
                    public void onError(ConnectionRequest request, String reason) {
                        getAdapter().handleFailedOutgoingCall(callInfo.getId(), reason);
                    }
                }
        );
    }

    @Override
    public final void abort(String callId) {
        findConnectionForAction(callId, "abort").abort();
    }

    @Override
    public final void setIncomingCallId(final String callId, Bundle extras) {
        onCreateIncomingConnection(
                new ConnectionRequest(
                        null /* todo getHandle() */,
                        extras),
                new Response<ConnectionRequest, Connection>() {
                    @Override
                    public void onResult(ConnectionRequest request, Connection... result) {
                        if (result.length != 1) {
                            getAdapter().handleFailedOutgoingCall(
                                    callId,
                                    "Created " + result.length + " Connections, expected 1");
                            for (int i = 0; i < result.length; i++) {
                                result[i].abort();
                            }
                        } else {
                            mConnectionById.put(callId, result[0]);
                            getAdapter().notifyIncomingCall(new CallInfo(
                                    callId,
                                    CallState.NEW /* TODO ? */,
                                    null /* TODO handle */));
                        }
                    }

                    @Override
                    public void onError(ConnectionRequest request, String reason) {
                        getAdapter().handleFailedOutgoingCall(callId, reason);
                    }
                }
        );
    }

    @Override
    public final void answer(String callId) {
        findConnectionForAction(callId, "answer").answer();
    }

    @Override
    public final void reject(String callId) {
        findConnectionForAction(callId, "reject").reject();
    }

    @Override
    public final void disconnect(String callId) {
        findConnectionForAction(callId, "disconnect").disconnect();
    }

    @Override
    public final void hold(String callId) {
        findConnectionForAction(callId, "hold").hold();
    }

    @Override
    public final void unhold(String callId) {
        findConnectionForAction(callId, "unhold").unhold();
    }

    @Override
    public final void playDtmfTone(String callId, char digit) {
        findConnectionForAction(callId, "playDtmfTone").playDtmfTone(digit);
    }

    @Override
    public final void stopDtmfTone(String callId) {
        findConnectionForAction(callId, "stopDtmfTone").stopDtmfTone();
    }

    @Override
    public final void onAudioStateChanged(String callId, CallAudioState audioState) {
        findConnectionForAction(callId, "onAudioStateChanged").setAudioState(audioState);
    }

    /**
     * Find a set of Subscriptions matching a given handle (phone number).
     *
     * @param handle A phone number.
     * @param callback A callback for providing the result.
     */
    public void onFindSubscriptions(
            Uri handle,
            Response<Uri, Subscription> callback) {}

    /**
     * Create a Connection given a request.
     *
     * @param request Some data encapsulating details of the desired Connection.
     * @param callback A callback for providing the result.
     */
    public void onCreateConnections(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> callback) {}

    /**
     * Create a Connection to match an incoming connection notification.
     *
     * @param request Some data encapsulating details of the desired Connection.
     * @param callback A callback for providing the result.
     */
    public void onCreateIncomingConnection(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> callback) {}

    private final Connection findConnectionForAction(String callId, String action) {
        if (mConnectionById.containsKey(callId)) {
            return mConnectionById.get(callId);
        }
        Log.wtf(TAG, action + " - Cannot find Connection \"" + callId + "\"");
        return NULL_CONNECTION;
    }
}