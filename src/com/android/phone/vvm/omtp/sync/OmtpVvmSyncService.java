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

package com.android.phone.vvm.omtp.sync;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.VoicemailContract;
import android.telecom.Voicemail;

import com.android.phone.vvm.omtp.imap.ImapHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service to run the VvmSyncAdapter.
 */
public class OmtpVvmSyncService extends Service {
    // Storage for an instance of the sync adapter
    private static OmtpVvmSyncAdapter sSyncAdapter = null;
    // Object to use as a thread-safe lock
    private static final Object sSyncAdapterLock = new Object();

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
        /**
         * Sync triggers should pass this extra to clear the database and freshly populate from the
         * server.
         */
        public static final String SYNC_EXTRAS_DOWNLOAD = "extra_download";

        private Context mContext;

        public OmtpVvmSyncAdapter(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
            mContext = context;
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {
            ImapHelper imapHelper = new ImapHelper(mContext, account);
            VoicemailsQueryHelper queryHelper = new VoicemailsQueryHelper(mContext);

            if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false)) {
                List<Voicemail> readVoicemails = queryHelper.getReadVoicemails();
                List<Voicemail> deletedVoicemails = queryHelper.getDeletedVoicemails();

                if (deletedVoicemails != null &&
                        imapHelper.markMessagesAsDeleted(deletedVoicemails)) {
                    // We want to delete selectively instead of all the voicemails for this provider
                    // in case the state changed since the IMAP query was completed.
                    queryHelper.deleteFromDatabase(deletedVoicemails);
                }

                if (readVoicemails != null && imapHelper.markMessagesAsRead(readVoicemails)) {
                    queryHelper.markReadInDatabase(readVoicemails);
                }
            }

            if (extras.getBoolean(SYNC_EXTRAS_DOWNLOAD, false)) {
                List<Voicemail> serverVoicemails = imapHelper.fetchAllVoicemails();
                List<Voicemail> localVoicemails = queryHelper.getAllVoicemails();

                if (localVoicemails == null || serverVoicemails == null) {
                    // Null value means the query failed.
                    return;
                }

                Map<String, Voicemail> remoteMap = buildMap(serverVoicemails);

                // Go through all the local voicemails and check if they are on the server.
                // They may be read or deleted on the server but not locally. Perform the
                // appropriate local operation if the status differs from the server. Remove
                // the messages that exist both locally and on the server to know which server
                // messages to insert locally.
                for (int i = 0; i < localVoicemails.size(); i++) {
                    Voicemail localVoicemail = localVoicemails.get(i);
                    Voicemail remoteVoicemail = remoteMap.remove(localVoicemail.getSourceData());
                    if (remoteVoicemail == null) {
                        queryHelper.deleteFromDatabase(localVoicemail);
                    } else {
                        if (remoteVoicemail.isRead() != localVoicemail.isRead()) {
                            queryHelper.markReadInDatabase(localVoicemail);
                        }
                    }
                }

                // The leftover messages are messages that exist on the server but not locally.
                for (Voicemail remoteVoicemail : remoteMap.values()) {
                    VoicemailContract.Voicemails.insert(mContext, remoteVoicemail);
                }
            }
        }

        /**
         * Builds a map from provider data to message for the given collection of voicemails.
         */
        private Map<String, Voicemail> buildMap(List<Voicemail> messages) {
            Map<String, Voicemail> map = new HashMap<String, Voicemail>();
            for (Voicemail message : messages) {
                map.put(message.getSourceData(), message);
            }
            return map;
        }
    }
}
