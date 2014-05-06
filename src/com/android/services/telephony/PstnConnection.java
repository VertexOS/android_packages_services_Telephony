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

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

/**
 * Manages a single phone call handled by the PSTN infrastructure.
 */
public abstract class PstnConnection extends TelephonyConnection {

    private final Phone mPhone;

    public PstnConnection(Phone phone, Connection connection) {
        super(connection);
        mPhone = phone;
    }

    /** {@inheritDoc} */
    @Override
    protected void onAnswer() {
        // TODO(santoscordon): Tons of hairy logic is missing here around multiple active calls on
        // CDMA devices. See {@link CallManager.acceptCall}.

        Log.i(this, "Answer call.");
        if (isValidRingingCall(getOriginalConnection())) {
            try {
                mPhone.acceptCall();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call.");
            }
        }
        super.onAnswer();
    }

    /** {@inheritDoc} */
    @Override
    protected void onReject() {
        Log.i(this, "Reject call.");
        if (isValidRingingCall(getOriginalConnection())) {
            try {
                mPhone.rejectCall();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to reject call.");
            }
        }
        super.onReject();
    }

    protected Phone getPhone() {
        return mPhone;
    }

    /**
     * Checks to see if the specified low-level Telephony {@link Connection} corresponds to an
     * active incoming call. Returns false if there is no such actual call, or if the
     * associated call is not incoming (See {@link Call.State#isRinging}).
     *
     * @param connection The connection to ask about.
     */
    private boolean isValidRingingCall(Connection connection) {
        Call ringingCall = mPhone.getRingingCall();

        if (ringingCall.getState().isRinging()) {
            // The ringingCall object is always not-null so we have to check its current state.
            if (ringingCall.getEarliestConnection() == connection) {
                // The ringing connection is the same one for this call. We have a match!
                return true;
            } else {
                Log.w(this, "A ringing connection exists, but it is not the same connection.");
            }
        } else {
            Log.i(this, "There is no longer a ringing call.");
        }

        return false;
    }
}
