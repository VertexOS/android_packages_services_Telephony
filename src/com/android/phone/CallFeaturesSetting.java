/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.phone;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.android.ims.ImsManager;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.common.util.SettingsUtil;
import com.android.phone.settings.AccountSelectionPreference;
import com.android.phone.settings.CallForwardInfoUtil;
import com.android.phone.settings.TtyModeListPreference;
import com.android.phone.settings.VoicemailDialogUtil;
import com.android.phone.settings.VoicemailNotificationSettingsUtil;
import com.android.phone.settings.VoicemailProviderListPreference;
import com.android.phone.settings.VoicemailProviderListPreference.VoicemailProvider;
import com.android.phone.settings.VoicemailProviderSettings;
import com.android.phone.settings.VoicemailProviderSettingsUtil;
import com.android.phone.settings.VoicemailRingtonePreference;
import com.android.phone.settings.fdn.FdnSetting;
import com.android.services.telephony.sip.SipUtil;

import java.lang.String;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Top level "Call settings" UI; see res/xml/call_feature_setting.xml
 *
 * This preference screen is the root of the "Call settings" hierarchy available from the Phone
 * app; the settings here let you control various features related to phone calls (including
 * voicemail settings, the "Respond via SMS" feature, and others.)  It's used only on
 * voice-capable phone devices.
 *
 * Note that this activity is part of the package com.android.phone, even
 * though you reach it from the "Phone" app (i.e. DialtactsActivity) which
 * is from the package com.android.contacts.
 *
 * For the "Mobile network settings" screen under the main Settings app,
 * See {@link MobileNetworkSettings}.
 *
 * TODO: Settings should be split into PreferenceFragments where possible (ie. voicemail).
 *
 * @see com.android.phone.MobileNetworkSettings
 */
public class CallFeaturesSetting extends PreferenceActivity
        implements DialogInterface.OnClickListener,
                Preference.OnPreferenceChangeListener,
                EditPhoneNumberPreference.OnDialogClosedListener,
                EditPhoneNumberPreference.GetDefaultNumberListener {
    private static final String LOG_TAG = "CallFeaturesSetting";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    // STOPSHIP if true. Flag to override behavior default behavior to hide VT setting.
    private static final boolean ENABLE_VT_FLAG = false;

    /**
     * Intent action to bring up Voicemail Provider settings.
     *
     * @see #IGNORE_PROVIDER_EXTRA
     */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";
    // intent action sent by this activity to a voice mail provider
    // to trigger its configuration UI
    public static final String ACTION_CONFIGURE_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.CONFIGURE_VOICEMAIL";
    // Extra put in the return from VM provider config containing voicemail number to set
    public static final String VM_NUMBER_EXTRA = "com.android.phone.VoicemailNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_EXTRA = "com.android.phone.ForwardingNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_TIME_EXTRA = "com.android.phone.ForwardingNumberTime";
    // If the VM provider returns non null value in this extra we will force the user to
    // choose another VM provider
    public static final String SIGNOUT_EXTRA = "com.android.phone.Signout";

    /**
     * String Extra put into ACTION_ADD_VOICEMAIL call to indicate which provider should be hidden
     * in the list of providers presented to the user. This allows a provider which is being
     * disabled (e.g. GV user logging out) to force the user to pick some other provider.
     */
    public static final String IGNORE_PROVIDER_EXTRA = "com.android.phone.ProviderToIgnore";

    /**
     * String Extra put into ACTION_ADD_VOICEMAIL to indicate that the voicemail setup screen should
     * be opened.
     */
    public static final String SETUP_VOICEMAIL_EXTRA = "com.android.phone.SetupVoicemail";

    // string constants
    private static final String NUM_PROJECTION[] = {CommonDataKinds.Phone.NUMBER};

    // String keys for preference lookup
    // TODO: Naming these "BUTTON_*" is confusing since they're not actually buttons(!)
    // TODO: Consider moving these strings to strings.xml, so that they are not duplicated here and
    // in the layout files. These strings need to be treated carefully; if the setting is
    // persistent, they are used as the key to store shared preferences and the name should not be
    // changed unless the settings are also migrated.
    private static final String VOICEMAIL_SETTING_SCREEN_PREF_KEY = "button_voicemail_category_key";
    private static final String BUTTON_VOICEMAIL_KEY = "button_voicemail_key";
    private static final String BUTTON_VOICEMAIL_PROVIDER_KEY = "button_voicemail_provider_key";
    private static final String BUTTON_VOICEMAIL_SETTING_KEY = "button_voicemail_setting_key";
    private static final String BUTTON_FDN_KEY   = "button_fdn_key";

    private static final String BUTTON_DTMF_KEY        = "button_dtmf_settings";
    private static final String BUTTON_RETRY_KEY       = "button_auto_retry_key";
    private static final String BUTTON_TTY_KEY         = "button_tty_mode_key";
    private static final String BUTTON_HAC_KEY         = "button_hac_key";

    private static final String BUTTON_GSM_UMTS_OPTIONS = "button_gsm_more_expand_key";
    private static final String BUTTON_CDMA_OPTIONS = "button_cdma_more_expand_key";
    private static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    private static final String ADDITIONAL_GSM_SETTINGS_KEY = "additional_gsm_call_settings_key";

    private static final String PHONE_ACCOUNT_SETTINGS_KEY =
            "phone_account_settings_preference_screen";

    private static final String ENABLE_VIDEO_CALLING_KEY = "button_enable_video_calling";

    /** Event for Async voicemail change call */
    private static final int EVENT_VOICEMAIL_CHANGED        = 500;
    private static final int EVENT_FORWARDING_CHANGED       = 501;
    private static final int EVENT_FORWARDING_GET_COMPLETED = 502;

    public static final String HAC_KEY = "HACSetting";
    public static final String HAC_VAL_ON = "ON";
    public static final String HAC_VAL_OFF = "OFF";

    /** Handle to voicemail pref */
    private static final int VOICEMAIL_PREF_ID = 1;
    private static final int VOICEMAIL_PROVIDER_CFG_ID = 2;

    private Phone mPhone;
    private AudioManager mAudioManager;

    private SubscriptionInfoHelper mSubscriptionInfoHelper;

    private EditPhoneNumberPreference mSubMenuVoicemailSettings;

    /** Whether dialpad plays DTMF tone or not. */
    private CheckBoxPreference mButtonAutoRetry;
    private CheckBoxPreference mButtonHAC;
    private ListPreference mButtonDTMF;
    private TtyModeListPreference mButtonTTY;
    private VoicemailProviderListPreference mVoicemailProviders;
    private PreferenceScreen mVoicemailSettingsScreen;
    private PreferenceScreen mVoicemailSettings;
    private VoicemailRingtonePreference mVoicemailNotificationRingtone;
    private CheckBoxPreference mVoicemailNotificationVibrate;
    private CheckBoxPreference mEnableVideoCalling;

    /**
     * Results of reading forwarding settings
     */
    private CallForwardInfo[] mForwardingReadResults = null;

    /**
     * Result of forwarding number change.
     * Keys are reasons (eg. unconditional forwarding).
     */
    private Map<Integer, AsyncResult> mForwardingChangeResults = null;

    /**
     * Expected CF read result types.
     * This set keeps track of the CF types for which we've issued change
     * commands so we can tell when we've received all of the responses.
     */
    private Collection<Integer> mExpectedChangeResultReasons = null;

    /**
     * Result of vm number change
     */
    private AsyncResult mVoicemailChangeResult = null;

    /**
     * Previous VM provider setting so we can return to it in case of failure.
     */
    private String mPreviousVMProviderKey = null;

    /**
     * Id of the dialog being currently shown.
     */
    private int mCurrentDialogId = 0;

    /**
     * Flag indicating that we are invoking settings for the voicemail provider programmatically
     * due to vm provider change.
     */
    private boolean mVMProviderSettingsForced = false;

    /**
     * Flag indicating that we are making changes to vm or fwd numbers
     * due to vm provider change.
     */
    private boolean mChangingVMorFwdDueToProviderChange = false;

    /**
     * True if we are in the process of vm & fwd number change and vm has already been changed.
     * This is used to decide what to do in case of rollback.
     */
    private boolean mVMChangeCompletedSuccessfully = false;

    /**
     * True if we had full or partial failure setting forwarding numbers and so need to roll them
     * back.
     */
    private boolean mFwdChangesRequireRollback = false;

    /**
     * Id of error msg to display to user once we are done reverting the VM provider to the previous
     * one.
     */
    private int mVMOrFwdSetError = 0;

    /** string to hold old voicemail number as it is being updated. */
    private String mOldVmNumber;

    // New call forwarding settings and vm number we will be setting
    // Need to save these since before we get to saving we need to asynchronously
    // query the existing forwarding settings.
    private CallForwardInfo[] mNewFwdSettings;
    private String mNewVMNumber;

    private boolean mForeground;

    @Override
    public void onPause() {
        super.onPause();
        mForeground = false;

        if (ImsManager.isVolteEnabledByPlatform(this) &&
                !mPhone.getContext().getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    /**
     * We have to pull current settings from the network for all kinds of
     * voicemail providers so we can tell whether we have to update them,
     * so use this bit to keep track of whether we're reading settings for the
     * default provider and should therefore save them out when done.
     */
    private boolean mReadingSettingsForDefaultProvider = false;

    /**
     * Used to indicate that the voicemail preference should be shown.
     */
    private boolean mShowVoicemailPreference = false;

    /**
     * Used to indicate that the voicemail setup screen should be shown.
     */
    private boolean mSetupVoicemail = false;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Enable/disable the TTY setting when in/out of a call (and if carrier doesn't
         * support VoLTE with TTY).
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);
            Preference pref = getPreferenceScreen().findPreference(BUTTON_TTY_KEY);
            if (pref != null) {
                pref.setEnabled(state == TelephonyManager.CALL_STATE_IDLE);
            }
        }
    };

    /*
     * Click Listeners, handle click based on objects attached to UI.
     */

    // Click listener for all toggle events
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSubMenuVoicemailSettings) {
            return true;
        } else if (preference == mButtonDTMF) {
            return true;
        } else if (preference == mButtonTTY) {
            return true;
        } else if (preference == mButtonAutoRetry) {
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.CALL_AUTO_RETRY,
                    mButtonAutoRetry.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mButtonHAC) {
            int hac = mButtonHAC.isChecked() ? 1 : 0;
            // Update HAC value in Settings database
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    Settings.System.HEARING_AID, hac);

            // Update HAC Value in AudioManager
            mAudioManager.setParameter(HAC_KEY, hac != 0 ? HAC_VAL_ON : HAC_VAL_OFF);
            return true;
        } else if (preference.getKey().equals(mVoicemailSettings.getKey())) {
            // Check key instead of comparing reference because closing the voicemail notification
            // ringtone dialog invokes onResume(), but leaves the old preference screen up,
            // TODO: Revert to checking reference after migrating voicemail to its own activity.
            if (DBG) log("onPreferenceTreeClick: Voicemail Settings Preference is clicked.");

            final Dialog dialog = ((PreferenceScreen) preference).getDialog();
            if (dialog != null) {
                dialog.getActionBar().setDisplayHomeAsUpEnabled(false);
            }

            if (preference.getIntent() != null) {
                if (DBG) log("Invoking cfg intent " + preference.getIntent().getPackage());

                // onActivityResult() will be responsible for resetting some of variables.
                this.startActivityForResult(preference.getIntent(), VOICEMAIL_PROVIDER_CFG_ID);
                return true;
            } else {
                if (DBG) log("onPreferenceTreeClick(). No intent; use default behavior in xml.");

                // onActivityResult() will not be called, so reset variables here.
                mPreviousVMProviderKey = VoicemailProviderListPreference.DEFAULT_KEY;
                mVMProviderSettingsForced = false;
                return false;
            }
        } else if (preference == mVoicemailSettingsScreen) {
            final Dialog dialog = mVoicemailSettingsScreen.getDialog();
            if (dialog != null) {
                dialog.getActionBar().setDisplayHomeAsUpEnabled(false);
            }
            return false;
        }
        return false;
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes.
     *
     * @param preference is the preference to be changed
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (DBG) log("onPreferenceChange: \"" + preference + "\" changed to \"" + objValue + "\"");

        if (preference == mButtonDTMF) {
            int index = mButtonDTMF.findIndexOfValue((String) objValue);
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, index);
        } else if (preference == mVoicemailProviders) {
            final String newProviderKey = (String) objValue;

            // If previous provider key and the new one is same, we don't need to handle it.
            if (mPreviousVMProviderKey.equals(newProviderKey)) {
                if (DBG) log("No change is made to the VM provider setting.");
                return true;
            }
            updateVMPreferenceWidgets(newProviderKey);

            final VoicemailProviderSettings newProviderSettings =
                    VoicemailProviderSettingsUtil.load(this, newProviderKey);

            // If the user switches to a voice mail provider and we have numbers stored for it we
            // will automatically change the phone's voice mail and forwarding number to the stored
            // ones. Otherwise we will bring up provider's configuration UI.
            if (newProviderSettings == null) {
                // Force the user into a configuration of the chosen provider
                Log.w(LOG_TAG, "Saved preferences not found - invoking config");
                mVMProviderSettingsForced = true;
                simulatePreferenceClick(mVoicemailSettings);
            } else {
                if (DBG) log("Saved preferences found - switching to them");
                // Set this flag so if we get a failure we revert to previous provider
                mChangingVMorFwdDueToProviderChange = true;
                saveVoiceMailAndForwardingNumber(newProviderKey, newProviderSettings);
            }
        } else if (preference.getKey().equals(mVoicemailNotificationVibrate.getKey())) {
            // Check key instead of comparing reference because closing the voicemail notification
            // ringtone dialog invokes onResume(), but leaves the old preference screen up,
            // TODO: Revert to checking reference after migrating voicemail to its own activity.
            VoicemailNotificationSettingsUtil.setVibrationEnabled(
                    mPhone, Boolean.TRUE.equals(objValue));
        } else if (preference == mEnableVideoCalling) {
            if (ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mPhone.getContext())) {
                PhoneGlobals.getInstance().phoneMgr.enableVideoCalling((boolean) objValue);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                DialogInterface.OnClickListener networkSettingsClickListener =
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(mPhone.getContext(),
                                        com.android.phone.MobileNetworkSettings.class));
                            }
                        };
                builder.setMessage(getResources().getString(
                                R.string.enable_video_calling_dialog_msg))
                        .setNeutralButton(getResources().getString(
                                R.string.enable_video_calling_dialog_settings),
                                networkSettingsClickListener)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return false;
            }
        }

        // Always let the preference setting proceed.
        return true;
    }

    @Override
    public void onDialogClosed(EditPhoneNumberPreference preference, int buttonClicked) {
        if (DBG) log("onDialogClosed: Button clicked is " + buttonClicked);

        if (buttonClicked == DialogInterface.BUTTON_NEGATIVE) {
            return;
        }

        if (preference == mSubMenuVoicemailSettings) {
            VoicemailProviderSettings newSettings = new VoicemailProviderSettings(
                    mSubMenuVoicemailSettings.getPhoneNumber(),
                    VoicemailProviderSettings.NO_FORWARDING);
            saveVoiceMailAndForwardingNumber(mVoicemailProviders.getKey(), newSettings);
        }
    }

    /**
     * Implemented for EditPhoneNumberPreference.GetDefaultNumberListener.
     * This method set the default values for the various
     * EditPhoneNumberPreference dialogs.
     */
    @Override
    public String onGetDefaultNumber(EditPhoneNumberPreference preference) {
        if (preference == mSubMenuVoicemailSettings) {
            // update the voicemail number field, which takes care of the
            // mSubMenuVoicemailSettings itself, so we should return null.
            if (DBG) log("updating default for voicemail dialog");
            updateVoiceNumberField();
            return null;
        }

        String vmDisplay = mPhone.getVoiceMailNumber();
        if (TextUtils.isEmpty(vmDisplay)) {
            // if there is no voicemail number, we just return null to
            // indicate no contribution.
            return null;
        }

        // Return the voicemail number prepended with "VM: "
        if (DBG) log("updating default for call forwarding dialogs");
        return getString(R.string.voicemail_abbreviated) + " " + vmDisplay;
    }

    private void switchToPreviousVoicemailProvider() {
        if (DBG) log("switchToPreviousVoicemailProvider " + mPreviousVMProviderKey);

        if (mPreviousVMProviderKey == null) {
            return;
        }

        if (mVMChangeCompletedSuccessfully || mFwdChangesRequireRollback) {
            showDialogIfForeground(VoicemailDialogUtil.VM_REVERTING_DIALOG);
            final VoicemailProviderSettings prevSettings =
                    VoicemailProviderSettingsUtil.load(this, mPreviousVMProviderKey);
            if (prevSettings == null) {
                Log.e(LOG_TAG, "VoicemailProviderSettings for the key \""
                        + mPreviousVMProviderKey + "\" is null but should be loaded.");
            }

            if (mVMChangeCompletedSuccessfully) {
                mNewVMNumber = prevSettings.getVoicemailNumber();
                Log.i(LOG_TAG, "VM change is already completed successfully."
                        + "Have to revert VM back to " + mNewVMNumber + " again.");
                mPhone.setVoiceMailNumber(
                        mPhone.getVoiceMailAlphaTag().toString(),
                        mNewVMNumber,
                        Message.obtain(mRevertOptionComplete, EVENT_VOICEMAIL_CHANGED));
            }

            if (mFwdChangesRequireRollback) {
                Log.i(LOG_TAG, "Requested to rollback forwarding changes.");

                final CallForwardInfo[] prevFwdSettings = prevSettings.getForwardingSettings();
                if (prevFwdSettings != null) {
                    Map<Integer, AsyncResult> results = mForwardingChangeResults;
                    resetForwardingChangeState();
                    for (int i = 0; i < prevFwdSettings.length; i++) {
                        CallForwardInfo fi = prevFwdSettings[i];
                        if (DBG) log("Reverting fwd #: " + i + ": " + fi.toString());
                        // Only revert the settings for which the update succeeded.
                        AsyncResult result = results.get(fi.reason);
                        if (result != null && result.exception == null) {
                            mExpectedChangeResultReasons.add(fi.reason);
                            CallForwardInfoUtil.setCallForwardingOption(mPhone, fi,
                                    mRevertOptionComplete.obtainMessage(
                                            EVENT_FORWARDING_CHANGED, i, 0));
                        }
                    }
                }
            }
        } else {
            if (DBG) log("No need to revert");
            onRevertDone();
        }
    }

    private void onRevertDone() {
        if (DBG) log("onRevertDone: Changing provider key back to " + mPreviousVMProviderKey);

        updateVMPreferenceWidgets(mPreviousVMProviderKey);
        updateVoiceNumberField();
        if (mVMOrFwdSetError != 0) {
            showDialogIfForeground(mVMOrFwdSetError);
            mVMOrFwdSetError = 0;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) {
            log("onActivityResult: requestCode: " + requestCode
                    + ", resultCode: " + resultCode
                    + ", data: " + data);
        }

        // there are cases where the contact picker may end up sending us more than one
        // request.  We want to ignore the request if we're not in the correct state.
        if (requestCode == VOICEMAIL_PROVIDER_CFG_ID) {
            boolean failure = false;

            // No matter how the processing of result goes lets clear the flag
            if (DBG) log("mVMProviderSettingsForced: " + mVMProviderSettingsForced);
            final boolean isVMProviderSettingsForced = mVMProviderSettingsForced;
            mVMProviderSettingsForced = false;

            String vmNum = null;
            if (resultCode != RESULT_OK) {
                if (DBG) log("onActivityResult: vm provider cfg result not OK.");
                failure = true;
            } else {
                if (data == null) {
                    if (DBG) log("onActivityResult: vm provider cfg result has no data");
                    failure = true;
                } else {
                    if (data.getBooleanExtra(SIGNOUT_EXTRA, false)) {
                        if (DBG) log("Provider requested signout");
                        if (isVMProviderSettingsForced) {
                            if (DBG) log("Going back to previous provider on signout");
                            switchToPreviousVoicemailProvider();
                        } else {
                            final String victim = mVoicemailProviders.getKey();
                            if (DBG) log("Relaunching activity and ignoring " + victim);
                            Intent i = new Intent(ACTION_ADD_VOICEMAIL);
                            i.putExtra(IGNORE_PROVIDER_EXTRA, victim);
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            this.startActivity(i);
                        }
                        return;
                    }
                    vmNum = data.getStringExtra(VM_NUMBER_EXTRA);
                    if (vmNum == null || vmNum.length() == 0) {
                        if (DBG) log("onActivityResult: vm provider cfg result has no vmnum");
                        failure = true;
                    }
                }
            }
            if (failure) {
                if (DBG) log("Failure in return from voicemail provider.");
                if (isVMProviderSettingsForced) {
                    switchToPreviousVoicemailProvider();
                }

                return;
            }
            mChangingVMorFwdDueToProviderChange = isVMProviderSettingsForced;
            final String fwdNum = data.getStringExtra(FWD_NUMBER_EXTRA);

            // TODO: It would be nice to load the current network setting for this and
            // send it to the provider when it's config is invoked so it can use this as default
            final int fwdNumTime = data.getIntExtra(FWD_NUMBER_TIME_EXTRA, 20);

            if (DBG) log("onActivityResult: cfg result has forwarding number " + fwdNum);
            saveVoiceMailAndForwardingNumber(mVoicemailProviders.getKey(),
                    new VoicemailProviderSettings(vmNum, fwdNum, fwdNumTime));
            return;
        }

        if (requestCode == VOICEMAIL_PREF_ID) {
            if (resultCode != RESULT_OK) {
                if (DBG) log("onActivityResult: contact picker result not OK.");
                return;
            }

            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(data.getData(),
                    NUM_PROJECTION, null, null, null);
                if ((cursor == null) || (!cursor.moveToFirst())) {
                    if (DBG) log("onActivityResult: bad contact data, no results found.");
                    return;
                }
                mSubMenuVoicemailSettings.onPickActivityResult(cursor.getString(0));
                return;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Wrapper around showDialog() that will silently do nothing if we're
     * not in the foreground.
     *
     * This is useful here because most of the dialogs we display from
     * this class are triggered by asynchronous events (like
     * success/failure messages from the telephony layer) and it's
     * possible for those events to come in even after the user has gone
     * to a different screen.
     */
    // TODO: this is too brittle: it's still easy to accidentally add new
    // code here that calls showDialog() directly (which will result in a
    // WindowManager$BadTokenException if called after the activity has
    // been stopped.)
    //
    // It would be cleaner to do the "if (mForeground)" check in one
    // central place, maybe by using a single Handler for all asynchronous
    // events (and have *that* discard events if we're not in the
    // foreground.)
    //
    // Unfortunately it's not that simple, since we sometimes need to do
    // actual work to handle these events whether or not we're in the
    // foreground (see the Handler code in mSetOptionComplete for
    // example.)
    //
    // TODO: It's a bit worrisome that we don't do anything in error cases when we're not in the
    // foreground. Consider displaying a toast instead.
    private void showDialogIfForeground(int id) {
        if (mForeground) {
            showDialog(id);
        }
    }

    private void dismissDialogSafely(int id) {
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
            // This is expected in the case where we were in the background
            // at the time we would normally have shown the dialog, so we didn't
            // show it.
        }
    }

    /**
     * TODO: Refactor to make it easier to understand what's done in the different stages.
     */
    private void saveVoiceMailAndForwardingNumber(
            String key, VoicemailProviderSettings newSettings) {
        if (DBG) log("saveVoiceMailAndForwardingNumber: " + newSettings.toString());
        mNewVMNumber = newSettings.getVoicemailNumber();
        mNewVMNumber = (mNewVMNumber == null) ? "" : mNewVMNumber;
        mNewFwdSettings = newSettings.getForwardingSettings();

        // No fwd settings on CDMA.
        boolean isCdma = mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA;
        if (isCdma) {
            if (DBG) log("ignoring forwarding setting since this is CDMA phone");
            mNewFwdSettings = VoicemailProviderSettings.NO_FORWARDING;
        }

        // Throw a warning if the voicemail is the same and we did not change forwarding.
        if (mNewVMNumber.equals(mOldVmNumber)
                && mNewFwdSettings == VoicemailProviderSettings.NO_FORWARDING) {
            showDialogIfForeground(VoicemailDialogUtil.VM_NOCHANGE_ERROR_DIALOG);
            return;
        }

        VoicemailProviderSettingsUtil.save(this, key, newSettings);
        mVMChangeCompletedSuccessfully = false;
        mFwdChangesRequireRollback = false;
        mVMOrFwdSetError = 0;

        // Don't read call forwarding settings if CDMA. Call forwarding is not supported by CDMA.
        if (!key.equals(mPreviousVMProviderKey) && !isCdma) {
            mReadingSettingsForDefaultProvider =
                    mPreviousVMProviderKey.equals(VoicemailProviderListPreference.DEFAULT_KEY);
            if (DBG) log("Reading current forwarding settings");
            int numSettingsReasons = VoicemailProviderSettings.FORWARDING_SETTINGS_REASONS.length;
            mForwardingReadResults = new CallForwardInfo[numSettingsReasons];
            for (int i = 0; i < mForwardingReadResults.length; i++) {
                mPhone.getCallForwardingOption(
                        VoicemailProviderSettings.FORWARDING_SETTINGS_REASONS[i],
                        mGetOptionComplete.obtainMessage(EVENT_FORWARDING_GET_COMPLETED, i, 0));
            }
            showDialogIfForeground(VoicemailDialogUtil.VM_FWD_READING_DIALOG);
        } else {
            saveVoiceMailAndForwardingNumberStage2();
        }

        // Refresh the MWI indicator if it is already showing.
        PhoneGlobals.getInstance().refreshMwiIndicator(mSubscriptionInfoHelper.getSubId());
    }

    private final Handler mGetOptionComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            switch (msg.what) {
                case EVENT_FORWARDING_GET_COMPLETED:
                    handleForwardingSettingsReadResult(result, msg.arg1);
                    break;
            }
        }
    };

    private void handleForwardingSettingsReadResult(AsyncResult ar, int idx) {
        if (DBG) Log.d(LOG_TAG, "handleForwardingSettingsReadResult: " + idx);

        Throwable error = null;
        if (ar.exception != null) {
            error = ar.exception;
            if (DBG) Log.d(LOG_TAG, "FwdRead: ar.exception=" + error.getMessage());
        }
        if (ar.userObj instanceof Throwable) {
            error = (Throwable) ar.userObj;
            if (DBG) Log.d(LOG_TAG, "FwdRead: userObj=" + error.getMessage());
        }

        // We may have already gotten an error and decided to ignore the other results.
        if (mForwardingReadResults == null) {
            if (DBG) Log.d(LOG_TAG, "Ignoring fwd reading result: " + idx);
            return;
        }

        // In case of error ignore other results, show an error dialog
        if (error != null) {
            if (DBG) Log.d(LOG_TAG, "Error discovered for fwd read : " + idx);
            mForwardingReadResults = null;
            dismissDialogSafely(VoicemailDialogUtil.VM_FWD_READING_DIALOG);
            showDialogIfForeground(VoicemailDialogUtil.FWD_GET_RESPONSE_ERROR_DIALOG);
            return;
        }

        // Get the forwarding info.
        mForwardingReadResults[idx] = CallForwardInfoUtil.getCallForwardInfo(
                (CallForwardInfo[]) ar.result,
                VoicemailProviderSettings.FORWARDING_SETTINGS_REASONS[idx]);

        // Check if we got all the results already
        boolean done = true;
        for (int i = 0; i < mForwardingReadResults.length; i++) {
            if (mForwardingReadResults[i] == null) {
                done = false;
                break;
            }
        }

        if (done) {
            if (DBG) Log.d(LOG_TAG, "Done receiving fwd info");
            dismissDialogSafely(VoicemailDialogUtil.VM_FWD_READING_DIALOG);

            if (mReadingSettingsForDefaultProvider) {
                VoicemailProviderSettingsUtil.save(mPhone.getContext(),
                        VoicemailProviderListPreference.DEFAULT_KEY,
                        new VoicemailProviderSettings(mOldVmNumber, mForwardingReadResults));
                mReadingSettingsForDefaultProvider = false;
            }
            saveVoiceMailAndForwardingNumberStage2();
        }
    }

    private void resetForwardingChangeState() {
        mForwardingChangeResults = new HashMap<Integer, AsyncResult>();
        mExpectedChangeResultReasons = new HashSet<Integer>();
    }

    // Called after we are done saving the previous forwarding settings if we needed.
    private void saveVoiceMailAndForwardingNumberStage2() {
        mForwardingChangeResults = null;
        mVoicemailChangeResult = null;
        if (mNewFwdSettings != VoicemailProviderSettings.NO_FORWARDING) {
            resetForwardingChangeState();
            for (int i = 0; i < mNewFwdSettings.length; i++) {
                CallForwardInfo fi = mNewFwdSettings[i];
                CallForwardInfo fiForReason =
                        CallForwardInfoUtil.infoForReason(mForwardingReadResults, fi.reason);
                final boolean doUpdate = CallForwardInfoUtil.isUpdateRequired(fiForReason, fi);

                if (doUpdate) {
                    if (DBG) log("Setting fwd #: " + i + ": " + fi.toString());
                    mExpectedChangeResultReasons.add(i);

                    CallForwardInfoUtil.setCallForwardingOption(mPhone, fi,
                            mSetOptionComplete.obtainMessage(
                                    EVENT_FORWARDING_CHANGED, fi.reason, 0));
                }
            }
            showDialogIfForeground(VoicemailDialogUtil.VM_FWD_SAVING_DIALOG);
        } else {
            if (DBG) log("Not touching fwd #");
            setVMNumberWithCarrier();
        }
    }

    private void setVMNumberWithCarrier() {
        if (DBG) log("save voicemail #: " + mNewVMNumber);

        mPhone.setVoiceMailNumber(
                mPhone.getVoiceMailAlphaTag().toString(),
                mNewVMNumber,
                Message.obtain(mSetOptionComplete, EVENT_VOICEMAIL_CHANGED));
    }

    /**
     * Callback to handle option update completions
     */
    private final Handler mSetOptionComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            boolean done = false;
            switch (msg.what) {
                case EVENT_VOICEMAIL_CHANGED:
                    mVoicemailChangeResult = result;
                    mVMChangeCompletedSuccessfully = isVmChangeSuccess();
                    done = true;
                    break;
                case EVENT_FORWARDING_CHANGED:
                    mForwardingChangeResults.put(msg.arg1, result);
                    if (result.exception != null) {
                        Log.w(LOG_TAG, "Error in setting fwd# " + msg.arg1 + ": " +
                                result.exception.getMessage());
                    }
                    if (isForwardingCompleted()) {
                        if (isFwdChangeSuccess()) {
                            if (DBG) log("Overall fwd changes completed ok, starting vm change");
                            setVMNumberWithCarrier();
                        } else {
                            Log.w(LOG_TAG, "Overall fwd changes completed in failure. " +
                                    "Check if we need to try rollback for some settings.");
                            mFwdChangesRequireRollback = false;
                            Iterator<Map.Entry<Integer,AsyncResult>> it =
                                mForwardingChangeResults.entrySet().iterator();
                            while (it.hasNext()) {
                                Map.Entry<Integer,AsyncResult> entry = it.next();
                                if (entry.getValue().exception == null) {
                                    // If at least one succeeded we have to revert
                                    Log.i(LOG_TAG, "Rollback will be required");
                                    mFwdChangesRequireRollback = true;
                                    break;
                                }
                            }
                            if (!mFwdChangesRequireRollback) {
                                Log.i(LOG_TAG, "No rollback needed.");
                            }
                            done = true;
                        }
                    }
                    break;
                default:
                    // TODO: should never reach this, may want to throw exception
            }

            if (done) {
                if (DBG) log("All VM provider related changes done");
                if (mForwardingChangeResults != null) {
                    dismissDialogSafely(VoicemailDialogUtil.VM_FWD_SAVING_DIALOG);
                }
                handleSetVmOrFwdMessage();
            }
        }
    };

    /**
     * Callback to handle option revert completions
     */
    private final Handler mRevertOptionComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult result = (AsyncResult) msg.obj;
            switch (msg.what) {
                case EVENT_VOICEMAIL_CHANGED:
                    if (DBG) log("VM revert complete msg");
                    mVoicemailChangeResult = result;
                    break;

                case EVENT_FORWARDING_CHANGED:
                    if (DBG) log("FWD revert complete msg ");
                    mForwardingChangeResults.put(msg.arg1, result);
                    if (result.exception != null) {
                        if (DBG) log("Error in reverting fwd# " + msg.arg1 + ": " +
                                result.exception.getMessage());
                    }
                    break;

                default:
                    // TODO: should never reach this, may want to throw exception
            }

            final boolean done = (!mVMChangeCompletedSuccessfully || mVoicemailChangeResult != null)
                    && (!mFwdChangesRequireRollback || isForwardingCompleted());
            if (done) {
                if (DBG) log("All VM reverts done");
                dismissDialogSafely(VoicemailDialogUtil.VM_REVERTING_DIALOG);
                onRevertDone();
            }
        }
    };

    /**
     * Return true if there is a change result for every reason for which we expect a result.
     */
    private boolean isForwardingCompleted() {
        if (mForwardingChangeResults == null) {
            return true;
        }

        for (Integer reason : mExpectedChangeResultReasons) {
            if (mForwardingChangeResults.get(reason) == null) {
                return false;
            }
        }

        return true;
    }

    private boolean isFwdChangeSuccess() {
        if (mForwardingChangeResults == null) {
            return true;
        }

        for (AsyncResult result : mForwardingChangeResults.values()) {
            Throwable exception = result.exception;
            if (exception != null) {
                String msg = exception.getMessage();
                msg = (msg != null) ? msg : "";
                Log.w(LOG_TAG, "Failed to change forwarding setting. Reason: " + msg);
                return false;
            }
        }
        return true;
    }

    private boolean isVmChangeSuccess() {
        if (mVoicemailChangeResult.exception != null) {
            String msg = mVoicemailChangeResult.exception.getMessage();
            msg = (msg != null) ? msg : "";
            Log.w(LOG_TAG, "Failed to change voicemail. Reason: " + msg);
            return false;
        }
        return true;
    }

    private void handleSetVmOrFwdMessage() {
        if (DBG) log("handleSetVMMessage: set VM request complete");

        if (!isFwdChangeSuccess()) {
            handleVmOrFwdSetError(VoicemailDialogUtil.FWD_SET_RESPONSE_ERROR_DIALOG);
        } else if (!isVmChangeSuccess()) {
            handleVmOrFwdSetError(VoicemailDialogUtil.VM_RESPONSE_ERROR_DIALOG);
        } else {
            handleVmAndFwdSetSuccess(VoicemailDialogUtil.VM_CONFIRM_DIALOG);
        }
    }

    /**
     * Called when Voicemail Provider or its forwarding settings failed. Rolls back partly made
     * changes to those settings and show "failure" dialog.
     *
     * @param dialogId ID of the dialog to show for the specific error case. Either
     *     {@link #FWD_SET_RESPONSE_ERROR_DIALOG} or {@link #VM_RESPONSE_ERROR_DIALOG}
     */
    private void handleVmOrFwdSetError(int dialogId) {
        if (mChangingVMorFwdDueToProviderChange) {
            mVMOrFwdSetError = dialogId;
            mChangingVMorFwdDueToProviderChange = false;
            switchToPreviousVoicemailProvider();
            return;
        }
        mChangingVMorFwdDueToProviderChange = false;
        showDialogIfForeground(dialogId);
        updateVoiceNumberField();
    }

    /**
     * Called when Voicemail Provider and its forwarding settings were successfully finished.
     * This updates a bunch of variables and show "success" dialog.
     */
    private void handleVmAndFwdSetSuccess(int dialogId) {
        if (DBG) log("handleVmAndFwdSetSuccess: key is " + mVoicemailProviders.getKey());

        mPreviousVMProviderKey = mVoicemailProviders.getKey();
        mChangingVMorFwdDueToProviderChange = false;
        showDialogIfForeground(dialogId);
        updateVoiceNumberField();
    }

    /**
     * Update the voicemail number from what we've recorded on the sim.
     */
    private void updateVoiceNumberField() {
        if (DBG) log("updateVoiceNumberField()");

        mOldVmNumber = mPhone.getVoiceMailNumber();
        if (TextUtils.isEmpty(mOldVmNumber)) {
            mSubMenuVoicemailSettings.setPhoneNumber("");
            mSubMenuVoicemailSettings.setSummary(getString(R.string.voicemail_number_not_set));
        } else {
            mSubMenuVoicemailSettings.setPhoneNumber(mOldVmNumber);
            mSubMenuVoicemailSettings.setSummary(mOldVmNumber);
        }
    }

    /*
     * Helper Methods for Activity class.
     * The initial query commands are split into two pieces now
     * for individual expansion.  This combined with the ability
     * to cancel queries allows for a much better user experience,
     * and also ensures that the user only waits to update the
     * data that is relevant.
     */

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        mCurrentDialogId = id;
    }

    // dialog creation method, called by showDialog()
    @Override
    protected Dialog onCreateDialog(int dialogId) {
        return VoicemailDialogUtil.getDialog(this, dialogId);
    }

    // This is a method implemented for DialogInterface.OnClickListener.
    // Used with the error dialog to close the app, voicemail dialog to just dismiss.
    // Close button is mapped to BUTTON_POSITIVE for the errors that close the activity,
    // while those that are mapped to BUTTON_NEUTRAL only move the preference focus.
    public void onClick(DialogInterface dialog, int which) {
        if (DBG) log("onClick: button clicked is " + which);

        dialog.dismiss();
        switch (which){
            case DialogInterface.BUTTON_NEGATIVE:
                if (mCurrentDialogId == VoicemailDialogUtil.FWD_GET_RESPONSE_ERROR_DIALOG) {
                    // We failed to get current forwarding settings and the user
                    // does not wish to continue.
                    switchToPreviousVoicemailProvider();
                }
                break;
            case DialogInterface.BUTTON_POSITIVE:
                if (mCurrentDialogId == VoicemailDialogUtil.FWD_GET_RESPONSE_ERROR_DIALOG) {
                    // We failed to get current forwarding settings but the user
                    // wishes to continue changing settings to the new vm provider
                    saveVoiceMailAndForwardingNumberStage2();
                } else {
                    finish();
                }
                return;
            default:
                // just let the dialog close and go back to the input
        }

        // In all dialogs, all buttons except BUTTON_POSITIVE lead to the end of user interaction
        // with settings UI. If we were called to explicitly configure voice mail then
        // we finish the settings activity here to come back to whatever the user was doing.
        if (getIntent().getAction().equals(ACTION_ADD_VOICEMAIL)) {
            finish();
        }
    }

    /*
     * Activity class methods
     */

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (DBG) log("onCreate: Intent is " + getIntent());

        // Make sure we are running as the primary user.
        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            Toast.makeText(this, R.string.call_settings_primary_user_only,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Show the voicemail preference in onResume if the calling intent specifies the
        // ACTION_ADD_VOICEMAIL action.
        mShowVoicemailPreference = (icicle == null) &&
                TextUtils.equals(getIntent().getAction(), ACTION_ADD_VOICEMAIL);
        mSetupVoicemail = mShowVoicemailPreference &&
                getIntent().getBooleanExtra(SETUP_VOICEMAIL_EXTRA, false);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mForeground = true;

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
        }

        addPreferencesFromResource(R.xml.call_feature_setting);

        TelecomManager telecomManager = TelecomManager.from(this);
        TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        Preference phoneAccountSettingsPreference = findPreference(PHONE_ACCOUNT_SETTINGS_KEY);
        if (telephonyManager.isMultiSimEnabled() || (telecomManager.getSimCallManagers().isEmpty()
                    && !SipUtil.isVoipSupported(mPhone.getContext()))) {
            getPreferenceScreen().removePreference(phoneAccountSettingsPreference);
        }

        PreferenceScreen prefSet = getPreferenceScreen();
        mSubMenuVoicemailSettings = (EditPhoneNumberPreference) findPreference(BUTTON_VOICEMAIL_KEY);
        mSubMenuVoicemailSettings.setParentActivity(this, VOICEMAIL_PREF_ID, this);
        mSubMenuVoicemailSettings.setDialogOnClosedListener(this);
        mSubMenuVoicemailSettings.setDialogTitle(R.string.voicemail_settings_number_label);

        mButtonDTMF = (ListPreference) findPreference(BUTTON_DTMF_KEY);
        mButtonAutoRetry = (CheckBoxPreference) findPreference(BUTTON_RETRY_KEY);
        mButtonHAC = (CheckBoxPreference) findPreference(BUTTON_HAC_KEY);
        mButtonTTY = (TtyModeListPreference) findPreference(
                getResources().getString(R.string.tty_mode_key));

        mVoicemailProviders = (VoicemailProviderListPreference) findPreference(
                BUTTON_VOICEMAIL_PROVIDER_KEY);
        mVoicemailProviders.init(mPhone, getIntent());
        mVoicemailProviders.setOnPreferenceChangeListener(this);
        mPreviousVMProviderKey = mVoicemailProviders.getValue();

        mVoicemailSettingsScreen =
                (PreferenceScreen) findPreference(VOICEMAIL_SETTING_SCREEN_PREF_KEY);
        mVoicemailSettings = (PreferenceScreen) findPreference(BUTTON_VOICEMAIL_SETTING_KEY);

        mVoicemailNotificationRingtone = (VoicemailRingtonePreference) findPreference(
                getResources().getString(R.string.voicemail_notification_ringtone_key));
        mVoicemailNotificationRingtone.init(mPhone);

        mVoicemailNotificationVibrate = (CheckBoxPreference) findPreference(
                getResources().getString(R.string.voicemail_notification_vibrate_key));
        mVoicemailNotificationVibrate.setOnPreferenceChangeListener(this);

        updateVMPreferenceWidgets(mVoicemailProviders.getValue());

        mEnableVideoCalling = (CheckBoxPreference) findPreference(ENABLE_VIDEO_CALLING_KEY);

        if (getResources().getBoolean(R.bool.dtmf_type_enabled)) {
            mButtonDTMF.setOnPreferenceChangeListener(this);
            int dtmf = Settings.System.getInt(getContentResolver(),
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, Constants.DTMF_TONE_TYPE_NORMAL);
            mButtonDTMF.setValueIndex(dtmf);
        } else {
            prefSet.removePreference(mButtonDTMF);
            mButtonDTMF = null;
        }

        if (getResources().getBoolean(R.bool.auto_retry_enabled)) {
            mButtonAutoRetry.setOnPreferenceChangeListener(this);
            int autoretry = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.CALL_AUTO_RETRY, 0);
            mButtonAutoRetry.setChecked(autoretry != 0);
        } else {
            prefSet.removePreference(mButtonAutoRetry);
            mButtonAutoRetry = null;
        }

        if (getResources().getBoolean(R.bool.hac_enabled)) {
            mButtonHAC.setOnPreferenceChangeListener(this);
            int hac = Settings.System.getInt(getContentResolver(), Settings.System.HEARING_AID, 0);
            mButtonHAC.setChecked(hac != 0);
        } else {
            prefSet.removePreference(mButtonHAC);
            mButtonHAC = null;
        }

        if (!telephonyManager.isMultiSimEnabled() && telecomManager.isTtySupported()) {
            mButtonTTY.init();
        } else {
            prefSet.removePreference(mButtonTTY);
            mButtonTTY = null;
        }

        if (!getResources().getBoolean(R.bool.world_phone)) {
            Preference cdmaOptions = prefSet.findPreference(BUTTON_CDMA_OPTIONS);
            prefSet.removePreference(cdmaOptions);

            // TODO: Support MSIM for this preference option.
            Preference gsmOptions = prefSet.findPreference(BUTTON_GSM_UMTS_OPTIONS);
            prefSet.removePreference(gsmOptions);

            int phoneType = mPhone.getPhoneType();
            Preference fdnButton = prefSet.findPreference(BUTTON_FDN_KEY);
            boolean shouldHideCarrierSettings = Settings.Global.getInt(
                    getContentResolver(), Settings.Global.HIDE_CARRIER_NETWORK_SETTINGS, 0) == 1;
            if (shouldHideCarrierSettings) {
                prefSet.removePreference(fdnButton);
                if (mButtonDTMF != null) {
                    prefSet.removePreference(mButtonDTMF);
                }
            } else {
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    prefSet.removePreference(fdnButton);

                    if (!getResources().getBoolean(R.bool.config_voice_privacy_disable)) {
                        addPreferencesFromResource(R.xml.cdma_call_privacy);
                    }
                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    fdnButton.setIntent(mSubscriptionInfoHelper.getIntent(FdnSetting.class));

                    if (getResources().getBoolean(R.bool.config_additional_call_setting)) {
                        addPreferencesFromResource(R.xml.gsm_umts_call_options);

                        Preference callForwardingPref = prefSet.findPreference(CALL_FORWARDING_KEY);
                        callForwardingPref.setIntent(mSubscriptionInfoHelper.getIntent(
                                GsmUmtsCallForwardOptions.class));

                        Preference additionalGsmSettingsPref =
                                prefSet.findPreference(ADDITIONAL_GSM_SETTINGS_KEY);
                        additionalGsmSettingsPref.setIntent(mSubscriptionInfoHelper.getIntent(
                                GsmUmtsAdditionalCallOptions.class));
                    }
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
            }
        }

        // check the intent that started this activity and pop up the voicemail
        // dialog if we've been asked to.
        // If we have at least one non default VM provider registered then bring up
        // the selection for the VM provider, otherwise bring up a VM number dialog.
        // We only bring up the dialog the first time we are called (not after orientation change)
        if (mShowVoicemailPreference) {
            if (DBG) log("ACTION_ADD_VOICEMAIL Intent is thrown");
            if (mSetupVoicemail) {
                simulatePreferenceClick(mVoicemailSettingsScreen);
                mSetupVoicemail = false;
            } else if (mVoicemailProviders.hasMoreThanOneVoicemailProvider()) {
                if (DBG) log("Voicemail data has more than one provider.");
                simulatePreferenceClick(mVoicemailProviders);
            } else {
                onPreferenceChange(mVoicemailProviders, VoicemailProviderListPreference.DEFAULT_KEY);
                mVoicemailProviders.setValue(VoicemailProviderListPreference.DEFAULT_KEY);
            }
            mShowVoicemailPreference = false;
        }

        updateVoiceNumberField();
        mVMProviderSettingsForced = false;

        mVoicemailNotificationVibrate.setChecked(
                VoicemailNotificationSettingsUtil.isVibrationEnabled(mPhone));

        if (ImsManager.isVtEnabledByPlatform(mPhone.getContext()) && ENABLE_VT_FLAG) {
            boolean currentValue =
                    ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mPhone.getContext())
                    ? PhoneGlobals.getInstance().phoneMgr.isVideoCallingEnabled() : false;
            mEnableVideoCalling.setChecked(currentValue);
            mEnableVideoCalling.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(mEnableVideoCalling);
        }

        if (ImsManager.isVolteEnabledByPlatform(this) &&
                !mPhone.getContext().getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        setIntent(newIntent);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    /**
     * Updates the look of the VM preference widgets based on current VM provider settings.
     * Note that the provider name is loaded fxrorm the found activity via loadLabel in
     * {@link VoicemailProviderListPreference#initVoiceMailProviders()} in order for it to be
     * localizable.
     */
    private void updateVMPreferenceWidgets(String currentProviderSetting) {
        final String key = currentProviderSetting;
        final VoicemailProvider provider = mVoicemailProviders.getVoicemailProvider(key);

        /* This is the case when we are coming up on a freshly wiped phone and there is no
         persisted value for the list preference mVoicemailProviders.
         In this case we want to show the UI asking the user to select a voicemail provider as
         opposed to silently falling back to default one. */
        if (provider == null) {
            if (DBG) log("updateVMPreferenceWidget: key: " + key + " -> null.");

            mVoicemailProviders.setSummary(getString(R.string.sum_voicemail_choose_provider));
            mVoicemailSettings.setEnabled(false);
            mVoicemailSettings.setIntent(null);
            mVoicemailNotificationVibrate.setEnabled(false);
        } else {
            if (DBG) log("updateVMPreferenceWidget: key: " + key + " -> " + provider.toString());

            final String providerName = provider.name;
            mVoicemailProviders.setSummary(providerName);
            mVoicemailSettings.setEnabled(true);
            mVoicemailSettings.setIntent(provider.intent);
            mVoicemailNotificationVibrate.setEnabled(true);
        }
    }


    /**
     * Simulates user clicking on a passed preference.
     * Usually needed when the preference is a dialog preference and we want to invoke
     * a dialog for this preference programmatically.
     * TODO: figure out if there is a cleaner way to cause preference dlg to come up
     */
    private void simulatePreferenceClick(Preference preference) {
        // Go through settings until we find our setting
        // and then simulate a click on it to bring up the dialog
        final ListAdapter adapter = getPreferenceScreen().getRootAdapter();
        for (int idx = 0; idx < adapter.getCount(); idx++) {
            if (adapter.getItem(idx) == preference) {
                getPreferenceScreen().onItemClick(this.getListView(),
                        null, idx, adapter.getItemId(idx));
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Finish current Activity and go up to the top level Settings ({@link CallFeaturesSetting}).
     * This is useful for implementing "HomeAsUp" capability for second-level Settings.
     */
    public static void goUpToTopLevelSetting(
            Activity activity, SubscriptionInfoHelper subscriptionInfoHelper) {
        Intent intent = subscriptionInfoHelper.getIntent(CallFeaturesSetting.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
