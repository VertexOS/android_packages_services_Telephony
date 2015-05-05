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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.Voicemail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Construct a queries to interact with the voicemails table.
 */
public class VoicemailsQueryHelper {
    final static String[] PROJECTION = new String[] {
            Voicemails._ID,              // 0
            Voicemails.SOURCE_DATA,      // 1
            Voicemails.IS_READ,          // 2
            Voicemails.DELETED,          // 3
    };

    public static final int _ID = 0;
    public static final int SOURCE_DATA = 1;
    public static final int IS_READ = 2;
    public static final int DELETED = 3;

    final static String READ_SELECTION = Voicemails.DIRTY + "=1 AND " + Voicemails.DELETED + "!=1";
    final static String DELETED_SELECTION = Voicemails.DELETED + "=1";

    private Context mContext;
    private ContentResolver mContentResolver;
    private Uri mSourceUri;

    public VoicemailsQueryHelper(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mSourceUri = VoicemailContract.Voicemails.buildSourceUri(mContext.getPackageName());
    }

    /**
     * Get all the local read voicemails that have not been synced to the server.
     *
     * @return A list of read voicemails.
     */
    public List<Voicemail> getReadVoicemails() {
        return getLocalVoicemails(READ_SELECTION);
    }

    /**
     * Get all the locally deleted voicemails that have not been synced to the server.
     *
     * @return A list of deleted voicemails.
     */
    public List<Voicemail> getDeletedVoicemails() {
        return getLocalVoicemails(DELETED_SELECTION);
    }

    /**
     * Get all voicemails locally stored.
     *
     * @return A list of all locally stored voicemails.
     */
    public List<Voicemail> getAllVoicemails() {
        return getLocalVoicemails(null);
    }

    /**
     * Utility method to make queries to the voicemail database.
     *
     * @param selection A filter declaring which rows to return. {@code null} returns all rows.
     * @return A list of voicemails according to the selection statement.
     */
    private List<Voicemail> getLocalVoicemails(String selection) {
        Cursor cursor = mContentResolver.query(mSourceUri, PROJECTION, selection, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            List<Voicemail> voicemails = new ArrayList<Voicemail>();
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(VoicemailsQueryHelper._ID);
                final String sourceData = cursor.getString(VoicemailsQueryHelper.SOURCE_DATA);
                Voicemail voicemail = Voicemail.createForUpdate(id, sourceData).build();
                voicemails.add(voicemail);
            }
            return voicemails;
        } finally {
            cursor.close();
        }
    }

    /**
     * Deletes a list of voicemails from the voicemail content provider.
     *
     * @param voicemails The list of voicemails to delete
     * @return The number of voicemails deleted
     */
    public int deleteFromDatabase(List<Voicemail> voicemails) {
        int count = voicemails.size();
        if (count == 0) {
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(voicemails.get(i).getId());
        }

        String selectionStatement = String.format(Voicemails._ID + " IN (%s)", sb.toString());
        return mContentResolver.delete(Voicemails.CONTENT_URI, selectionStatement, null);
    }

    /**
     * Utility method to delete a single voicemail.
     */
    public void deleteFromDatabase(Voicemail voicemail) {
        mContentResolver.delete(Voicemails.CONTENT_URI, Voicemails._ID + "=?",
                new String[] { Long.toString(voicemail.getId()) });
    }

    /**
     * Sends an update command to the voicemail content provider for a list of voicemails.
     * From the view of the provider, since the updater is the owner of the entry, a blank
     * "update" means that the voicemail source is indicating that the server has up-to-date
     * information on the voicemail. This flips the "dirty" bit to "0".
     *
     * @param voicemails The list of voicemails to update
     * @return The number of voicemails updated
     */
    public int markReadInDatabase(List<Voicemail> voicemails) {
        int count = voicemails.size();
        for (int i = 0; i < count; i++) {
            markReadInDatabase(voicemails.get(i));
        }
        return count;
    }

    /**
     * Utility method to mark single message as read.
     */
    public void markReadInDatabase(Voicemail voicemail) {
        Uri uri = ContentUris.withAppendedId(mSourceUri, voicemail.getId());
        ContentValues contentValues = new ContentValues();
        contentValues.put(Voicemails.IS_READ, "1");
        mContentResolver.update(uri, contentValues, null, null);
    }
}
