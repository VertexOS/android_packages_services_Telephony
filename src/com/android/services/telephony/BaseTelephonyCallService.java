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
import android.telecomm.CallAudioState;
import android.telecomm.CallInfo;
import android.telecomm.CallService;
import android.telecomm.CallServiceAdapter;
import android.text.TextUtils;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

/**
 * The parent class for telephony-based call services. Subclasses provide the specific phone (GSM,
 * CDMA, etc...) to use.
 */
public abstract class BaseTelephonyCallService extends CallService {
    /** {@inheritDoc} */
    @Override
    public void abort(String callId) {
        TelephonyCallConnection callConnection = CallRegistrar.get(callId);
        if (callConnection != null) {
            callConnection.disconnect(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect(String callId) {
        TelephonyCallConnection callConnection = CallRegistrar.get(callId);
        if (callConnection != null) {
            callConnection.disconnect(false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void hold(String callId) {
        Log.d(this, "Attempting to put call on hold: %s", callId);
        TelephonyCallConnection callConnection = CallRegistrar.get(callId);
        if (callConnection != null) {
            callConnection.hold();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void unhold(String callId) {
        Log.d(this, "Attempting to release call from hold: %s", callId);
        TelephonyCallConnection callConnection = CallRegistrar.get(callId);
        if (callConnection != null) {
            callConnection.unhold();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onAudioStateChanged(String activeCallId, CallAudioState audioState) {
        TelephonyCallConnection callConnection = CallRegistrar.get(activeCallId);
        if (callConnection != null) {
            callConnection.onAudioStateChanged(audioState);
        }
    }

    /**
     * Initiates the call, should be called by the subclass.
     */
    protected void startCallWithPhone(Phone phone, CallInfo callInfo) {
        String callId = callInfo.getId();
        if (phone == null) {
            getAdapter().handleFailedOutgoingCall(callId, "Phone is null");
            return;
        }

        if (callInfo.getHandle() == null) {
            getAdapter().handleFailedOutgoingCall(callInfo.getId(), "Handle is null");
            return;
        }

        String number = callInfo.getHandle().getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            getAdapter().handleFailedOutgoingCall(callId, "Unable to parse number");
            return;
        }

        Connection connection;
        try {
            connection = phone.dial(number);
        } catch (CallStateException e) {
            Log.e(this, e, "Call to Phone.dial failed with exception");
            getAdapter().handleFailedOutgoingCall(callId, e.getMessage());
            return;
        }

        if (connection == null) {
            getAdapter().handleFailedOutgoingCall(callId, "Call to phone.dial failed");
            return;
        }

        TelephonyCallConnection callConnection =
                new TelephonyCallConnection(getAdapter(), callId, connection);
        CallRegistrar.register(callId, callConnection);

        getAdapter().handleSuccessfulOutgoingCall(callId);
    }
}
