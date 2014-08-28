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

import android.telecomm.PhoneCapabilities;

import com.android.internal.telephony.Connection;

/**
 * Manages a single phone call handled by CDMA.
 */
final class CdmaConnection extends TelephonyConnection {

    /**
     * {@code True} if the CDMA connection should allow mute.
     */
    private final boolean mAllowMute;

    CdmaConnection(Connection connection, boolean allowMute) {
        super(connection);
        mAllowMute = allowMute;
    }

    /** {@inheritDoc} */
    @Override
    public void onPlayDtmfTone(char digit) {
        // TODO: There are conditions where we should play dtmf tones with different
        // timeouts.
        // TODO: We get explicit response from the phone via a Message when the burst
        // tone has completed. During this time we can get subsequent requests. We need to stop
        // passing in null as the message and start handling it to implement a queue.
        if (getPhone() != null) {
            getPhone().sendBurstDtmf(Character.toString(digit), 0, 0, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onStopDtmfTone() {
        // no-op, we only play timed dtmf tones for cdma.
    }

    @Override
    protected int buildCallCapabilities() {
        int capabilities = 0;
        if (mAllowMute) {
            capabilities = PhoneCapabilities.MUTE;
        }
        return capabilities;
    }
}
