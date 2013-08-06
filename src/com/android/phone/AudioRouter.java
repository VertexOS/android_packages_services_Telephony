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

package com.android.phone;

import com.google.common.collect.Lists;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemProperties;
import android.util.Log;

import com.android.phone.BluetoothManager.BluetoothIndicatorListener;
import com.android.services.telephony.common.AudioMode;

import java.util.List;

/**
 * Responsible for Routing in-call audio and maintaining routing state.
 */
/* package */ class AudioRouter implements BluetoothIndicatorListener {

    private static String LOG_TAG = AudioRouter.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private final Context mContext;
    private final BluetoothManager mBluetoothManager;
    private final List<AudioModeListener> mListeners = Lists.newArrayList();
    private int mAudioMode = AudioMode.EARPIECE;
    private int mPreviousMode = AudioMode.EARPIECE;

    public AudioRouter(Context context, BluetoothManager bluetoothManager) {
        mContext = context;
        mBluetoothManager = bluetoothManager;

        init();
    }

    /**
     * Return the current audio mode.
     */
    public int getAudioMode() {
        return mAudioMode;
    }

    /**
     * Add a listener to audio mode changes.
     */
    public void addAudioModeListener(AudioModeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove  listener.
     */
    public void removeAudioModeListener(AudioModeListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    /**
     * Sets the audio mode to the mode that is passed in.
     */
    public void setAudioMode(int mode) {
        if (AudioMode.BLUETOOTH == mode) {

            // dont set mAudioMode because we will get a notificaiton through
            // onBluetoothIndicationChange if successful
            toggleBluetooth(true);
        } else if (AudioMode.EARPIECE == mode) {
            toggleBluetooth(false);
        }
    }

    /**
     * Called when the bluetooth connection changes.
     * We adjust the audio mode according to the state we receive.
     */
    @Override
    public void onBluetoothIndicationChange(boolean isConnected, BluetoothManager btManager) {
        int newMode = mAudioMode;

        if (isConnected) {
            newMode = AudioMode.BLUETOOTH;
        } else {
            newMode = AudioMode.EARPIECE;
        }

        changeAudioModeTo(newMode);
    }

    private void toggleBluetooth(boolean on) {
        if (on) {
            mBluetoothManager.connectBluetoothAudio();
        } else {
            mBluetoothManager.disconnectBluetoothAudio();
        }
    }

    private void init() {
        mBluetoothManager.addBluetoothIndicatorListener(this);
    }

    private void changeAudioModeTo(int mode) {
        if (mAudioMode != mode) {
            Log.i(LOG_TAG, "Audio mode changing to " + AudioMode.toString(mode));

            mPreviousMode = mAudioMode;
            mAudioMode = mode;

            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onAudioModeChange(mPreviousMode, mAudioMode);
        }
    }

    public interface AudioModeListener {
        void onAudioModeChange(int previousMode, int newMode);
    }
}
