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
import android.telecomm.PhoneAccountMetadata;
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
        private final PhoneAccountMetadata mMetadata;
        private final PstnIncomingCallNotifier mIncomingCallNotifier;

        AccountEntry(Phone phone, boolean isDummy) {
            mPhone = phone;
            mMetadata = registerPstnPhoneAccount(isDummy);
            mIncomingCallNotifier = new PstnIncomingCallNotifier((PhoneProxy) mPhone);
        }

        /**
         * Registers the specified account with Telecomm as a PhoneAccount.
         */
        private PhoneAccountMetadata registerPstnPhoneAccount(boolean isDummyAccount) {
            TelephonyManager telephonyManager = TelephonyManager.from(mContext);
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccount phoneAccount = isDummyAccount ?
                    makePstnPhoneAccountWithPrefix(mPhone, dummyPrefix) :
                    makePstnPhoneAccount(mPhone);

            // Populate the phone account data.
            long subId = mPhone.getSubId();
            int slotId = mPhone.getPhoneId() + 1;
            PhoneAccountMetadata metadata = new PhoneAccountMetadata(
                    phoneAccount,
                    Uri.fromParts(TEL_SCHEME, telephonyManager.getLine1Number(subId), null),
                    mPhone.getPhoneSubInfo().getLine1Number(),
                    PhoneAccountMetadata.CAPABILITY_SIM_SUBSCRIPTION |
                            PhoneAccountMetadata.CAPABILITY_CALL_PROVIDER,
                    com.android.phone.R.mipmap.ic_launcher_phone,
                    dummyPrefix + "SIM " + slotId,
                    dummyPrefix + "SIM card in slot " + slotId,
                    true /* supportsVideoCalling */);

            // Register with Telecomm and put into the account entry.
            mTelecommManager.registerPhoneAccount(metadata);
            return metadata;
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

    static PhoneAccount makePstnPhoneAccount(Phone phone) {
        return makePstnPhoneAccountWithPrefix(phone, "");
    }

    private static PhoneAccount makePstnPhoneAccountWithPrefix(Phone phone, String prefix) {
        ComponentName pstnConnectionServiceName =
                new ComponentName(phone.getContext(), TelephonyConnectionService.class);
        return new PhoneAccount(
                pstnConnectionServiceName, prefix + String.valueOf(phone.getSubId()));
    }
}
