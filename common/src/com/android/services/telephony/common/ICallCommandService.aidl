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

/**
 * Service implemented by TelephonyService and used by In-call UI to control
 * phone calls on the device.
 * TODO: Move this out of opt/telephony and into opt/call or similar. This interface
 *       makes sense even without the telephony layer (think VOIP).
 */
oneway interface ICallCommandService {

    /**
     * Answer a ringing call.
     */
    void answerCall(int callId);

    /**
     * Reject a ringing call.
     */
    void rejectCall(int callId);

    /**
     * Disconnect an active call.
     */
    void disconnectCall(int callId);

    /**
     * Place call on hold.
     */
    void hold(int callId, boolean hold);

    /**
     * Mute the phone.
     */
    void mute(boolean onOff);

    /**
     * Turn on or off speaker.
     */
    void speaker(boolean onOff);

}
