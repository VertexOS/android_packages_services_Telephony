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

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.vvm.omtp.OmtpVvmSyncAccountManager;
import com.android.phone.vvm.omtp.imap.ImapHelper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FetchVoicemailReceiver extends BroadcastReceiver {
    private static final String TAG = "FetchVoicemailReceiver";

    final static String[] PROJECTION = new String[] {
        Voicemails.SOURCE_DATA,      // 0
        Voicemails.PHONE_ACCOUNT_ID, // 1
    };

    public static final int SOURCE_DATA = 0;
    public static final int PHONE_ACCOUNT_ID = 1;

    private ContentResolver mContentResolver;
    private Uri mUri;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (VoicemailContract.ACTION_FETCH_VOICEMAIL.equals(intent.getAction())) {
            mContentResolver = context.getContentResolver();
            mUri = intent.getData();

            if (mUri == null) {
                Log.w(TAG, VoicemailContract.ACTION_FETCH_VOICEMAIL + " intent sent with no data");
                return;
            }

            if (!context.getPackageName().equals(
                    mUri.getQueryParameter(VoicemailContract.PARAM_KEY_SOURCE_PACKAGE))) {
                // Ignore if the fetch request is for a voicemail not from this package.
                return;
            }

            Cursor cursor = mContentResolver.query(mUri, PROJECTION, null, null, null);
            if (cursor == null) {
                return;
            }
            try {
                if (cursor.moveToFirst()) {
                    final String uid = cursor.getString(SOURCE_DATA);
                    String accountId = cursor.getString(PHONE_ACCOUNT_ID);
                    if (TextUtils.isEmpty(accountId)) {
                        TelephonyManager telephonyManager = (TelephonyManager)
                                context.getSystemService(Context.TELEPHONY_SERVICE);
                        accountId = telephonyManager.getSimSerialNumber();

                        if (TextUtils.isEmpty(accountId)) {
                            Log.e(TAG, "Account null and no default sim found.");
                            return;
                        }
                    }
                    final Account account = new Account(accountId,
                            OmtpVvmSyncAccountManager.ACCOUNT_TYPE);
                    Executor executor = Executors.newCachedThreadPool();
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            new ImapHelper(context, account).fetchVoicemailPayload(
                                    new VoicemailFetchedCallback(context, mUri), uid);
                        }
                    });
                }
            } finally {
                cursor.close();
            }
        }
    }
}
