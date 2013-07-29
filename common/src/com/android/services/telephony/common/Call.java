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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class object used across CallHandlerService APIs.
 * Describes a single call and its state.
 */
final public class Call implements Parcelable {

    public static final int INVALID_CALL_ID = -1;

    /* Defines different states of this call */
    public static class State {
        public static final int INVALID = 0;

        // The call is idle.  Nothing active.
        public static final int IDLE = 1;

        // There is an active call.
        public static final int ACTIVE = 2;

        // A normal incoming phone call.
        public static final int INCOMING = 3;

        // An incoming phone call while another call is active.
        public static final int CALL_WAITING = 4;

        // A Mobile-originating (MO) call. This call is dialing out.
        public static final int DIALING = 5;

        // An active phone call placed on hold.
        public static final int ONHOLD = 6;
    }

    private int mCallId = INVALID_CALL_ID;
    private String mNumber = "";
    private int mState = State.INVALID;

    public Call(int callId) {
        mCallId = callId;
    }

    public int getCallId() {
        return mCallId;
    }

    public String getNumber() {
        return mNumber;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    /**
     * Parcelable implementation
     */

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCallId);
        dest.writeString(mNumber);
        dest.writeInt(mState);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Call> CREATOR
            = new Parcelable.Creator<Call>() {

        public Call createFromParcel(Parcel in) {
            return new Call(in);
        }

        public Call[] newArray(int size) {
            return new Call[size];
        }
    };

    private Call(Parcel in) {
        mCallId = in.readInt();
        mNumber = in.readString();
        mState = in.readInt();
    }

}
