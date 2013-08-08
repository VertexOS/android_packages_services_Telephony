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

import com.android.internal.telephony.PhoneConstants;

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

    // Number presentation type for caller id display
    // normal
    public static int PRESENTATION_ALLOWED = PhoneConstants.PRESENTATION_ALLOWED;
    // block by user
    public static int PRESENTATION_RESTRICTED = PhoneConstants.PRESENTATION_RESTRICTED;
    // no specified or unknown by network
    public static int PRESENTATION_UNKNOWN = PhoneConstants.PRESENTATION_UNKNOWN;
    // show pay phone info
    public static int PRESENTATION_PAYPHONE = PhoneConstants.PRESENTATION_PAYPHONE;

    private int mCallId = INVALID_CALL_ID;
    private String mNumber = "";
    private int mState = State.INVALID;
    private int mNumberPresentation = PRESENTATION_ALLOWED;
    private int mCnapNamePresentation = PRESENTATION_ALLOWED;
    private String mCnapName = "";

    public Call(int callId) {
        mCallId = callId;
    }

    public int getCallId() {
        return mCallId;
    }

    public String getNumber() {
        return mNumber;
    }

    public void setNumber(String number) {
        mNumber = number;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public int getNumberPresentation() {
        return mNumberPresentation;
    }

    public void setNumberPresentation(int presentation) {
        mNumberPresentation = presentation;
    }

    public int getCnapNamePresentation() {
        return mCnapNamePresentation;
    }

    public void setCnapNamePresentation(int presentation) {
        mCnapNamePresentation = presentation;
    }

    public String getCnapName() {
        return mCnapName;
    }

    public void setCnapName(String cnapName) {
        mCnapName = cnapName;
    }

    /**
     * Parcelable implementation
     */

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCallId);
        dest.writeString(mNumber);
        dest.writeInt(mState);
        dest.writeInt(mNumberPresentation);
        dest.writeInt(mCnapNamePresentation);
        dest.writeString(mCnapName);
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
        mNumberPresentation = in.readInt();
        mCnapNamePresentation = in.readInt();
        mCnapName = in.readString();
    }

}
