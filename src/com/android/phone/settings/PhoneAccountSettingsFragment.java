package com.android.phone.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;
import com.android.services.telephony.sip.SipAccountRegistry;
import com.android.services.telephony.sip.SipSharedPreferences;
import com.android.services.telephony.sip.SipUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class PhoneAccountSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                Preference.OnPreferenceClickListener,
                AccountSelectionPreference.AccountSelectionListener {

    private static final String ACCOUNTS_LIST_CATEGORY_KEY =
            "phone_accounts_accounts_list_category_key";

    private static final String DEFAULT_OUTGOING_ACCOUNT_KEY = "default_outgoing_account";
    private static final String ALL_CALLING_ACCOUNTS_KEY = "phone_account_all_calling_accounts";

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

    /**
     * Value to start ordering of phone accounts relative to other preferences. By setting this
     * value on the phone account listings, we ensure that anything that is ordered before
     * {value} in the preference XML comes before the phone account list and anything with
     * a value significantly larger will list after.
     */
    private static final int ACCOUNT_ORDERING_START_VALUE = 100;

    private String LOG_TAG = PhoneAccountSettingsFragment.class.getSimpleName();

    private TelecomManager mTelecomManager;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    private PreferenceCategory mAccountList;

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
        mTelephonyManager = TelephonyManager.from(getActivity());
        mSubscriptionManager = SubscriptionManager.from(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
        }

        addPreferencesFromResource(R.xml.phone_account_settings);

        mAccountList = (PreferenceCategory) getPreferenceScreen().findPreference(
                ACCOUNTS_LIST_CATEGORY_KEY);
        if (shouldShowConnectionServiceList()) {
            initAccountList();

            mDefaultOutgoingAccount = (AccountSelectionPreference)
                    getPreferenceScreen().findPreference(DEFAULT_OUTGOING_ACCOUNT_KEY);
            mDefaultOutgoingAccount.setListener(this);
            if (mTelecomManager.getCallCapablePhoneAccounts().size() > 1) {
                updateDefaultOutgoingAccountsModel();
            } else {
                getPreferenceScreen().removePreference(mDefaultOutgoingAccount);
            }

            Preference allAccounts = getPreferenceScreen().findPreference(ALL_CALLING_ACCOUNTS_KEY);
            if (getNonSimCallingAccounts().size() == 0 && allAccounts != null) {
                getPreferenceScreen().removePreference(allAccounts);
            }
        } else {
            getPreferenceScreen().removePreference(mAccountList);
        }

        List<PhoneAccountHandle> simCallManagers = mTelecomManager.getSimCallManagers();
        PreferenceCategory callAssistantCategory = (PreferenceCategory)
                getPreferenceScreen().findPreference(CALL_ASSISTANT_CATEGORY_PREF_KEY);
        if (simCallManagers.isEmpty()) {
            getPreferenceScreen().removePreference(callAssistantCategory);
        } else {
            // Display a list of call assistants. Choosing an item from the list enables the
            // corresponding call assistant.
            mSelectCallAssistant = (AccountSelectionPreference)
                    getPreferenceScreen().findPreference(SELECT_CALL_ASSISTANT_PREF_KEY);
            mSelectCallAssistant.setListener(this);
            mSelectCallAssistant.setDialogTitle(
                    R.string.wifi_calling_select_call_assistant_summary);
            updateCallAssistantModel();

            mConfigureCallAssistant =
                    getPreferenceScreen().findPreference(CONFIGURE_CALL_ASSISTANT_PREF_KEY);
            mConfigureCallAssistant.setOnPreferenceClickListener(this);
            updateConfigureCallAssistant();
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
            Intent intent = getConfigureCallAssistantIntent();
            if (intent != null) {
                PhoneAccountHandle handle = mTelecomManager.getSimCallManager();
                UserHandle userHandle = handle.getUserHandle();
                try {
                    if (userHandle != null) {
                        getActivity().startActivityAsUser(intent, userHandle);
                    } else {
                        startActivity(intent);
                    }
                } catch (ActivityNotFoundException e) {
                    Log.d(LOG_TAG, "Could not resolve call assistant configure intent: " + intent);
                }
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
            updateConfigureCallAssistant();
        }
    }

    /**
     * Update the configure preference summary when the call assistant changes.
     */
    @Override
    public void onAccountChanged(AccountSelectionPreference pref) {
        if (pref == mSelectCallAssistant) {
            updateConfigureCallAssistant();
        }
    }

    private synchronized void handleSipReceiveCallsOption(boolean isEnabled) {
        Context context = getActivity();
        if (context == null) {
            // Return if the fragment is detached from parent activity before executed by thread.
            return;
        }

        mSipSharedPreferences.setReceivingCallsEnabled(isEnabled);

        SipUtil.useSipToReceiveIncomingCalls(context, isEnabled);

        // Restart all Sip services to ensure we reflect whether we are receiving calls.
        SipAccountRegistry sipAccountRegistry = SipAccountRegistry.getInstance();
        sipAccountRegistry.restartSipService(context);
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
        mSelectCallAssistant.setModel(
                mTelecomManager, mTelecomManager.getSimCallManagers(),
                mTelecomManager.getSimCallManager(),
                getString(R.string.wifi_calling_call_assistant_none));
    }

    /**
     * Shows or hides the "configure call assistant" preference.
     */
    private void updateConfigureCallAssistant() {
        Intent intent = getConfigureCallAssistantIntent();
        boolean shouldShow = intent != null && !getActivity().getPackageManager()
            .queryIntentActivities(intent, 0).isEmpty();

        PreferenceCategory callAssistantCategory = (PreferenceCategory)
                getPreferenceScreen().findPreference(CALL_ASSISTANT_CATEGORY_PREF_KEY);
        if (shouldShow) {
            callAssistantCategory.addPreference(mConfigureCallAssistant);
        } else {
            callAssistantCategory.removePreference(mConfigureCallAssistant);
        }
    }

    private void initAccountList() {
        // Obtain the list of phone accounts.
        List<PhoneAccount> accounts = new ArrayList<>();
        for (PhoneAccountHandle handle : mTelecomManager.getCallCapablePhoneAccounts()) {
            PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
            if (account != null) {
                accounts.add(account);
            }
        }

        // Sort the accounts according to how we want to display them.
        Collections.sort(accounts, new Comparator<PhoneAccount>() {
            @Override
            public int compare(PhoneAccount account1, PhoneAccount account2) {
                int retval = 0;

                // SIM accounts go first
                boolean isSim1 = (account1.getCapabilities() &
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0;
                boolean isSim2 = (account2.getCapabilities() &
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0;
                if (isSim1 != isSim2) {
                    retval = isSim1 ? -1 : 1;
                }

                // Then order by package
                if (retval == 0) {
                    String pkg1 = account1.getAccountHandle().getComponentName().getPackageName();
                    String pkg2 = account2.getAccountHandle().getComponentName().getPackageName();
                    retval = pkg1.compareTo(pkg2);
                }

                // Finally, order by label
                if (retval == 0) {
                    String label1 = nullToEmpty(account1.getLabel().toString());
                    String label2 = nullToEmpty(account2.getLabel().toString());
                    retval = label1.compareTo(label2);
                }

                // Then by hashcode
                if (retval == 0) {
                    retval = account1.hashCode() - account2.hashCode();
                }
                return retval;
            }
        });

        int order = ACCOUNT_ORDERING_START_VALUE;

        // Add an entry for each account.
        for (PhoneAccount account : accounts) {
            PhoneAccountHandle handle = account.getAccountHandle();
            Intent intent = null;
            boolean isSimAccount = false;

            // SIM phone accounts use a different setting intent and are thus handled differently.
            if ((PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION & account.getCapabilities()) != 0) {
                isSimAccount = true;
                SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(
                        mTelephonyManager.getSubIdForPhoneAccount(account));

                if (subInfo != null) {
                    intent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    SubscriptionInfoHelper.addExtrasToIntent(intent, subInfo);
                }
            } else {
                // Build the settings intent.
                intent = new Intent(TelecomManager.ACTION_CONNECTION_SERVICE_CONFIGURE);
                intent.setPackage(handle.getComponentName().getPackageName());
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);

                // Check to see that the phone account package can handle the setting intent.
                PackageManager pm = getActivity().getPackageManager();
                List<ResolveInfo> resolutions = pm.queryIntentActivities(intent, 0);
                if (resolutions.size() == 0) {
                    intent = null;  // set no intent if the package cannot handle it.
                }
            }

            // Create the preference & add the label
            Preference accountPreference = new Preference(getActivity());
            accountPreference.setTitle(account.getLabel());

            // Add an icon.
            Icon icon = account.getIcon();
            accountPreference.setIcon(icon.loadDrawable(getActivity()));

            // Add an intent to send the user to the account's settings.
            if (intent != null) {
                accountPreference.setIntent(intent);
            }

            accountPreference.setOrder(order++);
            mAccountList.addPreference(accountPreference);
        }
    }

    private Intent getConfigureCallAssistantIntent() {
        PhoneAccountHandle handle = mTelecomManager.getSimCallManager();
        if (handle != null) {
            String packageName = handle.getComponentName().getPackageName();
            if (packageName != null) {
                return new Intent(TelecomManager.ACTION_CONNECTION_SERVICE_CONFIGURE)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setPackage(packageName);
            }
        }
        return null;
    }

    private boolean shouldShowConnectionServiceList() {
        return mTelephonyManager.isMultiSimEnabled() ||
            getNonSimCallingAccounts().size() > 0;
    }

    private List<PhoneAccountHandle> getNonSimCallingAccounts() {
        List<PhoneAccountHandle> accountHandles =
                mTelecomManager.getCallCapablePhoneAccounts();
        for (Iterator<PhoneAccountHandle> i = accountHandles.iterator(); i.hasNext();) {
            PhoneAccountHandle handle = i.next();
            PhoneAccount account = mTelecomManager.getPhoneAccount(handle);
            if (account == null || (account.getCapabilities() &
                    PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0) {
                // If the account is no longer valid OR the account is a built-in SIM account,
                // remove!
                i.remove();
            }
        }
        return accountHandles;
    }

    private String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
}
