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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.android.internal.os.SomeArgs;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.PhoneUtils;
import com.android.internal.telephony.Call;


/**
 * Helper class that implements special behavior related to emergency calls. Specifically, this
 * class handles the case of the user trying to dial an emergency number while the radio is off
 * (i.e. the device is in airplane mode), by forcibly turning the radio back on, waiting for it to
 * come up, and then retrying the emergency call.
 */
public class EmergencyCallHelper {

    /**
     * Receives the result of the EmergencyCallHelper's attempt to turn on the radio.
     */
    interface Callback {
        void onComplete(Phone phone, boolean isRadioReady);
    }

    // Number of times to retry the call, and time between retry attempts.
    public static final int MAX_NUM_RETRIES = 5;
    public static final long TIME_BETWEEN_RETRIES_MILLIS = 5000;  // msec

    // Handler message codes; see handleMessage()
    private static final int MSG_START_SEQUENCE = 1;
    private static final int MSG_SERVICE_STATE_CHANGED = 2;
    private static final int MSG_RETRY_TIMEOUT = 3;

    private final Context mContext;
    private static String mEmergencyNum;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_SEQUENCE:
                    SomeArgs args = (SomeArgs) msg.obj;
                    EmergencyCallHelper.Callback callback =
                            (EmergencyCallHelper.Callback) args.arg1;
                    args.recycle();

                    startSequenceInternal(callback);
                    break;
                case MSG_SERVICE_STATE_CHANGED:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    onServiceStateChanged((ServiceState)ar.result, (int)ar.userObj);
                    break;
                case MSG_RETRY_TIMEOUT:
                    onRetryTimeout();
                    break;
                default:
                    Log.wtf(this, "handleMessage: unexpected message: %d.", msg.what);
                    break;
            }
        }
    };


    private Callback mCallback;  // The callback to notify upon completion.
    private int mNumRetriesSoFar;
    private static int mPhoneCount;

    public EmergencyCallHelper(Context context) {
        Log.d(this, "EmergencyCallHelper constructor.");
        mContext = context;
    }

    /**
     * Starts the "turn on radio" sequence. This is the (single) external API of the
     * EmergencyCallHelper class.
     *
     * This method kicks off the following sequence:
     * - Power on the radio.
     * - Listen for the service state change event telling us the radio has come up.
     * - Retry if we've gone {@link #TIME_BETWEEN_RETRIES_MILLIS} without any response from the
     *   radio.
     * - Finally, clean up any leftover state.
     *
     * This method is safe to call from any thread, since it simply posts a message to the
     * EmergencyCallHelper's handler (thus ensuring that the rest of the sequence is entirely
     * serialized, and runs only on the handler thread.)
     */
    public void startTurnOnRadioSequence(String emergencyNumber, Callback callback) {
        Log.d(this, "startTurnOnRadioSequence");

        SomeArgs args = SomeArgs.obtain();
        args.arg1 = callback;
        mEmergencyNum = emergencyNumber;
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mHandler.obtainMessage(MSG_START_SEQUENCE, args).sendToTarget();
    }

    /**
     * Actual implementation of startTurnOnRadioSequence(), guaranteed to run on the handler thread.
     * @see #startTurnOnRadioSequence
     */
    private void startSequenceInternal(Callback callback) {
        Log.d(this, "startSequenceInternal()");
        // First of all, clean up any state left over from a prior emergency call sequence. This
        // ensures that we'll behave sanely if another startTurnOnRadioSequence() comes in while
        // we're already in the middle of the sequence.
        cleanup();
        mCallback = callback;


        // No need to check the current service state here, since the only reason to invoke this
        // method in the first place is if the radio is powered-off. So just go ahead and turn the
        // radio on.

        powerOnRadio();  // We'll get an onServiceStateChanged() callback
                         // when the radio successfully comes up.

        // Next step: when the SERVICE_STATE_CHANGED event comes in, we'll retry the call; see
        // onServiceStateChanged(). But also, just in case, start a timer to make sure we'll retry
        // the call even if the SERVICE_STATE_CHANGED event never comes in for some reason.
        startRetryTimer();
    }

    /**
     * Handles the SERVICE_STATE_CHANGED event. Normally this event tells us that the radio has
     * finally come up. In that case, it's now safe to actually place the emergency call.
     */
    private void onServiceStateChanged(ServiceState state, int phoneId) {
        Log.d(this, "onServiceStateChanged(), new state = %s phoneId = %d.", state, phoneId);

        // Possible service states:
        // - STATE_IN_SERVICE        // Normal operation
        // - STATE_OUT_OF_SERVICE    // Still searching for an operator to register to,
        //                           // or no radio signal
        // - STATE_EMERGENCY_ONLY    // Phone is locked; only emergency numbers are allowed
        // - STATE_POWER_OFF         // Radio is explicitly powered off (airplane mode)
        Phone phone =  PhoneFactory.getPhone(phoneId);
        if (isOkToCall(state.getState(), phone)) {
            // Woo hoo!  It's OK to actually place the call.

            Log.d(this, "onServiceStateChanged: ok to call! PhoneId:" + phoneId );
            boolean isEmergencyNum = isEmergencyNumber(phone, mEmergencyNum);

            if (!isEmergencyNum)
                Log.d(this, "" + mEmergencyNum + " not a emergency number in phoneId: " + phoneId);

            if (PhoneUtils.isDeviceInSingleStandBy() || isEmergencyNum) {
                onComplete(phone, true);
                cleanup();
            } else {
                Log.d(this, "wait for other Phone to be in service");
            }

        } else {
            // The service state changed, but we're still not ready to call yet. (This probably was
            // the transition from STATE_POWER_OFF to STATE_OUT_OF_SERVICE, which happens
            // immediately after powering-on the radio.)
            //
            // So just keep waiting; we'll probably get to either STATE_IN_SERVICE or
            // STATE_EMERGENCY_ONLY very shortly. (Or even if that doesn't happen, we'll at least do
            // another retry when the RETRY_TIMEOUT event fires.)
            Log.d(this, "onServiceStateChanged: not ready to call yet, keep waiting.");
        }
    }

    private boolean isOkToCall(int serviceState, Phone phone) {
        // Once we reach either STATE_IN_SERVICE or STATE_EMERGENCY_ONLY, it's finally OK to place
        // the emergency call.

        Call.State callState = phone.getForegroundCall().getState();
        return ((phone.getState() == PhoneConstants.State.OFFHOOK
                && callState != Call.State.DIALING)
                || (serviceState == ServiceState.STATE_IN_SERVICE)
                || (serviceState == ServiceState.STATE_EMERGENCY_ONLY)
                || phone.getServiceState().isEmergencyOnly());
    }

    /**
     * Handles the retry timer expiring.
     */
    private void onRetryTimeout() {
        Log.d(this, "onRetryTimeout(): retries = %d.", mNumRetriesSoFar);

        // - If we're actually in a call, we've succeeded.
        // - Otherwise, if the radio is now on, that means we successfully got out of airplane mode
        //   but somehow didn't get the service state change event.  In that case, try to place the
        //   call.
        // - If the radio is still powered off, try powering it on again.
        int radioPoweredOnCount = 0;
        for (int phoneId =0; phoneId < mPhoneCount; phoneId++) {
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                Log.d(this, " phoneid " + phoneId +
                    " state = %s, service state = %d",
                    phone.getState() , phone.getServiceState().getState());
                 if ((phone.getServiceState().getState() != ServiceState.STATE_OUT_OF_SERVICE)
                    && isOkToCall(phone.getServiceState().getState(), phone)) {
                      Log.d(this, "onRetryTimeout: Phoneid " + phoneId + " Radio is on.");
                      // Since radio is on, call onServiceStateChanged which will take care
                      // of placing the call
                      onServiceStateChanged(phone.getServiceState(), phoneId);
                      radioPoweredOnCount++;
                 }
            }
        }

        if (radioPoweredOnCount < mPhoneCount) {
            // Uh oh; we've waited the full TIME_BETWEEN_RETRIES_MILLIS and
            // the radio is still not powered-on.  Try again.

            mNumRetriesSoFar++;
            Log.d(this, "mNumRetriesSoFar is now " + mNumRetriesSoFar);

            if (mNumRetriesSoFar > MAX_NUM_RETRIES) {
                Log.w(this, "Hit MAX_NUM_RETRIES; giving up.");
                Log.i(this, "get Primary Stack id related phone");
                onComplete(getPrimaryStackIdPhone(), true);
                cleanup();
            } else {
                Log.d(this, "Trying (again) to turn on the radio.");
                powerOnRadio();  // Again, we'll (hopefully) get an
                                 // onServiceStateChanged() callback
                                 // when the radio successfully comes up.
                startRetryTimer();
            }
        }
    }

    /**
     * Attempt to power on the radio (i.e. take the device out of airplane mode.)
     * Additionally, start listening for service state changes; we'll eventually get an
     * onServiceStateChanged() callback when the radio successfully comes up.
     */
    private void powerOnRadio() {
        Log.d(this, "powerOnRadio().");

        // We're about to turn on the radio, so arrange to be notified when the sequence is
        // complete.
        registerForServiceStateChanged();

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
        } else {
            // Otherwise, for some strange reason the radio is off (even though the Settings
            // database doesn't think we're in airplane mode.)  In this case just turn the radio
            // back on.
            Log.d(this, "==> (Apparently) not in airplane mode; manually powering radio on.");
            for (int phoneId =0; phoneId < mPhoneCount; phoneId++) {
                 Phone phone = PhoneFactory.getPhone(phoneId);
                 if (phone != null && !phone.isRadioOn()) {
                     phone.setRadioPower(true);
                 }
            }
        }
    }

    /**
     * Clean up when done with the whole sequence: either after successfully turning on the radio,
     * or after bailing out because of too many failures.
     *
     * The exact cleanup steps are:
     * - Notify callback if we still hadn't sent it a response.
     * - Double-check that we're not still registered for any telephony events
     * - Clean up any extraneous handler messages (like retry timeouts) still in the queue
     *
     * Basically this method guarantees that there will be no more activity from the
     * EmergencyCallHelper until someone kicks off the whole sequence again with another call to
     * {@link #startTurnOnRadioSequence}
     *
     * TODO: Do the work for the comment below:
     * Note we don't call this method simply after a successful call to placeCall(), since it's
     * still possible the call will disconnect very quickly with an OUT_OF_SERVICE error.
     */
    private void cleanup() {
        Log.d(this, "cleanup()");

        unregisterForServiceStateChanged();
        cancelRetryTimer();

        mNumRetriesSoFar = 0;
    }

    private void startRetryTimer() {
        cancelRetryTimer();
        mHandler.sendEmptyMessageDelayed(MSG_RETRY_TIMEOUT, TIME_BETWEEN_RETRIES_MILLIS);
    }

    private void cancelRetryTimer() {
        mHandler.removeMessages(MSG_RETRY_TIMEOUT);
    }

    private void registerForServiceStateChanged() {
        // Unregister first, just to make sure we never register ourselves twice.  (We need this
        // because Phone.registerForServiceStateChanged() does not prevent multiple registration of
        // the same handler.)
        unregisterForServiceStateChanged();
        for (int phoneId =0; phoneId < mPhoneCount; phoneId++) {
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                phone.registerForServiceStateChanged(mHandler,
                        MSG_SERVICE_STATE_CHANGED, phoneId);
            }
        }
    }

    private void unregisterForServiceStateChanged() {
        // This method is safe to call even if we haven't set phone yet
        for (int phoneId =0; phoneId < mPhoneCount; phoneId++) {
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                phone.unregisterForServiceStateChanged(mHandler);
            }
            mHandler.removeMessages(MSG_SERVICE_STATE_CHANGED);
        }
    }

    private void onComplete(Phone phone, boolean isRadioReady) {
        if (mCallback != null) {
            Callback tempCallback = mCallback;
            mCallback = null;
            tempCallback.onComplete(phone, isRadioReady);
        }
    }

    private Phone getPrimaryStackIdPhone() {
        return PhoneFactory.getPhone(PhoneUtils.getPhoneIdForECall());
    }

    private boolean isEmergencyNumber(Phone phone, String number) {
        return PhoneNumberUtils.isLocalEmergencyNumber(mContext, phone.getSubId(), number);
    }
}
