/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.content.Context;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the {@link PhoneAccount} entries for SIP calling.
 */
public final class SipAccountRegistry {
    private final class AccountEntry {
        private final SipProfile mProfile;

        AccountEntry(SipProfile profile) {
            mProfile = profile;
        }

        SipProfile getProfile() {
            return mProfile;
        }

        /**
         * Starts the SIP service associated with the SIP profile.
         *
         * @param sipManager The SIP manager.
         * @param context The context.
         * @param isReceivingCalls {@code True} if the sip service is being started to make and
         *          receive calls.  {@code False} if the sip service is being started only for
         *          outgoing calls.
         * @return {@code True} if the service started successfully.
         */
        boolean startSipService(SipManager sipManager, Context context, boolean isReceivingCalls) {
            if (VERBOSE) log("startSipService, profile: " + mProfile);
            try {
                // Stop the Sip service for the profile if it is already running.  This is important
                // if we are changing the state of the "receive calls" option.
                sipManager.close(mProfile.getUriString());

                // Start the sip service for the profile.
                if (isReceivingCalls) {
                    sipManager.open(
                            mProfile,
                            SipUtil.createIncomingCallPendingIntent(context,
                                    mProfile.getUriString()),
                            null);
                } else {
                    sipManager.open(mProfile);
                }
                return true;
            } catch (SipException e) {
                log("startSipService, profile: " + mProfile.getProfileName() +
                        ", exception: " + e);
            }
            return false;
        }

        /**
         * Stops the SIP service associated with the SIP profile.  The {@code SipAccountRegistry} is
         * informed when the service has been stopped via an intent which triggers
         * {@link SipAccountRegistry#removeSipProfile(String)}.
         *
         * @param sipManager The SIP manager.
         * @return {@code True} if stop was successful.
         */
        boolean stopSipService(SipManager sipManager) {
            try {
                sipManager.close(mProfile.getUriString());
                return true;
            } catch (Exception e) {
                log("stopSipService, stop failed for profile: " + mProfile.getUriString() +
                        ", exception: " + e);
            }
            return false;
        }
    }

    private static final String PREFIX = "[SipAccountRegistry] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */
    private static final SipAccountRegistry INSTANCE = new SipAccountRegistry();

    private final List<AccountEntry> mAccounts = new CopyOnWriteArrayList<>();

    private SipAccountRegistry() {}

    public static SipAccountRegistry getInstance() {
        return INSTANCE;
    }

    void setup(Context context) {
        startSipProfilesAsync((Context) context, (String) null);
    }

    /**
     * Starts the SIP service for the specified SIP profile and ensures it has a valid registered
     * {@link PhoneAccount}.
     *
     * @param context The context.
     * @param sipUri The Uri of the {@link SipProfile} to start, or {@code null} for all.
     */
    void startSipService(Context context, String sipUri) {
        startSipProfilesAsync((Context) context, (String) sipUri);
    }

    /**
     * Removes a {@link SipProfile} from the account registry.  Does not stop/close the associated
     * SIP service (this method is invoked via an intent from the SipService once a profile has
     * been stopped/closed).
     *
     * @param sipUri The Uri of the {@link SipProfile} to remove from the registry.
     */
    void removeSipProfile(String sipUri) {
        AccountEntry accountEntry = getAccountEntry(sipUri);

        if (accountEntry != null) {
            mAccounts.remove(accountEntry);
        }
    }

    /**
     * Stops a SIP profile and un-registers its associated {@link android.telecom.PhoneAccount}.
     * Called after a SIP profile is deleted.  The {@link AccountEntry} will be removed when the
     * service has been stopped.  The {@code SipService} fires the {@code ACTION_SIP_REMOVE_PHONE}
     * intent, which triggers {@link SipAccountRegistry#removeSipProfile(String)} to perform the
     * removal.
     *
     * @param context The context.
     * @param sipUri The {@code Uri} of the sip profile.
     */
    void stopSipService(Context context, String sipUri) {
        // Stop the sip service for the profile.
        AccountEntry accountEntry = getAccountEntry(sipUri);
        if (accountEntry != null ) {
            SipManager sipManager = SipManager.newInstance(context);
            accountEntry.stopSipService(sipManager);
        }

        // Un-register its PhoneAccount.
        PhoneAccountHandle handle = SipUtil.createAccountHandle(context, sipUri);
        TelecomManager.from(context).unregisterPhoneAccount(handle);
    }

    /**
     * Causes the SIP service to be restarted for all {@link SipProfile}s.  For example, if the user
     * toggles the "receive calls" option for SIP, this method handles restarting the SIP services
     * in the new mode.
     *
     * @param context The context.
     */
    public void restartSipService(Context context) {
        startSipProfiles(context, null);
    }

    /**
     * Performs an asynchronous call to
     * {@link SipAccountRegistry#startSipProfiles(android.content.Context, String)}, starting the
     * specified SIP profile and registering its {@link android.telecom.PhoneAccount}.
     *
     * @param context The context.
     * @param sipUri A specific SIP uri to start.
     */
    private void startSipProfilesAsync(final Context context, final String sipUri) {
        if (VERBOSE) log("startSipProfiles, start auto registration");

        new Thread(new Runnable() {
            @Override
            public void run() {
                startSipProfiles(context, sipUri);
            }}
        ).start();
    }

    /**
     * Loops through all SIP accounts from the SIP database, starts each service and registers
     * each with the telecom framework. If a specific sipUri is specified, this will only register
     * the associated SIP account.
     *
     * @param context The context.
     * @param sipUri A specific SIP uri to start, or {@code null} to start all.
     */
    private void startSipProfiles(Context context, String sipUri) {
        final SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(context);
        boolean isReceivingCalls = sipSharedPreferences.isReceivingCallsEnabled();
        String primaryProfile = sipSharedPreferences.getPrimaryAccount();
        TelecomManager telecomManager = TelecomManager.from(context);
        SipManager sipManager = SipManager.newInstance(context);
        SipProfileDb profileDb = new SipProfileDb(context);
        List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();

        for (SipProfile profile : sipProfileList) {
            // Register a PhoneAccount for the profile and optionally enable the primary
            // profile.
            if (sipUri == null || Objects.equals(sipUri, profile.getUriString())) {
                PhoneAccount phoneAccount = SipUtil.createPhoneAccount(context, profile);
                telecomManager.registerPhoneAccount(phoneAccount);
            }

            if (sipUri == null || Objects.equals(sipUri, profile.getUriString())) {
                startSipServiceForProfile(profile, sipManager, context, isReceivingCalls);
            }
        }

        if (primaryProfile != null) {
            // Remove the primary account shared preference, ensuring the migration does not
            // occur again in the future.
            sipSharedPreferences.cleanupPrimaryAccountSetting();
        }
    }

    /**
     * Starts the SIP service for a sip profile and saves a new {@code AccountEntry} in the
     * registry.
     *
     * @param profile The {@link SipProfile} to start.
     * @param sipManager The SIP manager.
     * @param context The context.
     * @param isReceivingCalls {@code True} if the profile should be started such that it can
     *      receive incoming calls.
     */
    private void startSipServiceForProfile(SipProfile profile, SipManager sipManager,
            Context context, boolean isReceivingCalls) {
        removeSipProfile(profile.getUriString());

        AccountEntry entry = new AccountEntry(profile);
        if (entry.startSipService(sipManager, context, isReceivingCalls)) {
            mAccounts.add(entry);
        }
    }

    /**
     * Retrieves the {@link AccountEntry} from the registry with the specified Uri.
     *
     * @param sipUri The Uri of the profile to retrieve.
     * @return The {@link AccountEntry}, or {@code null} is it was not found.
     */
    private AccountEntry getAccountEntry(String sipUri) {
        for (AccountEntry entry : mAccounts) {
            if (Objects.equals(sipUri, entry.getProfile().getUriString())) {
                return entry;
            }
        }
        return null;
    }

    private void log(String message) {
        Log.d(SipUtil.LOG_TAG, PREFIX + message);
    }
}
