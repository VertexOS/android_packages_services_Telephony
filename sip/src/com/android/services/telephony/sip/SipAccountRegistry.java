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

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
import android.util.Log;

import com.android.phone.R;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the {@link PhoneAccount} entries for SIP calling.
 */
final class SipAccountRegistry {
    private final class AccountEntry {
        private final SipProfile mProfile;

        AccountEntry(SipProfile profile) {
            mProfile = profile;
        }

        SipProfile getProfile() {
            return mProfile;
        }

        boolean register(SipManager sipManager, Context context) {
            if (VERBOSE) log("register, profile: " + mProfile);
            try {
                sipManager.open(
                        mProfile,
                        SipUtil.createIncomingCallPendingIntent(context, mProfile.getUriString()),
                        null);
                TelecommManager.from(context).registerPhoneAccount(createPhoneAccount(context));
                return true;
            } catch (SipException e) {
                log("register, profile: " + mProfile.getProfileName() +
                        ", exception: " + e);
            }
            return false;
        }

        private PhoneAccount createPhoneAccount(Context context) {
            PhoneAccountHandle accountHandle =
                    SipUtil.createAccountHandle(context, mProfile.getUriString());
            return PhoneAccount.builder()
                    .withAccountHandle(accountHandle)
                    .withCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .withHandle(Uri.parse(mProfile.getUriString()))
                    .withLabel(mProfile.getDisplayName())
                    .withShortDescription(mProfile.getDisplayName())
                    .withIconResId(R.drawable.ic_dialer_sip_black_24dp)
                    .build();
        }
    }

    private static final String PREFIX = "[SipAccountRegistry] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */
    private static final SipAccountRegistry INSTANCE = new SipAccountRegistry();

    private final List<AccountEntry> mAccounts = new CopyOnWriteArrayList<>();

    private SipAccountRegistry() {}

    static SipAccountRegistry getInstance() {
        return INSTANCE;
    }

    void setup(Context context) {
        clearCurrentSipAccounts(context);
        registerProfiles(context, null);
    }

    void addPhone(Context context, String sipUri) {
        registerProfiles(context, sipUri);
    }

    void removePhone(Context context, String sipUri) {
        for (AccountEntry entry : mAccounts) {
            if (Objects.equals(sipUri, entry.getProfile().getUriString())) {
                TelecommManager.from(context).unregisterPhoneAccount(
                        SipUtil.createAccountHandle(context, sipUri));
                mAccounts.remove(entry);
                break;
            }
        }
    }

    /**
     * Loops through all SIP accounts from the SIP database, starts each service and registers
     * each with the telecomm framework. If a specific sipUri is specified, this will only register
     * the associated SIP account.
     *
     * @param context The context.
     * @param sipUri A specific SIP uri to register.
     */
    private void registerProfiles(final Context context, final String sipUri) {
        if (VERBOSE) log("registerProfiles, start auto registration");
        final SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                SipManager sipManager = SipManager.newInstance(context);
                SipProfileDb profileDb = new SipProfileDb(context);
                String primaryProfile = sipSharedPreferences.getPrimaryAccount();
                List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();

                for (SipProfile profile : sipProfileList) {
                    boolean isPrimaryProfile = profile.getUriString().equals(primaryProfile);
                    if (profile.getAutoRegistration() || isPrimaryProfile) {
                        if (sipUri == null || Objects.equals(sipUri, profile.getUriString())) {
                            registerAccountForProfile(profile, sipManager, context);
                        }
                    }
                }
            }}
        ).start();
    }

    private void registerAccountForProfile(
            SipProfile profile, SipManager sipManager, Context context) {
        AccountEntry entry = new AccountEntry(profile);
        if (entry.register(sipManager, context)) {
            mAccounts.add(entry);
        }
    }

    private void clearCurrentSipAccounts(Context context) {
        ComponentName sipComponentName = new ComponentName(context, SipConnectionService.class);
        TelecommManager telecommManager = TelecommManager.from(context);
        List<PhoneAccountHandle> accountHandles = telecommManager.getEnabledPhoneAccounts();
        for (PhoneAccountHandle handle : accountHandles) {
            if (sipComponentName.equals(handle.getComponentName())) {
                telecommManager.unregisterPhoneAccount(handle);
            }
        }
    }

    private void log(String message) {
        Log.d(SipUtil.LOG_TAG, PREFIX + message);
    }
}
