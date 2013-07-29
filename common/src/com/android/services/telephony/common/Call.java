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

    private int mCallId = INVALID_CALL_ID;
    private String mNumber = "";
    // TODO(klp): Add call state type

    public Call(int callId) {
        mCallId = callId;
    }

    public int getCallId() {
        return mCallId;
    }

    public String getNumber() {
        return mNumber;
    }

    /**
     * Parcelable implementation
     */

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCallId);
        dest.writeString(mNumber);
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
    }

}
