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

import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;

import android.telecomm.TelecommManager;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.google.common.base.Preconditions;

/**
 * Listens to incoming-call events from the associated phone object and notifies Telecomm upon each
 * occurence. One instance of these exists for each of the telephony-based call services.
 */
final class PstnIncomingCallNotifier {
    /** New ringing connection event code. */
    private static final int EVENT_NEW_RINGING_CONNECTION = 100;

    /** The phone proxy object to listen to. */
    private final PhoneProxy mPhoneProxy;

    /**
     * The base phone implementation behind phone proxy. The underlying phone implementation can
     * change underneath when the radio technology changes. We listen for these events and update
     * the base phone in this variable. We save it so that when the change happens, we can
     * unregister from the events we were listening to.
     */
    private Phone mPhoneBase;

    /**
     * Used to listen to events from {@link #mPhoneBase}.
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
     * Receiver to listen for radio technology change events.
     */
    private final BroadcastReceiver mRATReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED.equals(action)) {
                String newPhone = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                Log.d(this, "Radio technology switched. Now %s is active.", newPhone);

                registerForNotifications();
            }
        }
    };

    /**
     * Persists the specified parameters and starts listening to phone events.
     *
     * @param phoneProxy The phone object for listening to incoming calls.
     */
    PstnIncomingCallNotifier(PhoneProxy phoneProxy) {
        Preconditions.checkNotNull(phoneProxy);

        mPhoneProxy = phoneProxy;

        registerForNotifications();

        IntentFilter intentFilter =
                new IntentFilter(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        mPhoneProxy.getContext().registerReceiver(mRATReceiver, intentFilter);
    }

    /**
     * Register for notifications from the base phone.
     * TODO(santoscordon): We should only need to interact with the phoneproxy directly. However,
     * since the phoneproxy only interacts directly with CallManager we either listen to callmanager
     * or we have to poke into the proxy like this.  Neither is desirable. It would be better if
     * this class and callManager could register generically with the phone proxy instead and get
     * radio techonology changes directly.  Or better yet, just register for the notifications
     * directly with phone proxy and never worry about the technology changes. This requires a
     * change in opt/telephony code.
     */
    private void registerForNotifications() {
        Phone newPhone = mPhoneProxy.getActivePhone();
        if (newPhone != mPhoneBase) {
            if (mPhoneBase != null) {
                Log.i(this, "Unregistering: %s", mPhoneBase);
                mPhoneBase.unregisterForNewRingingConnection(mHandler);
            }

            if (newPhone != null) {
                Log.i(this, "Registering: %s", newPhone);
                mPhoneBase = newPhone;
                mPhoneBase.registerForNewRingingConnection(
                        mHandler, EVENT_NEW_RINGING_CONNECTION, null);
            }
        }
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
                sendIncomingCallIntent(connection);
            }
        }
    }

    /**
     * Sends the incoming call intent to telecomm.
     */
    private void sendIncomingCallIntent(Connection connection) {
        Context context = mPhoneProxy.getContext();

        Intent intent = new Intent(TelecommManager.ACTION_INCOMING_CALL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(TelecommManager.EXTRA_PHONE_ACCOUNT,
                TelecommAccountRegistry.makePstnPhoneAccount(mPhoneProxy));

        Log.d(this, "Sending incoming call intent: %s", intent);
        context.startActivityAsUser(intent, UserHandle.CURRENT);
    }
}
