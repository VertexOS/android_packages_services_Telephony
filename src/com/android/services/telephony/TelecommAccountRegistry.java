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

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;

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

        AccountEntry(Phone phone, boolean isDummy) {
            mPhone = phone;
            mAccount = registerPstnPhoneAccount(isDummy);
            Log.d(this, "Registered phoneAccount: %s with handle: %s",
                    mAccount, mAccount.getAccountHandle());
            mIncomingCallNotifier = new PstnIncomingCallNotifier((PhoneProxy) mPhone);
        }

        /**
         * Registers the specified account with Telecomm as a PhoneAccountHandle.
         */
        private PhoneAccount registerPstnPhoneAccount(boolean isDummyAccount) {
            TelephonyManager telephonyManager = TelephonyManager.from(mContext);
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle = isDummyAccount ?
                    makePstnPhoneAccountHandleWithPrefix(mPhone, dummyPrefix) :
                    makePstnPhoneAccountHandle(mPhone);

            // Populate the phone account data.
            long subId = mPhone.getSubId();
            int slotId = mPhone.getPhoneId() + 1;
            String line1Number = telephonyManager.getLine1Number(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            PhoneAccount account = new PhoneAccount(
                    phoneAccountHandle,
                    Uri.fromParts(TEL_SCHEME, line1Number, null),
                    mPhone.getPhoneSubInfo().getLine1Number(),
                    PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                            PhoneAccount.CAPABILITY_CALL_PROVIDER,
                    com.android.phone.R.mipmap.ic_launcher_phone,
                    dummyPrefix + "SIM " + slotId,
                    dummyPrefix + "SIM card in slot " + slotId,
                    true /* supportsVideoCalling */);

            // Register with Telecomm and put into the account entry.
            mTelecommManager.registerPhoneAccount(account);
            return account;
        }
    }

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
    void setup() {
        // Initialize the PhoneFactory, since the PhoneApp may not yet have been set up
        PhoneFactory.makeDefaultPhones(mContext);
        // Before we do anything, we need to clear whatever entries we registered at boot.
        mTelecommManager.clearAccounts(mContext.getPackageName());

        // Go through SIM-based phones and register ourselves
        Phone[] phones = PhoneFactory.getPhones();
        Log.d(this, "Found %d phones.  Attempting to register.", phones.length);
        for (Phone phone : phones) {
            long subscriptionId = phone.getSubId();
            Log.d(this, "Phone with subscription id %d", subscriptionId);
            if (subscriptionId >= 0) {
                mAccounts.add(new AccountEntry(phone, false /* isDummy */));
            }
        }

        // Add a fake account entry.
        if (phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
            mAccounts.add(new AccountEntry(phones[0], true /* isDummy */));
        }

        // TODO: Add SIP accounts.
    }

    static PhoneAccountHandle makePstnPhoneAccountHandle(Phone phone) {
        return makePstnPhoneAccountHandleWithPrefix(phone, "");
    }

    private static PhoneAccountHandle makePstnPhoneAccountHandleWithPrefix(
            Phone phone, String prefix) {
        ComponentName pstnConnectionServiceName =
                new ComponentName(phone.getContext(), TelephonyConnectionService.class);
        return new PhoneAccountHandle(
                pstnConnectionServiceName, prefix + String.valueOf(phone.getSubId()));
    }
}
