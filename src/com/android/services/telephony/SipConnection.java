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

import android.telecomm.Connection;

/**
 * A {@link Connection} object for SIP calls.
 */
public class SipConnection extends TelephonyConnection {

    public SipConnection(com.android.internal.telephony.Connection connection) {
        super(connection);
    }

    // TODO: Fill in the below methods

    /** {@inheritDoc} */
    @Override
    protected void onPlayDtmfTone(char c) {
        super.onPlayDtmfTone(c);
    }

    /** {@inheritDoc} */
    @Override
    protected void onStopDtmfTone() {
        super.onStopDtmfTone();
    }

    /** {@inheritDoc} */
    @Override
    protected void onDisconnect() {
        super.onDisconnect();
    }

    /** {@inheritDoc} */
    @Override
    protected void onAbort() {
        super.onAbort();
    }

    /** {@inheritDoc} */
    @Override
    public void onHold() {
        super.onHold();
    }

    /** {@inheritDoc} */
    @Override
    protected void onUnhold() {
        super.onUnhold();
    }

    /** {@inheritDoc} */
    @Override
    protected void onAnswer() {
        super.onAnswer();
    }

    /** {@inheritDoc} */
    @Override
    protected void onReject() {
        super.onReject();
    }
}
