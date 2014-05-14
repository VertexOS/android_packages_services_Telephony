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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.android.internal.telephony.Connection;

import java.util.Collection;
import java.util.HashMap;

/**
 * Maintains global associations between a Telecomm-supplied call ID and a
 * {@link TelephonyCallConnection} object.
 */
final class CallRegistrar {
    private static final String TAG = CallRegistrar.class.getSimpleName();

    /** Map of all call connections keyed by the call ID.  */
    private static HashMap<String, TelephonyCallConnection> sCallConnections = Maps.newHashMap();

    /**
     * Registers the specified call ID with the specified call connection.
     *
     * @param callId The call ID from Telecomm.
     * @param callConnection The call connection.
     */
    static void register(String callId, TelephonyCallConnection callConnection) {
        Preconditions.checkNotNull(callId);
        Preconditions.checkNotNull(callConnection);

        if (sCallConnections.containsKey(callId)) {
            Log.wtf(TAG, "Reregistering the call: %s", callId);
        } else {
            sCallConnections.put(callId, callConnection);
        }
    }

    /**
     * Unregisters the specified call ID.
     *
     * @param callId The call ID from Telecomm.
     */
    static void unregister(String callId) {
        Preconditions.checkNotNull(callId);
        sCallConnections.remove(callId);
    }

    /**
     * Returns true if the specified connection has already been registered with a call ID.
     *
     * @param connection The connection to test.
     */
    static boolean isConnectionRegistered(Connection connection) {
        for (TelephonyCallConnection callConnection : CallRegistrar.getCallConnections()) {
            if (callConnection.getOriginalConnection() == connection) {
                return true;
            }
        }
        return false;
    }

    static TelephonyCallConnection get(String callId) {
        return sCallConnections.get(callId);
    }

    static boolean isEmpty() {
        return sCallConnections.isEmpty();
    }

    /**
     * Returns all the registered call connections as a collection.
     */
    static Collection<TelephonyCallConnection> getCallConnections() {
        return sCallConnections.values();
    }
}
