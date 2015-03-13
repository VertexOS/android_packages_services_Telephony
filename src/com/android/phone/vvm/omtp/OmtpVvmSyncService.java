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

/**
 * A {@link Service} which runs the internal implementation of {@link AbstractThreadedSyncAdapter},
 * syncing voicemails to and from a visual voicemail server.
 */

package com.android.phone.vvm.omtp;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Voicemail;

import com.android.phone.vvm.omtp.sync.DirtyVoicemailQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * A service to run the VvmSyncAdapter.
 */
public class OmtpVvmSyncService extends Service {
    // Storage for an instance of the sync adapter
    private static OmtpVvmSyncAdapter sSyncAdapter = null;
    // Object to use as a thread-safe lock
    private static final Object sSyncAdapterLock = new Object();

    private Context mContext;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new OmtpVvmSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }

    public class OmtpVvmSyncAdapter extends AbstractThreadedSyncAdapter {
        public OmtpVvmSyncAdapter(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
            mContext = context;
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {
            if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false)) {
                List<Voicemail> readVoicemails = new ArrayList<Voicemail>();
                List<Voicemail> deletedVoicemails = new ArrayList<Voicemail>();

                Cursor cursor = DirtyVoicemailQuery.getDirtyVoicemails(mContext);
                if (cursor == null) {
                    return;
                }
                try {
                    while (cursor.moveToNext()) {
                        final long id = cursor.getLong(DirtyVoicemailQuery._ID);
                        final String sourceData = cursor.getString(DirtyVoicemailQuery.SOURCE_DATA);
                        final boolean isRead = cursor.getInt(DirtyVoicemailQuery.IS_READ) == 1;
                        final boolean deleted = cursor.getInt(DirtyVoicemailQuery.DELETED) == 1;
                        Voicemail voicemail = Voicemail.createForUpdate(id, sourceData).build();
                        if (deleted) {
                            // Check deleted first because if the voicemail is deleted, there's no
                            // need to mark as read.
                            deletedVoicemails.add(voicemail);
                        } else if (isRead) {
                            readVoicemails.add(voicemail);
                        }
                    }
                } finally {
                    cursor.close();
                }
                //TODO: send to server via IMAP
            }
        }
    }
}
