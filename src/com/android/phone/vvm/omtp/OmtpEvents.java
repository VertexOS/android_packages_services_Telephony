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

package com.android.phone.vvm.omtp;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Events internal to the OMTP client. These should be translated into {@link
 * android.provider.VoicemailContract.Status} error codes before writing into the voicemail status
 * table.
 */
public enum OmtpEvents {

    // Configuration State
    CONFIG_REQUEST_STATUS_SUCCESS(Type.CONFIGURATION, true),

    // Data channel State

    // Successfully downloaded/uploaded data from the server, which means the data channel is clear.
    DATA_IMAP_OPERATION_COMPLETED(Type.DATA_CHANNEL, true),

    // The port provided in the STATUS SMS is invalid.
    DATA_INVALID_PORT(Type.DATA_CHANNEL, false),
    // No connection to the internet, and the carrier requires cellular data
    DATA_NO_CONNECTION_CELLULAR_REQUIRED(Type.DATA_CHANNEL, false),
    // No connection to the internet.
    DATA_NO_CONNECTION(Type.DATA_CHANNEL, false),
    // Address lookup for the server hostname failed. DNS error?
    DATA_CANNOT_RESOLVE_HOST_ON_NETWORK(Type.DATA_CHANNEL, false),
    // All destination address that resolves to the server hostname are rejected or timed out
    DATA_ALL_SOCKET_CONNECTION_FAILED(Type.DATA_CHANNEL, false),
    // Failed to establish SSL with the server, either with a direct SSL connection or by
    // STARTTLS command
    DATA_CANNOT_ESTABLISH_SSL_SESSION(Type.DATA_CHANNEL, false),
    // Identity of the server cannot be verified.
    DATA_SSL_INVALID_HOST_NAME(Type.DATA_CHANNEL, false),
    // The server rejected our username/password
    DATA_BAD_IMAP_CREDENTIAL(Type.DATA_CHANNEL, false),
    // A command to the server didn't result with an "OK" or continuation request
    DATA_REJECTED_SERVER_RESPONSE(Type.DATA_CHANNEL, false),
    // The server did not greet us with a "OK", possibly not a IMAP server.
    DATA_INVALID_INITIAL_SERVER_RESPONSE(Type.DATA_CHANNEL, false),
    // An IOException occurred while trying to open an ImapConnection
    // TODO: reduce scope
    DATA_IOE_ON_OPEN(Type.DATA_CHANNEL, false),
    // The SELECT command on a mailbox is rejected
    DATA_MAILBOX_OPEN_FAILED(Type.DATA_CHANNEL, false),
    // An IOException has occurred
    // TODO: reduce scope
    DATA_GENERIC_IMAP_IOE(Type.DATA_CHANNEL, false),
    // An SslException has occurred while opening an ImapConnection
    // TODO: reduce scope
    DATA_SSL_EXCEPTION(Type.DATA_CHANNEL, false),

    // Notification Channel

    // Cell signal restored, can received VVM SMSs
    NOTIFICATION_IN_SERVICE(Type.NOTIFICATION_CHANNEL, true),
    // Cell signal lost, cannot received VVM SMSs
    NOTIFICATION_SERVICE_LOST(Type.NOTIFICATION_CHANNEL, false),


    // Other
    OTHER_SOURCE_REMOVED(Type.OTHER, false);


    public static class Type {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({CONFIGURATION, DATA_CHANNEL, NOTIFICATION_CHANNEL, OTHER})
        public @interface Values {

        }

        public static final int CONFIGURATION = 1;
        public static final int DATA_CHANNEL = 2;
        public static final int NOTIFICATION_CHANNEL = 3;
        public static final int OTHER = 4;
    }

    private final int mType;
    private final boolean mIsSuccess;

    OmtpEvents(int type, boolean isSuccess) {
        mType = type;
        mIsSuccess = isSuccess;
    }


    @Type.Values
    public int getType() {
        return mType;
    }

    public boolean isSuccess() {
        return mIsSuccess;
    }

}
