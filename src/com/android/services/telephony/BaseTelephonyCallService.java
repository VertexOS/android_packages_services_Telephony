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
import android.os.RemoteException;
import android.telecomm.CallInfo;
import android.telecomm.CallService;
import android.telecomm.ICallServiceAdapter;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

import java.util.HashMap;

/**
 * The parent class for telephony-based call services. Subclasses provide the specific phone (GSM,
 * CDMA, etc...) to use.
 */
public abstract class BaseTelephonyCallService extends CallService {
    private static final String TAG = "BaseTeleCallService";

    protected ICallServiceAdapter mCallServiceAdapter;

    /** Map of all call connections keyed by the call ID.  */
    private static HashMap<String, TelephonyCallConnection> sCallConnections =
            new HashMap<String, TelephonyCallConnection>();

    /**
     * Clears the connection from the list of connections. Called when a phone call disconnects.
     */
    static void onCallConnectionClosing(TelephonyCallConnection callConnection) {
        sCallConnections.remove(callConnection.getCallId());
    }

    /** {@inheritDoc} */
    @Override
    public void setCallServiceAdapter(ICallServiceAdapter callServiceAdapter) {
        mCallServiceAdapter = callServiceAdapter;
    }

    /** {@inheritDoc} */
    @Override
    public void setIncomingCallId(String callId) {
        // Incoming calls not implemented yet.
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect(String callId) {
        // Maybe null if the connection has already disconnected.
        if (sCallConnections.containsKey(callId)) {
            sCallConnections.get(callId).disconnect();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void answer(String callId) {
    }

    /** {@inheritDoc} */
    @Override
    public void reject(String callId) {
    }

    /**
     * Initiates the call, should be called by the subclass.
     */
    protected void startCallWithPhone(Phone phone, CallInfo callInfo) {
        try {
            if (phone == null) {
                mCallServiceAdapter.handleFailedOutgoingCall(callInfo.getId(), "Phone is null");
                return;
            }

            Uri uri = Uri.parse(callInfo.getHandle());
            String number = null;
            if (uri != null) {
                number = uri.getSchemeSpecificPart();
            }
            if (TextUtils.isEmpty(number)) {
                mCallServiceAdapter.handleFailedOutgoingCall(callInfo.getId(),
                        "Unable to parse number");
                return;
            }

            Connection connection;
            try {
                connection = phone.dial(number);
            } catch (CallStateException e) {
                Log.e(TAG, "Call to Phone.dial failed with exception", e);
                mCallServiceAdapter.handleFailedOutgoingCall(callInfo.getId(), e.getMessage());
                return;
            }

            TelephonyCallConnection callConnection =
                    new TelephonyCallConnection(mCallServiceAdapter, callInfo.getId(), connection);
            sCallConnections.put(callInfo.getId(), callConnection);
            mCallServiceAdapter.handleSuccessfulOutgoingCall(callInfo.getId());
        } catch (RemoteException e) {
            Log.e(TAG, "Got RemoteException", e);
        }
    }
}
