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
import android.telephony.DisconnectCause;

import com.android.internal.telephony.CallStateException;

import java.util.List;

/**
 * Manages state for a conference call.
 */
class ConferenceConnection extends Connection {
    @Override
    protected void onChildrenChanged(List<Connection> children) {
        if (children.isEmpty()) {
            setDisconnected(DisconnectCause.LOCAL, "conference call disconnected.");
            setDestroyed();
        }
    }

    /** ${inheritDoc} */
    @Override
    protected void onDisconnect() {
        // For conference-level disconnects, we need to make sure we disconnect the entire call,
        // not just one of the connections. To do this, we go through the children and get a
        // reference to the telephony-Call object and call hangup().
        for (Connection connection : getChildConnections()) {
            if (connection instanceof TelephonyConnection) {
                com.android.internal.telephony.Connection origConnection =
                        ((TelephonyConnection) connection).getOriginalConnection();
                if (origConnection != null && origConnection.getCall() != null) {
                    try {
                        // getCall() returns what is the parent call of all conferenced conections
                        // so we only need to call hangup on the main call object. Break once we've
                        // done that.
                        origConnection.getCall().hangup();
                        break;
                    } catch (CallStateException e) {
                        Log.e(this, e, "Call state exception in conference hangup.");
                    }
                }
            }
        }
    }

    /** ${inheritDoc} */
    @Override
    protected void onHold() {
        for (Connection connection : getChildConnections()) {
            if (connection instanceof TelephonyConnection) {
                ((TelephonyConnection) connection).onHold();
                // Hold only needs to be called on one of the children.
                break;
            }
        }
    }
}
