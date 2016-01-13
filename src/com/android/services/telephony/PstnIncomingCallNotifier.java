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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.text.TextUtils;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.phone.PhoneUtils;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Listens to incoming-call events from the associated phone object and notifies Telecom upon each
 * occurence. One instance of these exists for each of the telephony-based call services.
 */
final class PstnIncomingCallNotifier {
    /** New ringing connection event code. */
    private static final int EVENT_NEW_RINGING_CONNECTION = 100;
    private static final int EVENT_CDMA_CALL_WAITING = 101;
    private static final int EVENT_UNKNOWN_CONNECTION = 102;

    /** The phone object to listen to. */
    private final Phone mPhone;

    /**
     * Used to listen to events from {@link #mPhone}.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case EVENT_NEW_RINGING_CONNECTION:
                    handleNewRingingConnection((AsyncResult) msg.obj);
                    break;
                case EVENT_CDMA_CALL_WAITING:
                    handleCdmaCallWaiting((AsyncResult) msg.obj);
                    break;
                case EVENT_UNKNOWN_CONNECTION:
                    handleNewUnknownConnection((AsyncResult) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Persists the specified parameters and starts listening to phone events.
     *
     * @param phone The phone object for listening to incoming calls.
     */
    PstnIncomingCallNotifier(Phone phone) {
        Preconditions.checkNotNull(phone);

        mPhone = phone;

        registerForNotifications();
    }

    void teardown() {
        unregisterForNotifications();
    }

    /**
     * Register for notifications from the base phone.
     */
    private void registerForNotifications() {
        if (mPhone != null) {
            Log.i(this, "Registering: %s", mPhone);
            mPhone.registerForNewRingingConnection(mHandler, EVENT_NEW_RINGING_CONNECTION, null);
            mPhone.registerForCallWaiting(mHandler, EVENT_CDMA_CALL_WAITING, null);
            mPhone.registerForUnknownConnection(mHandler, EVENT_UNKNOWN_CONNECTION, null);
        }
    }

    private void unregisterForNotifications() {
        if (mPhone != null) {
            Log.i(this, "Unregistering: %s", mPhone);
            mPhone.unregisterForNewRingingConnection(mHandler);
            mPhone.unregisterForCallWaiting(mHandler);
            mPhone.unregisterForUnknownConnection(mHandler);
        }
    }

    /**
     * Verifies the incoming call and triggers sending the incoming-call intent to Telecom.
     *
     * @param asyncResult The result object from the new ringing event.
     */
    private void handleNewRingingConnection(AsyncResult asyncResult) {
        Log.d(this, "handleNewRingingConnection");
        Connection connection = (Connection) asyncResult.result;
        if (connection != null) {
            Call call = connection.getCall();

            // Final verification of the ringing state before sending the intent to Telecom.
            if (call != null && call.getState().isRinging()) {
                sendIncomingCallIntent(connection);
            }
        }
    }

    private void handleCdmaCallWaiting(AsyncResult asyncResult) {
        Log.d(this, "handleCdmaCallWaiting");
        CdmaCallWaitingNotification ccwi = (CdmaCallWaitingNotification) asyncResult.result;
        Call call = mPhone.getRingingCall();
        if (call.getState() == Call.State.WAITING) {
            Connection connection = call.getLatestConnection();
            if (connection != null) {
                String number = connection.getAddress();
                if (number != null && Objects.equals(number, ccwi.number)) {
                    sendIncomingCallIntent(connection);
                }
            }
        }
    }

    private void handleNewUnknownConnection(AsyncResult asyncResult) {
        Log.i(this, "handleNewUnknownConnection");
        if (!(asyncResult.result instanceof Connection)) {
            Log.w(this, "handleNewUnknownConnection called with non-Connection object");
            return;
        }
        Connection connection = (Connection) asyncResult.result;
        if (connection != null) {
            // Because there is a handler between telephony and here, it causes this action to be
            // asynchronous which means that the call can switch to DISCONNECTED by the time it gets
            // to this code. Check here to ensure we are not adding a disconnected or IDLE call.
            Call.State state = connection.getState();
            if (state == Call.State.DISCONNECTED || state == Call.State.IDLE) {
                Log.i(this, "Skipping new unknown connection because it is idle. " + connection);
                return;
            }

            Call call = connection.getCall();
            if (call != null && call.getState().isAlive()) {
                addNewUnknownCall(connection);
            }
        }
    }

    private void addNewUnknownCall(Connection connection) {
        Log.i(this, "addNewUnknownCall, connection is: %s", connection);

        if (!maybeSwapAnyWithUnknownConnection(connection)) {
            Log.i(this, "determined new connection is: %s", connection);
            Bundle extras = null;
            if (connection.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED &&
                    !TextUtils.isEmpty(connection.getAddress())) {
                extras = new Bundle();
                Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, connection.getAddress(), null);
                extras.putParcelable(TelecomManager.EXTRA_UNKNOWN_CALL_HANDLE, uri);
            }
            TelecomManager.from(mPhone.getContext()).addNewUnknownCall(
                    PhoneUtils.makePstnPhoneAccountHandle(mPhone), extras);
        } else {
            Log.i(this, "swapped an old connection, new one is: %s", connection);
        }
    }

    /**
     * Sends the incoming call intent to telecom.
     */
    private void sendIncomingCallIntent(Connection connection) {
        Bundle extras = null;
        if (connection.getNumberPresentation() == TelecomManager.PRESENTATION_ALLOWED &&
                !TextUtils.isEmpty(connection.getAddress())) {
            extras = new Bundle();
            Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, connection.getAddress(), null);
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        }
        TelecomManager.from(mPhone.getContext()).addNewIncomingCall(
                PhoneUtils.makePstnPhoneAccountHandle(mPhone), extras);
    }

    /**
     * Define cait.Connection := com.android.internal.telephony.Connection
     *
     * Given a previously unknown cait.Connection, check to see if it's likely a replacement for
     * another cait.Connnection we already know about. If it is, then we silently swap it out
     * underneath within the relevant {@link TelephonyConnection}, using
     * {@link TelephonyConnection#setOriginalConnection(Connection)}, and return {@code true}.
     * Otherwise, we return {@code false}.
     */
    private boolean maybeSwapAnyWithUnknownConnection(Connection unknown) {
        if (!unknown.isIncoming()) {
            TelecomAccountRegistry registry = TelecomAccountRegistry.getInstance(null);
            if (registry != null) {
                TelephonyConnectionService service = registry.getTelephonyConnectionService();
                if (service != null) {
                    for (android.telecom.Connection telephonyConnection : service
                            .getAllConnections()) {
                        if (telephonyConnection instanceof TelephonyConnection) {
                            if (maybeSwapWithUnknownConnection(
                                    (TelephonyConnection) telephonyConnection,
                                    unknown)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean maybeSwapWithUnknownConnection(
            TelephonyConnection telephonyConnection,
            Connection unknown) {
        Connection original = telephonyConnection.getOriginalConnection();
        if (original != null && !original.isIncoming()
                && Objects.equals(original.getAddress(), unknown.getAddress())) {
            telephonyConnection.setOriginalConnection(unknown);
            return true;
        }
        return false;
    }
}
