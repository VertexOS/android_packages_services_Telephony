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

package com.android.services.telephony;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;

import java.util.LinkedList;
import java.util.List;

/**
 * Owns all data we have registered with Telecomm including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
final class TelecommAccountRegistry {

    private final class AccountEntry {
        private final Phone mPhone;
        private final PhoneAccount mAccount;
        private final PstnIncomingCallNotifier mIncomingCallNotifier;

        AccountEntry(Phone phone, boolean isEmergency, boolean isDummy) {
            mPhone = phone;
            mAccount = registerPstnPhoneAccount(isEmergency, isDummy);
            Log.d(this, "Registered phoneAccount: %s with handle: %s",
                    mAccount, mAccount.getAccountHandle());
            mIncomingCallNotifier = new PstnIncomingCallNotifier((PhoneProxy) mPhone);
        }

        void teardown() {
            mTelecommManager.unregisterPhoneAccount(mAccount.getAccountHandle());
            mIncomingCallNotifier.teardown();
        }

        /**
         * Registers the specified account with Telecomm as a PhoneAccountHandle.
         */
        private PhoneAccount registerPstnPhoneAccount(
                boolean isEmergency, boolean isDummyAccount) {
            TelephonyManager telephonyManager = TelephonyManager.from(mContext);
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    makePstnPhoneAccountHandleWithPrefix(mPhone, dummyPrefix, isEmergency);

            // Populate the phone account data.
            long subId = mPhone.getSubId();
            int slotId = mPhone.getPhoneId() + 1;
            String line1Number = telephonyManager.getLine1Number(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            String subNumber = mPhone.getPhoneSubInfo().getLine1Number();
            if (subNumber == null) {
                subNumber = "";
            }
            String label = isEmergency
                    ? "Emergency calls"
                    : dummyPrefix + "SIM " + slotId;
            String description = isEmergency
                    ? "Emergency calling only"
                    : dummyPrefix + "SIM card in slot " + slotId;
            PhoneAccount account = new PhoneAccount(
                    phoneAccountHandle,
                    Uri.fromParts(TEL_SCHEME, line1Number, null),
                    subNumber,
                    PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                            PhoneAccount.CAPABILITY_CALL_PROVIDER,
                    com.android.phone.R.mipmap.ic_launcher_phone,
                    label,
                    description);

            // Register with Telecomm and put into the account entry.
            mTelecommManager.registerPhoneAccount(account);
            return account;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                int status = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                        SubscriptionManager.EXTRA_VALUE_NOCHANGE);
                Log.i(this, "SUBINFO_RECORD_UPDATED : %d.", status);
                // Anytime the SIM state changes...rerun the setup
                // We rely on this notification even when the status is EXTRA_VALUE_NOCHANGE,
                // so we explicitly do not check for that here.
                tearDownAccounts();
                setupAccounts();
            }
        }
    };

    private static final String TEL_SCHEME = "tel";
    private static TelecommAccountRegistry sInstance;
    private final Context mContext;
    private final TelecommManager mTelecommManager;
    private List<AccountEntry> mAccounts = new LinkedList<AccountEntry>();

    TelecommAccountRegistry(Context context) {
        mContext = context;
        mTelecommManager = TelecommManager.from(context);
    }

    static synchronized final TelecommAccountRegistry getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TelecommAccountRegistry(context);
        }
        return sInstance;
    }

    /**
     * Sets up all the phone accounts for SIM and SIP accounts on first boot.
     */
    void setupOnBoot() {
        IntentFilter intentFilter =
            new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        mContext.registerReceiver(mReceiver, intentFilter);

        setupAccounts();
    }

    static PhoneAccountHandle makePstnPhoneAccountHandle(Phone phone) {
        return makePstnPhoneAccountHandleWithPrefix(phone, "", false);
    }

    private static PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(
            Phone phone, String prefix, boolean isEmergency) {
        ComponentName pstnConnectionServiceName =
                new ComponentName(phone.getContext(), TelephonyConnectionService.class);
        // TODO: Should use some sort of special hidden flag to decorate this account as
        // an emergency-only account
        String id = isEmergency ? "E" : prefix + String.valueOf(phone.getSubId());
        return new PhoneAccountHandle(pstnConnectionServiceName, id);
    }

    private void setupAccounts() {
        // Before we do anything, we need to clear whatever entries we registered at boot.
        mTelecommManager.clearAccounts(mContext.getPackageName());

        // Go through SIM-based phones and register ourselves
        Phone[] phones = PhoneFactory.getPhones();
        Log.d(this, "Found %d phones.  Attempting to register.", phones.length);
        for (Phone phone : phones) {
            long subscriptionId = phone.getSubId();
            Log.d(this, "Phone with subscription id %d", subscriptionId);
            if (subscriptionId >= 0) {
                mAccounts.add(new AccountEntry(phone, false, false /* isDummy */));
            }
        }

        // If we did not list ANY accounts, we need to provide a "default" SIM account
        // for emergency numbers since no actual SIM is needed for dialing emergency
        // numbers but a phone account is.
        if (mAccounts.isEmpty()) {
            mAccounts.add(new AccountEntry(
                    PhoneFactory.getDefaultPhone(), true /*emergency*/, false /*isDummy*/));
        }

        // Add a fake account entry.
        if (phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
            mAccounts.add(new AccountEntry(phones[0], false, true /* isDummy */));
        }

        // TODO: Add SIP accounts.
    }

    private void tearDownAccounts() {
        for (AccountEntry entry : mAccounts) {
            entry.teardown();
        }
        mAccounts.clear();
    }
}
