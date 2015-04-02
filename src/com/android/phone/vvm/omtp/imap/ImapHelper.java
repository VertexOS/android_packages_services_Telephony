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
package com.android.phone.vvm.omtp.imap;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.telecom.Voicemail;
import android.util.Log;

import com.android.phone.common.mail.Message;
import com.android.phone.common.mail.MessagingException;
import com.android.phone.common.mail.store.ImapFolder;
import com.android.phone.common.mail.store.ImapStore;
import com.android.phone.common.mail.store.imap.ImapConstants;
import com.android.phone.vvm.omtp.OmtpConstants;

import java.util.List;

/**
 * A helper interface to abstract commands sent across IMAP interface for a given account.
 */
public class ImapHelper {
    private final String TAG = "ImapHelper";

    private ImapFolder mFolder;
    private ImapStore mImapStore;

    public ImapHelper(Context context, Account account) {
        try {
            AccountManager accountManager = AccountManager.get(context);
            String username = accountManager.getUserData(account, OmtpConstants.IMAP_USER_NAME);
            String password = accountManager.getUserData(account, OmtpConstants.IMAP_PASSWORD);
            String serverName = accountManager.getUserData(account, OmtpConstants.SERVER_ADDRESS);
            int port = Integer.parseInt(
                    accountManager.getUserData(account, OmtpConstants.IMAP_PORT));
            // TODO: determine the security protocol (e.g. ssl, tls, none, etc.)
            mImapStore = new ImapStore(
                    context, username, password, port, serverName,
                    ImapStore.FLAG_SSL | ImapStore.FLAG_TLS);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Could not parse port number", e);
        }
    }

    /** The caller thread will block until the method returns. */
    public boolean markMessagesAsRead(List<Voicemail> voicemails) {
        return setFlags(voicemails, Message.FLAG_SEEN);
    }

    /** The caller thread will block until the method returns. */
    public boolean markMessagesAsDeleted(List<Voicemail> voicemails) {
        return setFlags(voicemails, Message.FLAG_DELETED);
    }

    /**
     * Set flags on the server for a given set of voicemails.
     *
     * @param voicemails The voicemails to set flags for.
     * @param flags The flags to set on the voicemails.
     * @return {@code true} if the operation completes successfully, {@code false} otherwise.
     */
    private boolean setFlags(List<Voicemail> voicemails, String... flags) {
        if (voicemails.size() == 0) {
            return false;
        }
        try {
            mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
            if (mFolder != null ) {
                mFolder.setFlags(convertToImapMessages(voicemails), flags, true);
                return true;
            }
            return false;
        } catch (MessagingException e) {
            Log.e(TAG, "Messaging exception: ", e);
            return false;
        } finally {
            closeImapFolder();
        }
    }

    private ImapFolder openImapFolder(String modeReadWrite) {
        try {
            ImapFolder folder = new ImapFolder(mImapStore, ImapConstants.INBOX);
            folder.open(modeReadWrite);
            return folder;
        } catch (MessagingException e) {
            Log.e(TAG, "Messaging Exception:", e);
        }
        return null;
    }

    private Message[] convertToImapMessages(List<Voicemail> voicemails) {
        Message[] messages = new Message[voicemails.size()];
        for (int i = 0; i < voicemails.size(); ++i) {
            messages[i] = new Message();
            messages[i].setUid(voicemails.get(i).getSourceData());
        }
        return messages;
    }

    private void closeImapFolder() {
        if (mFolder != null) {
            mFolder.close(true);
        }
    }
}