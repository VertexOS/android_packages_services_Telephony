/**
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

package com.android.phone;

import com.android.internal.telephony.CallManager;
import com.android.services.telephony.common.ICallCommandService;

/**
 * Service interface used by in-call ui to control phone calls using commands
 * exposed as methods.  Instances of this class are handed to in-call UI via
 * CallMonitorService.
 */
class CallCommandService extends ICallCommandService.Stub {

    private CallManager mCallManager;

    public CallCommandService(CallManager callManager) {
        mCallManager = callManager;
    }

    /**
     * TODO(klp): Add a confirmation callback parameter.
     */
    @Override
    public void answerCall(int callId) {
        // TODO(klp): Change to using the callId and logic from InCallScreen::internalAnswerCall
        PhoneUtils.answerCall(mCallManager.getFirstActiveRingingCall());
    }

    /**
     * TODO(klp): Add a confirmation callback parameter.
     */
    @Override
    public void rejectCall(int callId) {
        // TODO(klp): Change to using the callId
        PhoneUtils.hangupRingingCall(mCallManager.getFirstActiveRingingCall());
    }

    @Override
    public void disconnectCall(int callId) {
        // TODO(klp): Change to using the callId
        PhoneUtils.hangup(mCallManager);
    }
}
