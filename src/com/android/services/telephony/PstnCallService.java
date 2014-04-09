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

import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.phone.Constants;
import com.google.android.collect.Sets;

import java.util.Set;

/**
 * The parent class for PSTN-based call services. Handles shared functionality between all PSTN
 * call services.
 */
public abstract class PstnCallService extends BaseTelephonyCallService {
    private EmergencyCallHelper mEmergencyCallHelper;
    private Set<String> mPendingOutgoingEmergencyCalls = Sets.newHashSet();

    @Override
    public void onCreate() {
        super.onCreate();
        mEmergencyCallHelper = new EmergencyCallHelper(this);
    }

    /** {@inheritDoc} */
    @Override
    public final void call(final CallInfo callInfo) {
        // TODO: Consider passing call emergency information as part of CallInfo so that we dont
        // have to make the check here once again.
        String handle = callInfo.getHandle().getSchemeSpecificPart();
        final Phone phone = getPhone();
        if (PhoneNumberUtils.isPotentialEmergencyNumber(handle)) {
            final String callId = callInfo.getId();

            EmergencyCallHelper.Callback callback = new EmergencyCallHelper.Callback() {
                @Override
                public void onComplete(boolean isRadioReady) {
                    if (mPendingOutgoingEmergencyCalls.remove(callId)) {
                        // The emergency call was still pending (not aborted) so continue with the
                        // rest of the logic.

                        if (isRadioReady) {
                            startCallWithPhone(phone, callInfo);
                        } else {
                            getAdapter().handleFailedOutgoingCall(
                                    callInfo.getId(), "Failed to turn on radio.");
                        }
                    }
                }
            };

            mPendingOutgoingEmergencyCalls.add(callId);

            // If the radio is already on, this will call us back fairly quickly.
            mEmergencyCallHelper.startTurnOnRadioSequence(phone, callback);
        } else {
            startCallWithPhone(phone, callInfo);
        }
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
                        new TelephonyCallConnection(getAdapter(), callId, connection);
                CallRegistrar.register(callId, callConnection);

                // Address can be null for blocked calls.
                String address = connection.getAddress();
                if (address == null) {
                    address = "";
                }

                // Notify Telecomm of the incoming call.
                Uri handle = Uri.fromParts(Constants.SCHEME_TEL, address, null);
                CallInfo callInfo = new CallInfo(callId, CallState.RINGING, handle);
                getAdapter().notifyIncomingCall(callInfo);
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

    /** {@inheritDoc} */
    @Override
    public void abort(String callId) {
        mPendingOutgoingEmergencyCalls.remove(callId);
        super.abort(callId);
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
