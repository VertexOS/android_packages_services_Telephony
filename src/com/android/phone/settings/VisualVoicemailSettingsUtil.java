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

import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.sms.StatusMessage;

/**
 * Save visual voicemail login values in shared preferences to be retrieved later.
 */
public class VisualVoicemailSettingsUtil {
    private static final String VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX =
            "visual_voicemail_";

    private static final String IS_ENABLED_KEY = "is_enabled";

    public static void setVisualVoicemailEnabled(Context context, PhoneAccountHandle phoneAccount,
            boolean isEnabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(
                getVisualVoicemailSharedPrefsKey(IS_ENABLED_KEY, phoneAccount), isEnabled);
        editor.commit();
    }

    public static boolean getVisualVoicemailEnabled(Context context,
            PhoneAccountHandle phoneAccount) {
        if (phoneAccount == null) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(getVisualVoicemailSharedPrefsKey(IS_ENABLED_KEY, phoneAccount),
                true);
    }

    public static void setSourceCredentialsFromStatusMessage(Context context,
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

    public static String getCredentialForSource(Context context, String key,
            PhoneAccountHandle phoneAccount) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(getVisualVoicemailSharedPrefsKey(key, phoneAccount), null);
    }

    private static String getVisualVoicemailSharedPrefsKey(String key,
            PhoneAccountHandle phoneAccount) {
        return VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX + key + "_" + phoneAccount.getId();
    }
}
