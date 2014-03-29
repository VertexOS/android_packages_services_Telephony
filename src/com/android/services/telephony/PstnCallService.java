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
        Log.d(this, "setIncomingCallId: %s", callId);
        Phone phone = getPhone();
        Call call = getPhone().getRingingCall();

        // The ringing call is always not-null, check if it is truly ringing by checking its state.
        if (call.getState().isRinging()) {
            Connection connection = call.getEarliestConnection();

            if (CallRegistrar.isConnectionRegistered(connection)) {
                Log.w(this, "Cannot set incoming call ID, ringing connection already registered.");
            } else {
                // Create and register a new call connection.
                TelephonyCallConnection callConnection =
                        new TelephonyCallConnection(mCallServiceAdapter, callId, connection);
                CallRegistrar.register(callId, callConnection);

                // Notify Telecomm of the incoming call.
                Uri handle = Uri.fromParts(Constants.SCHEME_TEL, connection.getAddress(), null);
                CallInfo callInfo = new CallInfo(callId, CallState.RINGING, handle);
                mCallServiceAdapter.notifyIncomingCall(callInfo);
            }
        } else {
            Log.w(this, "Found no ringing call, call state: %s", call.getState());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void answer(String callId) {
        // TODO(santoscordon): Tons of hairy logic is missing here around multiple active calls on
        // CDMA devices. See {@link CallManager.acceptCall}.

        Log.i(this, "answer: %s", callId);
        if (isValidRingingCall(callId)) {
            try {
                getPhone().acceptCall();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call: %s", callId);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reject(String callId) {
        Log.i(this, "reject: %s", callId);
        if (isValidRingingCall(callId)) {
            try {
                getPhone().rejectCall();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to reject call: %s", callId);
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
            Log.d(this, "Unknown call ID while testing for a ringing call.");
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
                    Log.w(this, "A ringing connection exists, but it is not the same connection.");
                }
            } else {
                Log.i(this, "There is no longer a ringing call.");
            }
        }

        return false;
    }
}
