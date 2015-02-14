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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Owns all data we have registered with Telecom including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
final class TelecomAccountRegistry {
    private static final boolean DBG = false; /* STOP SHIP if true */

    // This icon is the one that is used when the Slot ID that we have for a particular SIM
    // is not supported, i.e. SubscriptionManager.INVALID_SLOT_ID or the 5th SIM in a phone.
    private final static int defaultPhoneAccountIcon =  R.drawable.ic_multi_sim;

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
         * Registers the specified account with Telecom as a PhoneAccountHandle.
         */
        private PhoneAccount registerPstnPhoneAccount(boolean isEmergency, boolean isDummyAccount) {
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                            mPhone, dummyPrefix, isEmergency);

            // Populate the phone account data.
            int subId = mPhone.getSubId();
            int color = PhoneAccount.NO_HIGHLIGHT_COLOR;
            int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
            String line1Number = mTelephonyManager.getLine1NumberForSubscriber(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            String subNumber = mPhone.getPhoneSubInfo().getLine1Number();
            if (subNumber == null) {
                subNumber = "";
            }

            String label;
            String description;
            Bitmap iconBitmap = null;

            if (isEmergency) {
                label = mContext.getResources().getString(R.string.sim_label_emergency_calls);
                description =
                        mContext.getResources().getString(R.string.sim_description_emergency_calls);
            } else if (mTelephonyManager.getPhoneCount() == 1) {
                // For single-SIM devices, we show the label and description as whatever the name of
                // the network is.
                description = label = mTelephonyManager.getNetworkOperatorName();
            } else {
                CharSequence subDisplayName = null;
                // We can only get the real slotId from the SubInfoRecord, we can't calculate the
                // slotId from the subId or the phoneId in all instances.
                SubscriptionInfo record =
                        mSubscriptionManager.getActiveSubscriptionInfo(subId);
                if (record != null) {
                    subDisplayName = record.getDisplayName();
                    slotId = record.getSimSlotIndex();
                    color = record.getIconTint();
                    iconBitmap = record.createIconBitmap(mContext);
                }

                String slotIdString;
                if (SubscriptionManager.isValidSlotId(slotId)) {
                    slotIdString = Integer.toString(slotId);
                } else {
                    slotIdString = mContext.getResources().getString(R.string.unknown);
                }

                if (TextUtils.isEmpty(subDisplayName)) {
                    // Either the sub record is not there or it has an empty display name.
                    Log.w(this, "Could not get a display name for subid: %d", subId);
                    subDisplayName = mContext.getResources().getString(
                            R.string.sim_description_default, slotIdString);
                }

                // The label is user-visible so let's use the display name that the user may
                // have set in Settings->Sim cards.
                label = dummyPrefix + subDisplayName;
                description = dummyPrefix + mContext.getResources().getString(
                                R.string.sim_description_default, slotIdString);
            }

            // By default all SIM phone accounts can place emergency calls.
            int capabilities = PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS |
                    PhoneAccount.CAPABILITY_MULTI_USER;

            if (iconBitmap == null) {
                iconBitmap = BitmapFactory.decodeResource(
                        mContext.getResources(),
                        defaultPhoneAccountIcon);
            }

            PhoneAccount account = PhoneAccount.builder(phoneAccountHandle, label)
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, line1Number, null))
                    .setSubscriptionAddress(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setCapabilities(capabilities)
                    .setIcon(iconBitmap)
                    .setHighlightColor(color)
                    .setShortDescription(description)
                    .setSupportedUriSchemes(Arrays.asList(
                            PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
                    .build();

            // Register with Telecom and put into the account entry.
            mTelecomManager.registerPhoneAccount(account);
            return account;
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mAccount != null ? mAccount.getAccountHandle() : null;
        }
    }

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            // Any time the SubscriptionInfo changes...rerun the setup
            tearDownAccounts();
            setupAccounts();
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            int newState = serviceState.getState();
            if (newState == ServiceState.STATE_IN_SERVICE && mServiceState != newState) {
                tearDownAccounts();
                setupAccounts();
            }
            mServiceState = newState;
        }
    };

    private static TelecomAccountRegistry sInstance;
    private final Context mContext;
    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private List<AccountEntry> mAccounts = new LinkedList<AccountEntry>();
    private int mServiceState = ServiceState.STATE_POWER_OFF;

    // TODO: Remove back-pointer from app singleton to Service, since this is not a preferred
    // pattern; redesign. This was added to fix a late release bug.
    private TelephonyConnectionService mTelephonyConnectionService;

    TelecomAccountRegistry(Context context) {
        mContext = context;
        mTelecomManager = TelecomManager.from(context);
        mTelephonyManager = TelephonyManager.from(context);
        mSubscriptionManager = SubscriptionManager.from(context);
    }

    static synchronized final TelecomAccountRegistry getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new TelecomAccountRegistry(context);
        }
        return sInstance;
    }

    void setTelephonyConnectionService(TelephonyConnectionService telephonyConnectionService) {
        this.mTelephonyConnectionService = telephonyConnectionService;
    }

    TelephonyConnectionService getTelephonyConnectionService() {
        return mTelephonyConnectionService;
    }

    /**
     * Sets up all the phone accounts for SIMs on first boot.
     */
    void setupOnBoot() {
        // TODO: When this object "finishes" we should unregister by invoking
        // SubscriptionManager.getInstance(mContext).unregister(mOnSubscriptionsChangedListener);
        // This is not strictly necessary because it will be unregistered if the
        // notification fails but it is good form.

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionsChanged the first time.
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                mOnSubscriptionsChangedListener);

        // We also need to listen for changes to the service state (e.g. emergency -> in service)
        // because this could signal a removal or addition of a SIM in a single SIM phone.
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
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
        List<PhoneAccountHandle> accountHandles = mTelecomManager.getAllPhoneAccountHandles();
        for (PhoneAccountHandle handle : accountHandles) {
            if (telephonyComponentName.equals(handle.getComponentName()) &&
                    !hasAccountEntryForPhoneAccount(handle)) {
                Log.d(this, "Unregistering phone account %s.", handle);
                mTelecomManager.unregisterPhoneAccount(handle);
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
        if (DBG && phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
            mAccounts.add(new AccountEntry(phones[0], false /* emergency */, true /* isDummy */));
        }

        // Clean up any PhoneAccounts that are no longer relevant
        cleanupPhoneAccounts();
    }

    private void tearDownAccounts() {
        for (AccountEntry entry : mAccounts) {
            entry.teardown();
        }
        mAccounts.clear();
    }
}
