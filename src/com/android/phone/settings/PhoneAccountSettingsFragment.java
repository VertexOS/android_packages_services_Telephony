package com.android.phone.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.android.phone.R;
import com.android.services.telephony.sip.SipAccountRegistry;
import com.android.services.telephony.sip.SipSharedPreferences;
import com.android.services.telephony.sip.SipUtil;

import java.util.List;

public class PhoneAccountSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                Preference.OnPreferenceClickListener,
                AccountSelectionPreference.AccountSelectionListener {

    private static final Intent CONNECTION_SERVICE_CONFIGURE_INTENT =
            new Intent(TelecomManager.ACTION_CONNECTION_SERVICE_CONFIGURE)
                    .addCategory(Intent.CATEGORY_DEFAULT);

    private static final String DEFAULT_OUTGOING_ACCOUNT_KEY = "default_outgoing_account";

    private static final String CONFIGURE_CALL_ASSISTANT_PREF_KEY =
            "wifi_calling_configure_call_assistant_preference";
    private static final String CALL_ASSISTANT_CATEGORY_PREF_KEY =
            "phone_accounts_call_assistant_settings_category_key";
    private static final String SELECT_CALL_ASSISTANT_PREF_KEY =
            "wifi_calling_call_assistant_preference";

    private static final String SIP_SETTINGS_CATEGORY_PREF_KEY =
            "phone_accounts_sip_settings_category_key";
    private static final String USE_SIP_PREF_KEY = "use_sip_calling_options_key";
    private static final String SIP_RECEIVE_CALLS_PREF_KEY = "sip_receive_calls_key";

    private String LOG_TAG = PhoneAccountSettingsFragment.class.getSimpleName();

    private TelecomManager mTelecomManager;

    private AccountSelectionPreference mDefaultOutgoingAccount;
    private AccountSelectionPreference mSelectCallAssistant;
    private Preference mConfigureCallAssistant;

    private ListPreference mUseSipCalling;
    private CheckBoxPreference mSipReceiveCallsPreference;
    private SipSharedPreferences mSipSharedPreferences;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mTelecomManager = TelecomManager.from(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
        }

        addPreferencesFromResource(com.android.phone.R.xml.phone_account_settings);

        mDefaultOutgoingAccount = (AccountSelectionPreference)
                getPreferenceScreen().findPreference(DEFAULT_OUTGOING_ACCOUNT_KEY);
        if (mTelecomManager.getAllPhoneAccountsCount() > 1) {
            mDefaultOutgoingAccount.setListener(this);
            updateDefaultOutgoingAccountsModel();
        } else {
            getPreferenceScreen().removePreference(mDefaultOutgoingAccount);
        }

        if (!mTelecomManager.getSimCallManagers().isEmpty()) {
            mSelectCallAssistant = (AccountSelectionPreference)
                    getPreferenceScreen().findPreference(SELECT_CALL_ASSISTANT_PREF_KEY);
            mSelectCallAssistant.setListener(this);
            mSelectCallAssistant.setDialogTitle(
                    R.string.wifi_calling_select_call_assistant_summary);

            mConfigureCallAssistant =
                    getPreferenceScreen().findPreference(CONFIGURE_CALL_ASSISTANT_PREF_KEY);
            mConfigureCallAssistant.setOnPreferenceClickListener(this);
            updateCallAssistantModel();
        } else {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(CALL_ASSISTANT_CATEGORY_PREF_KEY));
        }

        if (SipUtil.isVoipSupported(getActivity())) {
            mSipSharedPreferences = new SipSharedPreferences(getActivity());

            mUseSipCalling = (ListPreference)
                    getPreferenceScreen().findPreference(USE_SIP_PREF_KEY);
            mUseSipCalling.setEntries(!SipManager.isSipWifiOnly(getActivity())
                    ? R.array.sip_call_options_wifi_only_entries
                    : R.array.sip_call_options_entries);
            mUseSipCalling.setOnPreferenceChangeListener(this);

            int optionsValueIndex =
                    mUseSipCalling.findIndexOfValue(mSipSharedPreferences.getSipCallOption());
            if (optionsValueIndex == -1) {
                // If the option is invalid (eg. deprecated value), default to SIP_ADDRESS_ONLY.
                mSipSharedPreferences.setSipCallOption(
                        getResources().getString(R.string.sip_address_only));
                optionsValueIndex =
                        mUseSipCalling.findIndexOfValue(mSipSharedPreferences.getSipCallOption());
            }
            mUseSipCalling.setValueIndex(optionsValueIndex);
            mUseSipCalling.setSummary(mUseSipCalling.getEntry());

            mSipReceiveCallsPreference = (CheckBoxPreference)
                    getPreferenceScreen().findPreference(SIP_RECEIVE_CALLS_PREF_KEY);
            mSipReceiveCallsPreference.setEnabled(SipUtil.isPhoneIdle(getActivity()));
            mSipReceiveCallsPreference.setChecked(
                    mSipSharedPreferences.isReceivingCallsEnabled());
            mSipReceiveCallsPreference.setOnPreferenceChangeListener(this);
        } else {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(SIP_SETTINGS_CATEGORY_PREF_KEY));
        }
    }

    /**
     * Handles changes to the preferences.
     *
     * @param pref The preference changed.
     * @param objValue The changed value.
     * @return True if the preference change has been handled, and false otherwise.
     */
    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        if (pref == mUseSipCalling) {
            String option = objValue.toString();
            mSipSharedPreferences.setSipCallOption(option);
            mUseSipCalling.setValueIndex(mUseSipCalling.findIndexOfValue(option));
            mUseSipCalling.setSummary(mUseSipCalling.getEntry());
            return true;
        } else if (pref == mSipReceiveCallsPreference) {
            final boolean isEnabled = !mSipReceiveCallsPreference.isChecked();
            new Thread(new Runnable() {
                public void run() {
                    handleSipReceiveCallsOption(isEnabled);
                }
            }).start();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mConfigureCallAssistant) {
            try {
                startActivity(CONNECTION_SERVICE_CONFIGURE_INTENT);
            } catch (ActivityNotFoundException e) {
                Log.d(LOG_TAG, "Could not resolve telecom connection service configure intent.");
            }
            return true;
        }
        return false;
    }

    /**
     * Handles a phone account selection, namely when a call assistant has been selected.
     *
     * @param pref The account selection preference which triggered the account selected event.
     * @param account The account selected.
     * @return True if the account selection has been handled, and false otherwise.
     */
    @Override
    public boolean onAccountSelected(AccountSelectionPreference pref, PhoneAccountHandle account) {
        if (pref == mDefaultOutgoingAccount) {
            mTelecomManager.setUserSelectedOutgoingPhoneAccount(account);
            return true;
        } else if (pref == mSelectCallAssistant) {
            mTelecomManager.setSimCallManager(account);
            return true;
        }
        return false;
    }

    /**
     * Repopulate the dialog to pick up changes before showing.
     *
     * @param pref The account selection preference dialog being shown.
     */
    @Override
    public void onAccountSelectionDialogShow(AccountSelectionPreference pref) {
        if (pref == mDefaultOutgoingAccount) {
            updateDefaultOutgoingAccountsModel();
        } else if (pref == mSelectCallAssistant) {
            updateCallAssistantModel();
        }
    }

    /**
     * Update the configure preference summary when the call assistant changes.
     */
    @Override
    public void onAccountChanged(AccountSelectionPreference pref) {
        if (pref == mSelectCallAssistant) {
            updateConfigureCallAssistantSummary();
        }
    }

    private synchronized void handleSipReceiveCallsOption(boolean isEnabled) {
        mSipSharedPreferences.setReceivingCallsEnabled(isEnabled);

        SipUtil.useSipToReceiveIncomingCalls(getActivity(), isEnabled);

        // Restart all Sip services to ensure we reflect whether we are receiving calls.
        SipAccountRegistry sipAccountRegistry = SipAccountRegistry.getInstance();
        sipAccountRegistry.restartSipService(getActivity());
    }

    /**
     * Queries the telcomm manager to update the default outgoing account selection preference
     * with the list of outgoing accounts and the current default outgoing account.
     */
    private void updateDefaultOutgoingAccountsModel() {
        mDefaultOutgoingAccount.setModel(
                mTelecomManager,
                mTelecomManager.getCallCapablePhoneAccounts(),
                mTelecomManager.getUserSelectedOutgoingPhoneAccount(),
                getString(R.string.phone_accounts_ask_every_time));
    }

    /**
     * Queries the telecomm manager to update the account selection preference with the list of
     * call assistants, and the currently selected call assistant.
     */
    public void updateCallAssistantModel() {
        List<PhoneAccountHandle> simCallManagers = mTelecomManager.getSimCallManagers();
        mSelectCallAssistant.setModel(
                mTelecomManager,
                simCallManagers,
                mTelecomManager.getSimCallManager(),
                getString(R.string.wifi_calling_call_assistant_none));

        updateConfigureCallAssistantSummary();
    }

    /**
     * Updates the summary on the "configure call assistant" preference. If it is the last entry,
     * show the summary for when no call assistant is selected. Otherwise, display the currently
     * selected call assistant.
     */
    private void updateConfigureCallAssistantSummary() {
        if (mSelectCallAssistant.getEntries().length - 1
                == mSelectCallAssistant.findIndexOfValue(mSelectCallAssistant.getValue())) {
            mConfigureCallAssistant.setSummary(
                    R.string.wifi_calling_call_assistant_configure_no_selection);
            mConfigureCallAssistant.setEnabled(false);
        } else {
            mConfigureCallAssistant.setSummary(null);
            mConfigureCallAssistant.setEnabled(true);
        }
    }
}
