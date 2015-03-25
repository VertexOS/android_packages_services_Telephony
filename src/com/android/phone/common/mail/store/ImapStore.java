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
 * limitations under the License.
 */

package com.android.phone.common.mail.store;

import android.content.Context;

import com.android.phone.common.mail.MailTransport;
import com.android.phone.common.mail.Message;
import com.android.phone.common.mail.MessagingException;
import com.android.phone.common.mail.store.ImapFolder;

public class ImapStore {
    /**
     * A global suggestion to Store implementors on how much of the body
     * should be returned on FetchProfile.Item.BODY_SANE requests. We'll use 125k now.
     */
    public static final int FETCH_BODY_SANE_SUGGESTED_SIZE = (125 * 1024);
    private Context mContext;
    private String mUsername;
    private String mPassword;
    private MailTransport mTransport;
    private ImapConnection mConnection;

    public static final int FLAG_NONE         = 0x00;    // No flags
    public static final int FLAG_SSL          = 0x01;    // Use SSL
    public static final int FLAG_TLS          = 0x02;    // Use TLS
    public static final int FLAG_AUTHENTICATE = 0x04;    // Use name/password for authentication
    public static final int FLAG_TRUST_ALL    = 0x08;    // Trust all certificates
    public static final int FLAG_OAUTH        = 0x10;    // Use OAuth for authentication

    /**
     * Contains all the information necessary to log into an imap server
     */
    public ImapStore(Context context, String username, String password, int port,
            String serverName, int flags) {
        mContext = context;
        mUsername = username;
        mPassword = password;
        mTransport = new MailTransport(context, serverName, port, flags);
    }

    public ImapFolder getFolder(String name) {
        return new ImapFolder(this, name);
    }

    public String getUsername() {
        return mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    /** Returns a clone of the transport associated with this store. */
    MailTransport cloneTransport() {
        return mTransport.clone();
    }

    static class ImapException extends MessagingException {
        private static final long serialVersionUID = 1L;

        private final String mStatus;
        private final String mAlertText;
        private final String mResponseCode;

        public ImapException(String message, String status, String alertText,
                String responseCode) {
            super(message);
            mStatus = status;
            mAlertText = alertText;
            mResponseCode = responseCode;
        }

        public String getStatus() {
            return mStatus;
        }

        public String getAlertText() {
            return mAlertText;
        }

        public String getResponseCode() {
            return mResponseCode;
        }
    }

    public void closeConnection() {
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }
    }

    public ImapConnection getConnection() {
        if (mConnection == null) {
            mConnection = new ImapConnection(this);
        }
        return mConnection;
    }
}