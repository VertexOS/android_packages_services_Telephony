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
import android.provider.VoicemailContract.Voicemails;

/**
 * Construct a query to get dirty voicemails.
 */
public class DirtyVoicemailQuery {
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

    final static String SELECTION = Voicemails.DIRTY + "=1";

    /**
     * Get all the locally modified voicemails that have not been synced to the server.
     *
     * @param context The context from the package calling the method. This will be the source.
     * @return A list of all locally modified voicemails.
     */
    public static Cursor getDirtyVoicemails(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri statusUri = VoicemailContract.Voicemails.buildSourceUri(context.getPackageName());
        return contentResolver.query(statusUri, PROJECTION, SELECTION, null, null);
    }
}
