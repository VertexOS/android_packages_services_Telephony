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

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Helper class that implements special behavior related to emergency calls. Specifically, this
 * class handles the case of the user trying to dial an emergency number while the radio is off
 * (i.e. the device is in airplane mode), by forcibly turning the radio back on, waiting for it to
 * come up, and then retrying the emergency call.
 */
public class EmergencyCallHelper implements EmergencyCallStateListener.Callback {

    private final Context mContext;
    private EmergencyCallStateListener.Callback mCallback;
    private List<EmergencyCallStateListener> mListeners;
    private List<EmergencyCallStateListener> mInProgressListeners;
    private boolean mIsEmergencyCallingEnabled;


    public EmergencyCallHelper(Context context) {
        mContext = context;
        mInProgressListeners = new ArrayList<>(2);
    }

    private void setupListeners() {
        if (mListeners != null) {
            return;
        }
        mListeners = new ArrayList<>(2);
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            mListeners.add(new EmergencyCallStateListener());
        }
    }
    /**
     * Starts the "turn on radio" sequence. This is the (single) external API of the
     * EmergencyCallHelper class.
     *
     * This method kicks off the following sequence:
     * - Power on the radio for each Phone
     * - Listen for the service state change event telling us the radio has come up.
     * - Retry if we've gone a significant amount of time without any response from the radio.
     * - Finally, clean up any leftover state.
     *
     * This method is safe to call from any thread, since it simply posts a message to the
     * EmergencyCallHelper's handler (thus ensuring that the rest of the sequence is entirely
     * serialized, and runs on the main looper.)
     */
    public void enableEmergencyCalling(EmergencyCallStateListener.Callback callback) {
        setupListeners();
        mCallback = callback;
        mInProgressListeners.clear();
        mIsEmergencyCallingEnabled = false;
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone == null)
                continue;

            mInProgressListeners.add(mListeners.get(i));
            mListeners.get(i).waitForRadioOn(phone, this);
        }

        powerOnRadio();
    }
    /**
     * Attempt to power on the radio (i.e. take the device out of airplane mode). We'll eventually
     * get an onServiceStateChanged() callback when the radio successfully comes up.
     */
    private void powerOnRadio() {
        Log.d(this, "powerOnRadio().");

        // If airplane mode is on, we turn it off the same way that the Settings activity turns it
        // off.
        if (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) > 0) {
            Log.d(this, "==> Turning off airplane mode.");

            // Change the system setting
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);

            // Post the broadcast intend for change in airplane mode
            // TODO: We really should not be in charge of sending this broadcast.
            //     If changing the setting is sufficent to trigger all of the rest of the logic,
            //     then that should also trigger the broadcast intent.
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", false);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    /**
     * This method is called from multiple Listeners on the Main Looper.
     * Synchronization is not necessary.
     */
    @Override
    public void onComplete(EmergencyCallStateListener listener, boolean isRadioReady) {
        mIsEmergencyCallingEnabled |= isRadioReady;
        mInProgressListeners.remove(listener);
        if (mCallback != null && mInProgressListeners.isEmpty()) {
            mCallback.onComplete(null, mIsEmergencyCallingEnabled);
        }
    }
}
