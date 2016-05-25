/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;

public class VoicemailStatus {


    public static class Editor {

        private final Context mContext;
        private final PhoneAccountHandle mPhoneAccountHandle;

        private ContentValues mValues = new ContentValues();

        private Editor(Context context, PhoneAccountHandle phoneAccountHandle) {
            mContext = context;
            mPhoneAccountHandle = phoneAccountHandle;
        }

        public Editor setType(String type) {
            mValues.put(Status.SOURCE_TYPE, type);
            return this;
        }

        public Editor setConfigurationState(int configurationState) {
            mValues.put(Status.CONFIGURATION_STATE, configurationState);
            return this;
        }

        public Editor setDataChannelState(int dataChannelState) {
            mValues.put(Status.DATA_CHANNEL_STATE, dataChannelState);
            return this;
        }

        public Editor setNotificationChannelState(int notificationChannelState) {
            mValues.put(Status.NOTIFICATION_CHANNEL_STATE, notificationChannelState);
            return this;
        }

        public Editor setQuota(int occupied, int total) {
            if (occupied == VoicemailContract.Status.QUOTA_UNAVAILABLE
                    && total == VoicemailContract.Status.QUOTA_UNAVAILABLE) {
                return this;
            }

            mValues.put(Status.QUOTA_OCCUPIED, occupied);
            mValues.put(Status.QUOTA_TOTAL, total);
            return this;
        }

        public void apply() {
            mValues.put(Status.PHONE_ACCOUNT_COMPONENT_NAME,
                    mPhoneAccountHandle.getComponentName().flattenToString());
            mValues.put(Status.PHONE_ACCOUNT_ID, mPhoneAccountHandle.getId());
            ContentResolver contentResolver = mContext.getContentResolver();
            Uri statusUri = VoicemailContract.Status.buildSourceUri(mContext.getPackageName());
            contentResolver.insert(statusUri, mValues);
        }
    }

    public static Editor edit(Context context, PhoneAccountHandle phoneAccountHandle) {
        return new Editor(context, phoneAccountHandle);
    }

    public static Editor edit(Context context, int subId) {
        PhoneAccountHandle phone = PhoneUtils.makePstnPhoneAccountHandle(
                SubscriptionManager.getPhoneId(subId));
        return new Editor(context, phone);
    }
}
