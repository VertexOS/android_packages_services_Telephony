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
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.R;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Owns all data we have registered with Telecomm including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
final class TelecommAccountRegistry {
    private static final boolean DBG = false; /* STOP SHIP if true */

    private final static int[] phoneAccountIcons = {
            R.drawable.ic_multi_sim,
            R.drawable.ic_multi_sim1,
            R.drawable.ic_multi_sim2,
            R.drawable.ic_multi_sim3,
            R.drawable.ic_multi_sim4
    };

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
            mIncomingCallNotifier.teardown();
        }

        /**
         * Registers the specified account with Telecomm as a PhoneAccountHandle.
         */
        private PhoneAccount registerPstnPhoneAccount(boolean isEmergency, boolean isDummyAccount) {
            TelephonyManager telephonyManager = TelephonyManager.from(mContext);
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    makePstnPhoneAccountHandleWithPrefix(mPhone, dummyPrefix, isEmergency);

            // Populate the phone account data.
            long subId = mPhone.getSubId();
            int slotId = mPhone.getPhoneId() + 1;
            String line1Number = telephonyManager.getLine1NumberForSubscriber(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            String subNumber = mPhone.getPhoneSubInfo().getLine1Number();
            if (subNumber == null) {
                subNumber = "";
            }

            String subDisplayName = null;
            SubInfoRecord record = SubscriptionManager.getSubInfoForSubscriber(subId);
            if (record != null) {
                subDisplayName = record.displayName;
            }

            if (TextUtils.isEmpty(subDisplayName)) {
                // Either the sub record is not there or it has an empty display name.
                Log.w(this, "Could not get a display name for subid: %d", subId);
                subDisplayName = mContext.getResources().getString(
                        R.string.sim_description_default, slotId);
            }

            // The label is user-visible so let's use the display name that the user may
            // have set in Settings->Sim cards.
            String label = isEmergency ?
                    mContext.getResources().getString(R.string.sim_label_emergency_calls) :
                    dummyPrefix + subDisplayName;
            String description = isEmergency ?
                    mContext.getResources().getString(R.string.sim_description_emergency_calls) :
                    dummyPrefix + mContext.getResources().getString(
                            R.string.sim_description_default, slotId);

            // By default all SIM phone accounts can place emergency calls.
            int capabilities = PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS;

            // Indicate the emergency calling PhoneAccount is ALWAYS enabled.  This capability is
            // important to ensure the emergency-only PhoneAccount cannot be disabled.
            if (isEmergency) {
                capabilities |= PhoneAccount.CAPABILITY_ALWAYS_ENABLED;
            }

            PhoneAccount account = PhoneAccount.builder(phoneAccountHandle, label)
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, line1Number, null))
                    .setSubscriptionAddress(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setCapabilities(capabilities)
                    .setIconResId(getPhoneAccountIcon(slotId))
                    .setShortDescription(description)
                    .setSupportedUriSchemes(Arrays.asList(
                            PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
                    .setEnabled(true)
                    .build();

            // Register with Telecomm and put into the account entry.
            mTelecommManager.registerPhoneAccount(account);
            return account;
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mAccount != null ? mAccount.getAccountHandle() : null;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean rebuildAccounts = false;
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                int status = intent.getIntExtra(
                        SubscriptionManager.INTENT_KEY_DETECT_STATUS,
                        SubscriptionManager.EXTRA_VALUE_NOCHANGE);
                Log.i(this, "SUBINFO_RECORD_UPDATED : %d.", status);
                // Anytime the SIM state changes...rerun the setup
                // We rely on this notification even when the status is EXTRA_VALUE_NOCHANGE,
                // so we explicitly do not check for that here.
                rebuildAccounts = true;
            } else if (TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE.equals(action)) {
                String columnName = intent.getStringExtra(TelephonyIntents.EXTRA_COLUMN_NAME);
                String stringContent = intent.getStringExtra(TelephonyIntents.EXTRA_STRING_CONTENT);
                Log.v(this, "SUBINFO_CONTENT_CHANGE: Column: %s Content: %s",
                        columnName, stringContent);
                rebuildAccounts = true;
            }
            if (rebuildAccounts) {
                tearDownAccounts();
                setupAccounts();
            }
        }
    };

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
     * Sets up all the phone accounts for SIMs on first boot.
     */
    void setupOnBoot() {
        // We need to register for both types of intents if we want to see added/removed Subs
        // along with changes to a given Sub.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        mContext.registerReceiver(mReceiver, intentFilter);
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

    /**
     * Determines if the list of {@link AccountEntry}(s) contains an {@link AccountEntry} with a
     * specified {@link PhoneAccountHandle}.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if an entry exists.
     */
    private boolean hasAccountEntryForPhoneAccount(PhoneAccountHandle handle) {
        for (AccountEntry entry : mAccounts) {
            if (entry.getPhoneAccountHandle().equals(handle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Un-registers any {@link PhoneAccount}s which are no longer present in the list
     * {@code AccountEntry}(s).
     */
    private void cleanupPhoneAccounts() {
        ComponentName telephonyComponentName =
                new ComponentName(mContext, TelephonyConnectionService.class);

        List<PhoneAccountHandle> accountHandles = mTelecommManager.getAllPhoneAccountHandles();
        for (PhoneAccountHandle handle : accountHandles) {
            if (telephonyComponentName.equals(handle.getComponentName()) &&
                    !hasAccountEntryForPhoneAccount(handle)) {
                Log.d(this, "Unregistering phone account %s.", handle);
                mTelecommManager.unregisterPhoneAccount(handle);
            }
        }
    }

    private void setupAccounts() {
        // Go through SIM-based phones and register ourselves -- registering an existing account
        // will cause the existing entry to be replaced.
        Phone[] phones = PhoneFactory.getPhones();
        Log.d(this, "Found %d phones.  Attempting to register.", phones.length);
        for (Phone phone : phones) {
            long subscriptionId = phone.getSubId();
            Log.d(this, "Phone with subscription id %d", subscriptionId);
            if (subscriptionId >= 0) {
                mAccounts.add(new AccountEntry(phone, false /* emergency */, false /* isDummy */));
            }
        }

        // If we did not list ANY accounts, we need to provide a "default" SIM account
        // for emergency numbers since no actual SIM is needed for dialing emergency
        // numbers but a phone account is.
        if (mAccounts.isEmpty()) {
            mAccounts.add(new AccountEntry(PhoneFactory.getDefaultPhone(), true /* emergency */,
                    false /* isDummy */));
        }

        // Add a fake account entry.
        if ( DBG && phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
            mAccounts.add(new AccountEntry(phones[0], false /* emergency */, true /* isDummy */));
        }

        // Clean up any PhoneAccounts that are no longer relevant
        cleanupPhoneAccounts();
    }

    private int getPhoneAccountIcon(int index) {
        if (index < TelecommAccountRegistry.phoneAccountIcons.length) {
            return TelecommAccountRegistry.phoneAccountIcons[index];
        }
        // default blank icon
        return TelecommAccountRegistry.phoneAccountIcons[0];
    }

    private void tearDownAccounts() {
        for (AccountEntry entry : mAccounts) {
            entry.teardown();
        }
        mAccounts.clear();
    }
}
