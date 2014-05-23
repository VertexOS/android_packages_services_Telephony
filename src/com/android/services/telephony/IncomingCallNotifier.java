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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.telecomm.CallService;
import android.telecomm.CallServiceDescriptor;
import android.telecomm.TelecommConstants;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

/**
 * Listens to incoming-call events from the associated phone object and notifies Telecomm upon each
 * occurence. One instance of these exists for each of the telephony-based call services.
 */
final class IncomingCallNotifier {
    /** New ringing connection event code. */
    private static final int EVENT_NEW_RINGING_CONNECTION = 100;

    /** The phone object to listen to. */
    private final Phone mPhone;

    /** The class for the associated call service. */
    private final Class<? extends CallService> mCallServiceClass;

    /**
     * Used to listen to events from {@link #mPhone}.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case EVENT_NEW_RINGING_CONNECTION:
                    handleNewRingingConnection((AsyncResult) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Persists the specified parameters and starts listening to phone events.
     *
     * @param callServiceClass The call service class.
     * @param phone The phone object for listening to incoming calls.
     */
    IncomingCallNotifier(Class<? extends CallService> callServiceClass, Phone phone) {
        Preconditions.checkNotNull(callServiceClass);
        Preconditions.checkNotNull(phone);

        mCallServiceClass = callServiceClass;
        mPhone = phone;

        mPhone.registerForNewRingingConnection(mHandler, EVENT_NEW_RINGING_CONNECTION, null);
    }

    /**
     * Verifies the incoming call and triggers sending the incoming-call intent to Telecomm.
     *
     * @param asyncResult The result object from the new ringing event.
     */
    private void handleNewRingingConnection(AsyncResult asyncResult) {
        Log.d(this, "handleNewRingingConnection");
        Connection connection = (Connection) asyncResult.result;
        if (connection != null) {
            Call call = connection.getCall();

            // Final verification of the ringing state before sending the intent to Telecomm.
            if (call != null && call.getState().isRinging()) {
                sendIncomingCallIntent();
            }
        }
    }

    /**
     * Sends the incoming call intent to telecomm.
     */
    private void sendIncomingCallIntent() {
        Context context = mPhone.getContext();

        CallServiceDescriptor.Builder builder = CallServiceDescriptor.newBuilder(context);
        builder.setCallService(mCallServiceClass);
        builder.setNetworkType(CallServiceDescriptor.FLAG_PSTN);

        Intent intent = new Intent(TelecommConstants.ACTION_INCOMING_CALL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(TelecommConstants.EXTRA_CALL_SERVICE_DESCRIPTOR, builder.build());

        Log.d(this, "Sending incoming call intent: %s", intent);
        context.startActivityAsUser(intent, UserHandle.CURRENT);
    }
}
