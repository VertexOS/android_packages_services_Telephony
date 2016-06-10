/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telephony.Phone;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.sms.StatusMessage;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Save visual voicemail login values and whether or not a particular account is enabled in shared
 * preferences to be retrieved later.
 * Because a voicemail source is tied 1:1 to a phone account, the phone account handle is used in
 * the key for each voicemail source and the associated data.
 */
public class VisualVoicemailSettingsUtil {
    private static final String VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX =
            "visual_voicemail_";

    private static final String IS_ENABLED_KEY = "is_enabled";
    // Record the timestamp of the last full sync so that duplicate syncs can be reduced.
    private static final String LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp";
    // Constant indicating that there has never been a full sync.
    public static final long NO_PRIOR_FULL_SYNC = -1;

    // Setting for how often retries should be done.
    private static final String SYNC_RETRY_INTERVAL = "sync_retry_interval";
    private static final long MAX_SYNC_RETRY_INTERVAL_MS = 86400000;   // 24 hours
    private static final long DEFAULT_SYNC_RETRY_INTERVAL_MS = 900000; // 15 minutes

    /* package */
    static void setVisualVoicemailEnabled(Context context, PhoneAccountHandle phoneAccount,
            boolean isEnabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .putBoolean(getVisualVoicemailSharedPrefsKey(IS_ENABLED_KEY, phoneAccount),
                        isEnabled)
                .apply();
    }

    public static boolean isVisualVoicemailEnabled(Context context,
            PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }
        if (!context.getResources().getBoolean(R.bool.allow_visual_voicemail)) {
            return false;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getVisualVoicemailSharedPrefsKey(IS_ENABLED_KEY, phoneAccount);
        if (prefs.contains(key)) {
            // isEnableByDefault is a bit expensive, so don't use it as default value of
            // getBoolean(). The "false" here should never be actually used.
            return prefs.getBoolean(key, false);
        }
        return new OmtpVvmCarrierConfigHelper(context,
                PhoneAccountHandleConverter.toSubId(phoneAccount)).isEnabledByDefault();
    }

    public static boolean isVisualVoicemailEnabled(Phone phone) {
        return isVisualVoicemailEnabled(phone.getContext(),
                PhoneUtils.makePstnPhoneAccountHandle(phone));
    }

    /**
     * Whether the client enabled status is explicitly set by user or by default(Whether carrier VVM
     * app is installed). This is used to determine whether to disable the client when the carrier
     * VVM app is installed. If the carrier VVM app is installed the client should give priority to
     * it if the settings are not touched.
     */
    public static boolean isVisualVoicemailUserSet(Context context,
            PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.contains(getVisualVoicemailSharedPrefsKey(IS_ENABLED_KEY, phoneAccount));
    }

    public static void setVisualVoicemailCredentialsFromStatusMessage(Context context,
            PhoneAccountHandle phoneAccount, StatusMessage message) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(
                getVisualVoicemailSharedPrefsKey(OmtpConstants.IMAP_PORT, phoneAccount),
                message.getImapPort());
        editor.putString(
                getVisualVoicemailSharedPrefsKey(OmtpConstants.SERVER_ADDRESS, phoneAccount),
                message.getServerAddress());
        editor.putString(
                getVisualVoicemailSharedPrefsKey(OmtpConstants.IMAP_USER_NAME, phoneAccount),
                message.getImapUserName());
        editor.putString(
                getVisualVoicemailSharedPrefsKey(OmtpConstants.IMAP_PASSWORD, phoneAccount),
                message.getImapPassword());
        editor.commit();
    }

    public static String getVisualVoicemailCredentials(Context context, String key,
            PhoneAccountHandle phoneAccount) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(getVisualVoicemailSharedPrefsKey(key, phoneAccount), null);
    }

    public static long getVisualVoicemailRetryInterval(Context context,
            PhoneAccountHandle phoneAccount) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(getVisualVoicemailSharedPrefsKey(SYNC_RETRY_INTERVAL, phoneAccount),
                DEFAULT_SYNC_RETRY_INTERVAL_MS);
    }

    public static void resetVisualVoicemailRetryInterval(Context context,
            PhoneAccountHandle phoneAccount) {
        setVisualVoicemailRetryInterval(context, phoneAccount, DEFAULT_SYNC_RETRY_INTERVAL_MS);
    }

    public static void setVisualVoicemailRetryInterval(Context context,
            PhoneAccountHandle phoneAccount, long interval) {
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putLong(getVisualVoicemailSharedPrefsKey(SYNC_RETRY_INTERVAL, phoneAccount),
                Math.min(interval, MAX_SYNC_RETRY_INTERVAL_MS));
        editor.commit();
    }

    public static void setVisualVoicemailLastFullSyncTime(Context context,
            PhoneAccountHandle phoneAccount, long timestamp) {
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putLong(getVisualVoicemailSharedPrefsKey(LAST_FULL_SYNC_TIMESTAMP, phoneAccount),
                timestamp);
        editor.commit();

    }

    public static long getVisualVoicemailLastFullSyncTime(Context context,
            PhoneAccountHandle phoneAccount) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(
                getVisualVoicemailSharedPrefsKey(LAST_FULL_SYNC_TIMESTAMP, phoneAccount),
                NO_PRIOR_FULL_SYNC);
    }

    public static String getVisualVoicemailSharedPrefsKey(String key,
            PhoneAccountHandle phoneAccount) {
        return VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX + key + "_" + phoneAccount.getId();
    }
}
