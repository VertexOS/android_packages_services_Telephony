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
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.phone.PhoneUtils;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.sms.StatusMessage;

import java.util.HashMap;
import java.util.Map;

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

    private Context mContext;
    private SubscriptionManager mSubscriptionManager;
    private AccountManager mAccountManager;
    private TelephonyManager mTelephonyManager;
    // Each account is associated with a phone state listener for updates to whether the device
    // is able to sync.
    private Map<Account, PhoneStateListener> mPhoneStateListenerMap;

    /**
     * Private constructor. Instance should only be acquired through getInstance().
     */
    private OmtpVvmSyncAccountManager() {}

    public static OmtpVvmSyncAccountManager getInstance(Context context) {
        sInstance.setup(context);
        return sInstance;
    }

    /**
     * Set the context and system services so they do not need to be retrieved every time.
     * @param context The context to get the account manager and subscription manager for.
     */
    private void setup(Context context) {
        if (mContext == null) {
            mContext = context;
            mSubscriptionManager = SubscriptionManager.from(context);
            mAccountManager = AccountManager.get(context);
            mTelephonyManager = (TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);
            mPhoneStateListenerMap = new HashMap<Account, PhoneStateListener>();
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
     * When a voicemail source is removed, we don't always know which one was removed. Check the
     * list of registered sync accounts against the active subscriptions list and remove the
     * inactive accounts.
     */
    public void removeInactiveAccounts() {
        Account[] registeredAccounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);
        for (int i = 0; i < registeredAccounts.length; i++) {
            PhoneAccountHandle handle = PhoneUtils.makePstnPhoneAccountHandle(
                    registeredAccounts[i].name);
            if (!PhoneUtils.isPhoneAccountActive(mSubscriptionManager, handle)) {
                mAccountManager.removeAccount(registeredAccounts[i], null, null, null);
                VoicemailContract.Status.setStatus(mContext, handle,
                        VoicemailContract.Status.CONFIGURATION_STATE_NOT_CONFIGURED,
                        VoicemailContract.Status.DATA_CHANNEL_STATE_NO_CONNECTION,
                        VoicemailContract.Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);

                removePhoneStateListener(registeredAccounts[i]);
            }
        }
    }

    public void addPhoneStateListener(Account account) {
        if (!mPhoneStateListenerMap.containsKey(account)) {
            VvmPhoneStateListener phoneStateListener = new VvmPhoneStateListener(mContext,
                    PhoneUtils.makePstnPhoneAccountHandle(account.name));
            mPhoneStateListenerMap.put(account, phoneStateListener);
            mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    public void removePhoneStateListener(Account account) {
        PhoneStateListener phoneStateListener =
                mPhoneStateListenerMap.remove(account);
        mTelephonyManager.listen(phoneStateListener, 0);
    }

    public Account[] getOmtpAccounts() {
        return mAccountManager.getAccountsByType(ACCOUNT_TYPE);
    }

    /**
     * Check if a certain account is registered.
     *
     * @param account The account to look for.
     * @return {@code true} if the account is in the list of registered OMTP voicemail sync
     * accounts. {@code false} otherwise.
     */
    public boolean isAccountRegistered(Account account) {
        Account[] accounts = getOmtpAccounts();
        for (int i = 0; i < accounts.length; i++) {
            if (account.equals(accounts[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the IMAP credentials as extra fields in the account.
     *
     * @param account The account to add credentials to.
     * @param message The status message to extract the fields from.
     */
    public void setAccountCredentialsFromStatusMessage(Account account, StatusMessage message) {
        mAccountManager.setUserData(account, OmtpConstants.IMAP_PORT, message.getImapPort());
        mAccountManager.setUserData(account, OmtpConstants.SERVER_ADDRESS,
                message.getServerAddress());
        mAccountManager.setUserData(account, OmtpConstants.IMAP_USER_NAME,
                message.getImapUserName());
        mAccountManager.setUserData(account, OmtpConstants.IMAP_PASSWORD,
                message.getImapPassword());
    }
}
