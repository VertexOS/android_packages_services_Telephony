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
 * limitations under the License
 */

package com.android.phone;

import android.content.Context;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.services.telephony.common.ICallCommandService;

/**
 * Service interface used by in-call ui to control phone calls using commands exposed as methods.
 * Instances of this class are handed to in-call UI via CallMonitorService.
 */
class CallCommandService extends ICallCommandService.Stub {

    private static final String TAG = CallCommandService.class.getSimpleName();

    private Context mContext;
    private CallManager mCallManager;

    public CallCommandService(Context context, CallManager callManager) {
        mContext = context;
        mCallManager = callManager;
    }

    /**
     * TODO(klp): Add a confirmation callback parameter.
     */
    @Override
    public void answerCall(int callId) {
        try {
            // TODO(klp): Change to using the callId and logic from InCallScreen::internalAnswerCall
            PhoneUtils.answerCall(mCallManager.getFirstActiveRingingCall());
        } catch (Exception e) {
            Log.e(TAG, "Error during answerCall().", e);
        }
    }

    /**
     * TODO(klp): Add a confirmation callback parameter.
     */
    @Override
    public void rejectCall(int callId) {
        try {
            // TODO(klp): Change to using the callId
            PhoneUtils.hangupRingingCall(mCallManager.getFirstActiveRingingCall());
        } catch (Exception e) {
            Log.e(TAG, "Error during rejectCall().", e);
        }
    }

    @Override
    public void disconnectCall(int callId) {
        try {
            // TODO(klp): Change to using the callId
            PhoneUtils.hangup(mCallManager);
        } catch (Exception e) {
            Log.e(TAG, "Error during disconnectCall().", e);
        }
    }

    @Override
    public void mute(boolean onOff) {
        try {
            PhoneUtils.setMute(onOff);
        } catch (Exception e) {
            Log.e(TAG, "Error during mute().", e);
        }
    }

    @Override
    public void speaker(boolean onOff) {
        try {
            // TODO(klp): add bluetooth logic from InCallScreen.toggleSpeaker()
            PhoneUtils.turnOnSpeaker(mContext, onOff, true);
        } catch (Exception e) {
            Log.e(TAG, "Error during speaker().", e);
        }
    }
}
