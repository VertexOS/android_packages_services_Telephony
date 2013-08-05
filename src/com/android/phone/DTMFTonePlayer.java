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

import com.google.common.collect.ImmutableMap;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;
import com.android.services.telephony.common.Call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Playing DTMF tones through the CallManager.
 */
public class DTMFTonePlayer implements CallModeler.Listener {
    private static final String LOG_TAG = DTMFTonePlayer.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final int DTMF_STOP = 100;

    /** Hash Map to map a character to a tone*/
    private static final Map<Character, Integer> mToneMap =
            ImmutableMap.<Character, Integer>builder()
                    .put('1', ToneGenerator.TONE_DTMF_1)
                    .put('2', ToneGenerator.TONE_DTMF_2)
                    .put('3', ToneGenerator.TONE_DTMF_3)
                    .put('4', ToneGenerator.TONE_DTMF_4)
                    .put('5', ToneGenerator.TONE_DTMF_5)
                    .put('6', ToneGenerator.TONE_DTMF_6)
                    .put('7', ToneGenerator.TONE_DTMF_7)
                    .put('8', ToneGenerator.TONE_DTMF_8)
                    .put('9', ToneGenerator.TONE_DTMF_9)
                    .put('0', ToneGenerator.TONE_DTMF_0)
                    .put('#', ToneGenerator.TONE_DTMF_P)
                    .put('*', ToneGenerator.TONE_DTMF_S)
                    .build();

    private final CallManager mCallManager;
    private final CallModeler mCallModeler;
    private final Object mToneGeneratorLock = new Object();
    private ToneGenerator mToneGenerator;
    private boolean mLocalToneEnabled;

    public DTMFTonePlayer(CallManager callManager, CallModeler callModeler) {
        mCallManager = callManager;
        mCallModeler = callModeler;
    }

    @Override
    public void onDisconnect(Call call) {
        checkCallState();
    }

    @Override
    public void onUpdate(List<Call> calls, boolean full) {
        checkCallState();
    }

    /**
     * Allocates some resources we keep around during a "dialer session".
     *
     * (Currently, a "dialer session" just means any situation where we
     * might need to play local DTMF tones, which means that we need to
     * keep a ToneGenerator instance around.  A ToneGenerator instance
     * keeps an AudioTrack resource busy in AudioFlinger, so we don't want
     * to keep it around forever.)
     *
     * Call {@link stopDialerSession} to release the dialer session
     * resources.
     */
    public void startDialerSession() {
        logD("startDialerSession()... this = " + this);

        // see if we need to play local tones.
        if (PhoneGlobals.getInstance().getResources().getBoolean(R.bool.allow_local_dtmf_tones)) {
            mLocalToneEnabled = Settings.System.getInt(
                    PhoneGlobals.getInstance().getContentResolver(),
                    Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;
        } else {
            mLocalToneEnabled = false;
        }
        logD("- startDialerSession: mLocalToneEnabled = " + mLocalToneEnabled);

        // create the tone generator
        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        if (mLocalToneEnabled) {
            synchronized (mToneGeneratorLock) {
                if (mToneGenerator == null) {
                    try {
                        mToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
                    } catch (RuntimeException e) {
                        Log.e(LOG_TAG, "Exception caught while creating local tone generator", e);
                        mToneGenerator = null;
                    }
                }
            }
        }
    }

    /**
     * Releases resources we keep around during a "dialer session"
     * (see {@link startDialerSession}).
     *
     * It's safe to call this even without a corresponding
     * startDialerSession call.
     */
    public void stopDialerSession() {
        // release the tone generator.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    /**
     * Starts playback of the dtmf tone corresponding to the parameter.
     */
    public void playDtmfTone(char c) {
        // Only play the tone if it exists.
        if (!mToneMap.containsKey(c)) {
            return;
        }

        if (!okToDialDtmfTones()) {
            return;
        }

        // Read the settings as it may be changed by the user during the call
        Phone phone = mCallManager.getFgPhone();

        logD("startDtmfTone()...");

        // Pass as a char to be sent to network
        logD("send long dtmf for " + c);
        mCallManager.startDtmf(c);

        startLocalToneIfNeeded(c);
    }

    public void stopDtmfTone() {
        mCallManager.stopDtmf();
        stopLocalToneIfNeeded();
    }

    /**
     * Plays the local tone based the phone type, optionally forcing a short
     * tone.
     */
    private void startLocalToneIfNeeded(char c) {
        if (mLocalToneEnabled) {
            synchronized (mToneGeneratorLock) {
                if (mToneGenerator == null) {
                    logD("startDtmfTone: mToneGenerator == null, tone: " + c);
                } else {
                    logD("starting local tone " + c);
                    int toneDuration = -1;
                    mToneGenerator.startTone(mToneMap.get(c), toneDuration);
                }
            }
        }
    }

    /**
     * Stops the local tone based on the phone type.
     */
    public void stopLocalToneIfNeeded() {
        // if local tone playback is enabled, stop it.
        logD("trying to stop local tone...");
        if (mLocalToneEnabled) {
            synchronized (mToneGeneratorLock) {
                if (mToneGenerator == null) {
                    logD("stopLocalTone: mToneGenerator == null");
                } else {
                    logD("stopping local tone.");
                    mToneGenerator.stopTone();
                }
            }
        }
    }

    private boolean okToDialDtmfTones() {
        boolean hasActiveCall = false;
        boolean hasIncomingCall = false;

        final List<Call> calls = mCallModeler.getFullList();
        final int len = calls.size();

        for (int i = 0; i < len; i++) {
            hasActiveCall |= (calls.get(i).getState() == Call.State.ACTIVE);
            hasIncomingCall |= (calls.get(i).getState() == Call.State.INCOMING);
        }

        return hasActiveCall && !hasIncomingCall;
    }

    /**
     * Checks to see if there are any active calls. If there are, then we want to allocate the tone
     * resources for playing DTMF tone, otherwise release them.
     */
    private void checkCallState() {
        if (mCallModeler.hasOutstandingActiveCall()) {
            startDialerSession();
        } else {
            stopDialerSession();
        }
    }

    /**
     * static logging method
     */
    private static void logD(String msg) {
        if (DBG) {
            Log.d(LOG_TAG, msg);
        }
    }

}
