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

import android.telecomm.CallCapabilities;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;

/**
 * Manages a single phone call handled by GSM.
 */
final class GsmConnection extends TelephonyConnection {
    private boolean mIsConferenceCapable;

    GsmConnection(Connection connection) {
        super(connection);
        GsmConferenceController.add(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPlayDtmfTone(char digit) {
        if (getPhone() != null) {
            getPhone().startDtmf(digit);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onStopDtmfTone() {
        if (getPhone() != null) {
            getPhone().stopDtmf();
        }
    }

    void setIsConferenceCapable(boolean isConferenceCapable) {
        if (mIsConferenceCapable != isConferenceCapable) {
            mIsConferenceCapable = isConferenceCapable;
            updateCallCapabilities(false);
        }
    }

    void performConference() {
        Log.d(this, "performConference - %s", this);
        if (getPhone() != null) {
            try {
                getPhone().conference();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to conference call.");
            }
        }
    }

    @Override
    protected int buildCallCapabilities() {
        int capabilities = CallCapabilities.MUTE | CallCapabilities.SUPPORT_HOLD;
        if (getState() == State.ACTIVE || getState() == State.HOLDING) {
            capabilities |= CallCapabilities.HOLD;
        }
        if (mIsConferenceCapable) {
            capabilities |= CallCapabilities.MERGE_CALLS;
        }
        return capabilities;
    }

    @Override
    void onRemovedFromCallService() {
        super.onRemovedFromCallService();
        GsmConferenceController.remove(this);
    }
}
