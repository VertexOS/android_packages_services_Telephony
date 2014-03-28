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
import android.os.Bundle;
import android.telecomm.CallInfo;
import android.telecomm.CallState;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.phone.Constants;

/**
 * The parent class for PSTN-based call services. Handles shared functionality between all PSTN
 * call services.
 */
public abstract class PstnCallService extends BaseTelephonyCallService {
    private static final String TAG = PstnCallService.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    /** {@inheritDoc} */
    @Override
    public final void call(CallInfo callInfo) {
        startCallWithPhone(getPhone(), callInfo);
    }

    /**
     * Looks for a new incoming call and if one is found, tells Telecomm to associate the incoming
     * call with the specified call ID.
     *
     * {@inheritDoc}
     */
    @Override
    public final void setIncomingCallId(String callId, Bundle extras) {
        if (DBG) {
            Log.d(TAG, "setIncomingCallId: " + callId);
        }
        Phone phone = getPhone();
        Call call = getPhone().getRingingCall();

        // The ringing call is always not-null, check if it is truly ringing by checking its state.
        if (call.getState().isRinging()) {
            Connection connection = call.getEarliestConnection();

            if (CallRegistrar.isConnectionRegistered(connection)) {
                Log.e(TAG, "Cannot set incoming call ID, ringing connection already registered.");
            } else {
                // Create and register a new call connection.
                TelephonyCallConnection callConnection =
                        new TelephonyCallConnection(mCallServiceAdapter, callId, connection);
                CallRegistrar.register(callId, callConnection);

                // Address can be null for blocked calls.
                String address = connection.getAddress();
                if (address == null) {
                    address = "";
                }

                // Notify Telecomm of the incoming call.
                Uri handle = Uri.fromParts(Constants.SCHEME_TEL, address, null);
                CallInfo callInfo = new CallInfo(callId, CallState.RINGING, handle);
                mCallServiceAdapter.notifyIncomingCall(callInfo);
            }
        } else {
            Log.e(TAG, "Found no ringing call, call state: " + call.getState());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void answer(String callId) {
        // TODO(santoscordon): Tons of hairy logic is missing here around multiple active calls on
        // CDMA devices. See {@link CallManager.acceptCall}.

        Log.i(TAG, "answer: " + callId);
        if (isValidRingingCall(callId)) {
            try {
                getPhone().acceptCall();
            } catch (CallStateException e) {
                Log.e(TAG, "Failed to accept call " + callId, e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reject(String callId) {
        Log.i(TAG, "reject: " + callId);
        if (isValidRingingCall(callId)) {
            try {
                getPhone().rejectCall();
            } catch (CallStateException e) {
                Log.e(TAG, "Failed to reject call " + callId, e);
            }
        }
    }

    /**
     * @return The current phone object behind this call service.
     */
    protected abstract Phone getPhone();

    /**
     * Checks to see if the specified call ID corresponds to an active incoming call. Returns false
     * if there is no association between the specified call ID and an actual call, or if the
     * associated call is not incoming (See {@link Call.State#isRinging}).
     *
     * @param callId The ID of the call.
     */
    private boolean isValidRingingCall(String callId) {
        TelephonyCallConnection callConnection = CallRegistrar.get(callId);

        if (callConnection == null) {
            if (DBG) {
                Log.d(TAG, "Unknown call ID while testing for a ringing call.");
            }
        } else {
            Phone phone = getPhone();
            Call ringingCall = phone.getRingingCall();

            // The ringingCall object is always not-null so we have to check its current state.
            if (ringingCall.getState().isRinging()) {
                Connection connection = callConnection.getOriginalConnection();
                if (ringingCall.getEarliestConnection() == connection) {
                    // The ringing connection is the same one for this call. We have a match!
                    return true;
                } else {
                    Log.w(TAG, "A ringing connection exists, but it is not the same connection.");
                }
            } else {
                Log.i(TAG, "There is no longer a ringing call.");
            }
        }

        return false;
    }
}
