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
package com.android.phone.vvm.omtp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

/**
 * A singleton class designed to assist in OMTP visual voicemail sync behavior.
 */
public class OmtpVvmSyncAccountManager {
    public static final String TAG = "OmtpVvmSyncAccountManager";
    // Constants
    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "com.android.voicemail";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "com.android.phone.vvm.omtp";

    private static OmtpVvmSyncAccountManager sInstance = new OmtpVvmSyncAccountManager();

    private AccountManager mAccountManager;

    /**
     * Private constructor. Instance should only be acquired through getInstance().
     */
    private OmtpVvmSyncAccountManager() {}

    public static OmtpVvmSyncAccountManager getInstance(Context context) {
        sInstance.setAccountManager(context);
        return sInstance;
    }

    /**
     * Set the account manager so it does not need to be retrieved every time.
     * @param context The context to get the account manager for.
     */
    private void setAccountManager(Context context) {
        if (mAccountManager == null) {
            mAccountManager = AccountManager.get(context);
        }
    }

    /**
     * Register a sync account. There should be a one to one mapping of sync account to voicemail
     * source. These sync accounts primarily service the purpose of keeping track of how many OMTP
     * voicemail sources are active and which phone accounts they correspond to.
     *
     * @param account The account to register
     */
    public void createSyncAccount(Account account) {
        // Add the account and account type, no password or user data
        if (mAccountManager.addAccountExplicitly(account, null, null)) {
             ContentResolver.setIsSyncable(account, AUTHORITY, 1);
             ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
        } else {
            Log.w(TAG, "Attempted to re-register existing account.");
        }
    }

    /**
     * Check if a certain account is registered.
     *
     * @param account The account to look for.
     * @return {@code true} if the account is in the list of registered OMTP voicemail sync
     * accounts. {@code false} otherwise.
     */
    public boolean isAccountRegistered(Account account) {
        Account[] accounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);
        for (int i = 0; i < accounts.length; i++) {
            if (account.equals(accounts[i])) {
                return true;
            }
        }
        return false;
    }
}
