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
 * limitations under the License
 */

package com.android.phone.settings;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.internal.util.CharSequences;
import com.android.phone.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Preference activity used to facilitate enabling and disabling phone accounts by the user.
 */
public class PhoneAccountSelectionPreferenceActivity extends PreferenceActivity {

    /**
     * Preference fragment containing a list of all {@link PhoneAccount}s in the form of switches
     * the user can use to enable or disable accounts.
     */
    public static class PhoneAccountSelectionPreferenceFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener {
        private static final String CATEGORY_PHONE_ACCOUNTS_KEY = "phone_accounts_list";

        private TelecomManager mTelecomManager;
        private PreferenceCategory mPhoneAccountsCategory;

        /**
         * Represents a single {@link PhoneAccount} for the purpose enabling and disabling.
         */
        static class PhoneAccountPreference extends SwitchPreference {
            private PhoneAccountHandle mPhoneAccountHandle;
            private boolean mPreviousState;

            public PhoneAccountPreference(Context context, PhoneAccount phoneAccount) {
                super(context);

                setPhoneAccount(phoneAccount);
            }

            /**
             * Configures the {@code PhoneAccountPreference} for the passed in {@link PhoneAccount}.
             *
             * @param phoneAccount The phone account.
             */
            private void setPhoneAccount(PhoneAccount phoneAccount) {
                mPhoneAccountHandle = phoneAccount.getAccountHandle();
                mPreviousState = phoneAccount.isEnabled();
                this.setTitle(phoneAccount.getLabel());
                this.setChecked(mPreviousState);
            }

            public boolean getPreviousState() {
                return mPreviousState;
            }

            public PhoneAccountHandle getPhoneAccountHandle() {
                return mPhoneAccountHandle;
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.phone_account_selection);
            mPhoneAccountsCategory = (PreferenceCategory) findPreference(
                    CATEGORY_PHONE_ACCOUNTS_KEY);
            mTelecomManager = TelecomManager.from(this.getActivity());

            List<PhoneAccount> phoneAccounts = mTelecomManager.getAllPhoneAccounts();
            Collections.sort(phoneAccounts, new Comparator<PhoneAccount>() {
                @Override
                public int compare(PhoneAccount o1, PhoneAccount o2) {
                    return CharSequences.compareToIgnoreCase(o1.getLabel(), o2.getLabel());
                }
            });

            for (PhoneAccount phoneAccount : phoneAccounts) {
                // Do not add Sim PhoneAccounts.
                if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    continue;
                }

                PhoneAccountPreference phoneAccountPreference = new PhoneAccountPreference(
                        getActivity(), phoneAccount);
                phoneAccountPreference.setOnPreferenceChangeListener(this);
                phoneAccountPreference.setEnabled(!phoneAccount.hasCapabilities(
                        PhoneAccount.CAPABILITY_ALWAYS_ENABLED));
                mPhoneAccountsCategory.addPreference(phoneAccountPreference);
            }
        }

        /**
         * Handles changes to preferences
         * @param preference The preference which changed.
         * @param newValue The new value of the preference.
         * @return
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference instanceof PhoneAccountPreference) {
                PhoneAccountPreference phoneAccountPreference = (PhoneAccountPreference) preference;
                boolean newState = Boolean.valueOf(newValue.toString()).booleanValue();

                if (newState != phoneAccountPreference.getPreviousState()) {
                    mTelecomManager.setPhoneAccountEnabled(
                            phoneAccountPreference.getPhoneAccountHandle(), newState);
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.phone_account_selection_activity, target);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTitle(getResources().getString(R.string.call_settings));
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        // By default, show the main fragment.
        Intent intent = getIntent();
        if (intent.getStringArrayExtra(EXTRA_SHOW_FRAGMENT) == null) {
            getIntent().putExtra(EXTRA_SHOW_FRAGMENT,
                    PhoneAccountSelectionPreferenceFragment.class.getName());
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean isValidFragment(String fragmentName) {
        return true;
    }
}
