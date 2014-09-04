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

import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
import android.util.AttributeSet;

import java.util.List;
import java.util.Objects;

public class AccountSelectionPreference extends ListPreference implements
        Preference.OnPreferenceChangeListener {

    public interface AccountSelectionListener {
        boolean onAccountSelected(AccountSelectionPreference pref, PhoneAccountHandle account);
    }

    private AccountSelectionListener mListener;
    private PhoneAccountHandle[] mAccounts;
    private String[] mEntryValues;
    private CharSequence[] mEntries;

    public AccountSelectionPreference(Context context) {
        super(context);
        setOnPreferenceChangeListener(this);
    }

    public AccountSelectionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnPreferenceChangeListener(this);
    }

    public void setListener(AccountSelectionListener listener) {
        mListener = listener;
    }

    public void setModel(
            TelecommManager telecommManager,
            List<PhoneAccountHandle> accountsList,
            PhoneAccountHandle currentSelection,
            CharSequence nullSelectionString) {

        mAccounts = accountsList.toArray(new PhoneAccountHandle[accountsList.size()]);
        mEntryValues = new String[mAccounts.length + 1];
        mEntries = new CharSequence[mAccounts.length + 1];

        int selectedIndex = mAccounts.length;  // Points to nullSelectionString by default
        int i = 0;
        for ( ; i < mAccounts.length; i++) {
            CharSequence label = telecommManager.getPhoneAccount(mAccounts[i]).getLabel();
            mEntries[i] = label == null ? null : label.toString();
            mEntryValues[i] = Integer.toString(i);
            if (Objects.equals(currentSelection, mAccounts[i])) {
                selectedIndex = i;
            }
        }
        mEntryValues[i] = Integer.toString(i);
        mEntries[i] = nullSelectionString;

        setEntryValues(mEntryValues);
        setEntries(mEntries);
        setValueIndex(selectedIndex);
        setSummary(mEntries[selectedIndex]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mListener != null) {
            int index = Integer.parseInt((String) newValue);
            PhoneAccountHandle account = index < mAccounts.length ? mAccounts[index] : null;
            if (mListener.onAccountSelected(this, account)) {
                setSummary(mEntries[index]);
                return true;
            }
        }
        return false;
    }
}
