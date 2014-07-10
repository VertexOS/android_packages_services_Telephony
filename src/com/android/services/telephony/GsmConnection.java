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
import com.android.internal.telephony.Phone;

/**
 * Manages a single phone call handled by GSM.
 */
public class GsmConnection extends PstnConnection {
    private boolean mIsConferenceCapable;

    public GsmConnection(Phone phone, Connection connection) {
        super(phone, connection);
    }

    /** {@inheritDoc} */
    @Override
    public void onPlayDtmfTone(char digit) {
        getPhone().startDtmf(digit);
        super.onPlayDtmfTone(digit);
    }

    /** {@inheritDoc} */
    @Override
    public void onStopDtmfTone() {
        getPhone().stopDtmf();
        super.onStopDtmfTone();
    }

    void setIsConferenceCapable(boolean isConferenceCapable) {
        if (mIsConferenceCapable != isConferenceCapable) {
            mIsConferenceCapable = isConferenceCapable;
            updateCallCapabilities();
        }
    }

    public void performConference() {
        try {
            Log.d(this, "conference - %s", this);
            getPhone().conference();
        } catch (CallStateException e) {
            Log.e(this, e, "Failed to conference call.");
        }
    }

    @Override
    protected int buildCallCapabilities() {
        int capabilities = CallCapabilities.MUTE | CallCapabilities.SUPPORT_HOLD;
        if (getState() == State.ACTIVE || getSate() == State.HOLDING) {
            capabilities |= CallCapabilities.HOLD;
        }
        if (mIsConferenceCapable) {
            capabilities |= CallCapabilities.MERGE_CALLS;
        }
        return capabilities;
    }
}
