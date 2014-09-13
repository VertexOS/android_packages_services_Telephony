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

import com.android.phone.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.AttributeSet;

import java.util.List;
import java.util.Objects;

public class AccountSelectionPreference extends ListPreference implements
        Preference.OnPreferenceChangeListener {

    public interface AccountSelectionListener {
        boolean onAccountSelected(AccountSelectionPreference pref, PhoneAccountHandle account);
        void onAccountSelectionDialogShow(AccountSelectionPreference pref);
    }

    private final Context mContext;
    private AccountSelectionListener mListener;
    private PhoneAccountHandle[] mAccounts;
    private String[] mEntryValues;
    private CharSequence[] mEntries;

    public AccountSelectionPreference(Context context) {
        super(context);
        mContext = context;
        setOnPreferenceChangeListener(this);
    }

    public AccountSelectionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setOnPreferenceChangeListener(this);
    }

    public void setListener(AccountSelectionListener listener) {
        mListener = listener;
    }

    public void setModel(
            TelecomManager telecomManager,
            List<PhoneAccountHandle> accountsList,
            PhoneAccountHandle currentSelection,
            CharSequence nullSelectionString) {

        mAccounts = accountsList.toArray(new PhoneAccountHandle[accountsList.size()]);
        mEntryValues = new String[mAccounts.length + 1];
        mEntries = new CharSequence[mAccounts.length + 1];

        int selectedIndex = mAccounts.length;  // Points to nullSelectionString by default
        int i = 0;
        for ( ; i < mAccounts.length; i++) {
            CharSequence label = telecomManager.getPhoneAccount(mAccounts[i]).getLabel();
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

    /**
     * Modifies the dialog to change the default "Cancel" button to "Choose Accounts", which
     * triggers the {@link PhoneAccountSelectionPreferenceActivity} to be shown.
     *
     * @param builder The {@code AlertDialog.Builder}.
     */
    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        // Notify the listener that the dialog is about to be built.  This is important so that the
        // list of enabled accounts can be updated prior to showing the dialog.
        mListener.onAccountSelectionDialogShow(this);

        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                showSelectPhoneAccounts();
            }
        };
        builder.setNegativeButton(R.string.phone_accounts_choose_accounts, listener);
        super.onPrepareDialogBuilder(builder);
    }

    /**
     * Displays the {@link PhoneAccountSelectionPreferenceActivity} where the user is able to
     * enable and disable phone accounts.
     */
    private void showSelectPhoneAccounts() {
        Intent intent = new Intent(mContext, PhoneAccountSelectionPreferenceActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, null, UserHandle.CURRENT);
    }
}
