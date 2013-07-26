/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.services.telephony.common;

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallCommandService;

/**
 * Service implemented by clients that would like to control and know the status
 * of phone calls on the device.
 * TODO: Rename interface.  This not only monitors but controls calls, too. Come
 *       up with a name that doesn't conflict with current CallManager and
 *       CallController classes.
 * TODO: Move this out of opt/telephony and into opt/call or similar. This interface
 *       makes sense even without the telephony layer (think VOIP).
 */
oneway interface ICallHandlerService {

    /**
     * Hands a command interface to the CallMonitorService through which
     * the call monitor can control the phone calls.
     */
    void setCallCommandService(ICallCommandService callCommandService);

    /**
     * Called when a new incoming call comes in.
     */
    void onIncomingCall(in Call call);

    /**
     * Called when the state of a call changes.
     * TODO(klp): Should this replace onIncomingCall and onDisconnect?
     * TODO(klp): Should this take in a Collection of calls to update in bulk.
     */
    void onCallUpdate(in Call call);

    /**
     * Called when a call disconnects.
     */
    void onDisconnect(in Call call);
}
