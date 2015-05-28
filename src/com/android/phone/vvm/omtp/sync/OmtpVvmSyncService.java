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
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.provider.VoicemailContract;
import android.telecom.Voicemail;
import android.util.Log;

import com.android.phone.PhoneUtils;
import com.android.phone.vvm.omtp.imap.ImapHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sync OMTP visual voicemail.
 */
public class OmtpVvmSyncService extends IntentService {
    private static final String TAG = OmtpVvmSyncService.class.getSimpleName();

    /** Signifies a sync with both uploading to the server and downloading from the server. */
    public static final String SYNC_FULL_SYNC = "full_sync";
    /** Only upload to the server. */
    public static final String SYNC_UPLOAD_ONLY = "upload_only";
    /** Only download from the server. */
    public static final String SYNC_DOWNLOAD_ONLY = "download_only";
    /** The account to sync. */
    public static final String EXTRA_ACCOUNT = "account";

    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;

    private VoicemailsQueryHelper mQueryHelper;

    private ConnectivityManager mConnectivityManager;

    public OmtpVvmSyncService() {
        super("OmtpVvmSyncService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mQueryHelper = new VoicemailsQueryHelper(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onHandleIntent: could not handle null intent");
            return;
        }

        String action = intent.getAction();
        OmtpVvmSyncAccountManager syncAccountManager = OmtpVvmSyncAccountManager.getInstance(this);
        Account account = intent.getParcelableExtra(EXTRA_ACCOUNT);
        if (account != null && syncAccountManager.isAccountRegistered(account)) {
            Log.v(TAG, "Sync requested: " + action + " - for account: " + account.name);
            doSync(account, action);
        } else {
            Log.v(TAG, "Sync requested: " + action + " - for all accounts");
            Account[] accounts = syncAccountManager.getOmtpAccounts();
            for (int i = 0; i < accounts.length; i++) {
                doSync(accounts[i], action);
            }
        }
    }

    private void doSync(Account account, String action) {
        int subId = PhoneUtils.getSubIdForPhoneAccountHandle(
                PhoneUtils.makePstnPhoneAccountHandle(account.name));

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(Integer.toString(subId))
                .build();
        NetworkCallback networkCallback = new OmtpVvmNetworkRequestCallback(this, account, action);
        getConnectivityManager().requestNetwork(
                networkRequest, networkCallback, NETWORK_REQUEST_TIMEOUT_MILLIS);
    }

    private class OmtpVvmNetworkRequestCallback extends ConnectivityManager.NetworkCallback {
        Context mContext;
        Account mAccount;
        String mAction;

        public OmtpVvmNetworkRequestCallback(Context context, Account account, String action) {
            mContext = context;
            mAccount = account;
            mAction = action;
        }

        @Override
        public void onAvailable(final Network network) {
            ImapHelper imapHelper = new ImapHelper(mContext, mAccount, network);
            if (SYNC_FULL_SYNC.equals(mAction) || SYNC_UPLOAD_ONLY.equals(mAction)) {
                upload(imapHelper);
            }
            if (SYNC_FULL_SYNC.equals(mAction) || SYNC_DOWNLOAD_ONLY.equals(mAction)) {
                download(imapHelper);
            }
            releaseNetwork();
        }

        @Override
        public void onLost(Network network) {
            releaseNetwork();
        }

        @Override
        public void onUnavailable() {
            releaseNetwork();
        }

        private void releaseNetwork() {
            getConnectivityManager().unregisterNetworkCallback(this);
        }
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) this.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    private void upload(ImapHelper imapHelper) {
        List<Voicemail> readVoicemails = mQueryHelper.getReadVoicemails();
        List<Voicemail> deletedVoicemails = mQueryHelper.getDeletedVoicemails();

        if (deletedVoicemails != null &&
                imapHelper.markMessagesAsDeleted(deletedVoicemails)) {
            // We want to delete selectively instead of all the voicemails for this provider
            // in case the state changed since the IMAP query was completed.
            mQueryHelper.deleteFromDatabase(deletedVoicemails);
        }

        if (readVoicemails != null && imapHelper.markMessagesAsRead(readVoicemails)) {
            mQueryHelper.markReadInDatabase(readVoicemails);
        }
    }

    private void download(ImapHelper imapHelper) {
        List<Voicemail> serverVoicemails = imapHelper.fetchAllVoicemails();
        List<Voicemail> localVoicemails = mQueryHelper.getAllVoicemails();

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
                mQueryHelper.deleteFromDatabase(localVoicemail);
            } else {
                if (remoteVoicemail.isRead() != localVoicemail.isRead()) {
                    mQueryHelper.markReadInDatabase(localVoicemail);
                }
            }
        }

        // The leftover messages are messages that exist on the server but not locally.
        for (Voicemail remoteVoicemail : remoteMap.values()) {
            VoicemailContract.Voicemails.insert(this, remoteVoicemail);
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
