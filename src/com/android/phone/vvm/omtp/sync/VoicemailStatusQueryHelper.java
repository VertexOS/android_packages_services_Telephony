/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.phone.vvm.omtp.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

/**
 * Construct queries to interact with the voicemail status table.
 */
public class VoicemailStatusQueryHelper {
    private static final String TAG = "VoicemailStatusQueryHelper";

    final static String[] PROJECTION = new String[] {
            Status._ID,                        // 0
            Status.NOTIFICATION_CHANNEL_STATE, // 1
            Status.SOURCE_PACKAGE              // 2
   };

    public static final int _ID = 0;
    public static final int NOTIFICATION_CHANNEL_STATE = 1;
    public static final int SOURCE_PACKAGE = 2;

    private Context mContext;
    private ContentResolver mContentResolver;
    private Uri mSourceUri;

    public VoicemailStatusQueryHelper(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mSourceUri = VoicemailContract.Status.buildSourceUri(mContext.getPackageName());
    }

    /**
     * Check if the notifications channel of a voicemail source is active. That is, when a new
     * voicemail is available, if the server able to notify the device.
     *
     * @return {@code true} if notifications channel is active, {@code false} otherwise.
     */
    public boolean isNotificationsChannelActive(PhoneAccountHandle phoneAccount) {
        Cursor cursor = null;
        if (phoneAccount != null) {
            String phoneAccountComponentName = phoneAccount.getComponentName().flattenToString();
            String phoneAccountId = phoneAccount.getId();
            if (phoneAccountComponentName == null || phoneAccountId == null) {
                return false;
            }
            try {
                String whereClause =
                        Status.PHONE_ACCOUNT_COMPONENT_NAME + "=? AND " +
                        Status.PHONE_ACCOUNT_ID + "=? AND " + Status.SOURCE_PACKAGE + "=?";
                String[] whereArgs = { phoneAccountComponentName, phoneAccountId,
                        mContext.getPackageName()};
                cursor = mContentResolver.query(
                        mSourceUri, PROJECTION, whereClause, whereArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getInt(NOTIFICATION_CHANNEL_STATE) ==
                            Status.NOTIFICATION_CHANNEL_STATE_OK;
                }
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return false;
    }
}
