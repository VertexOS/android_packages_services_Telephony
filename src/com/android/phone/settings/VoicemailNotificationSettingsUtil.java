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

package com.android.phone.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.phone.R;

public class VoicemailNotificationSettingsUtil {
    private static final String VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY =
            "button_voicemail_notification_ringtone_key";
    private static final String VOICEMAIL_NOTIFICATION_VIBRATION_SHARED_PREFS_KEY =
            "button_voicemail_notification_vibrate_key";

    // Old voicemail notification vibration string constants used for migration.
    private static final String OLD_VOICEMAIL_VIBRATE_WHEN_SHARED_PREFS_KEY =
            "button_voicemail_notification_vibrate_when_key";
    private static final String OLD_VOICEMAIL_RINGTONE_SHARED_PREFS_KEY =
            "button_voicemail_notification_ringtone_key";
    private static final String OLD_VOICEMAIL_VIBRATION_ALWAYS = "always";
    private static final String OLD_VOICEMAIL_VIBRATION_NEVER = "never";

    public static void setVibrationEnabled(Context context, boolean isEnabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(VOICEMAIL_NOTIFICATION_VIBRATION_SHARED_PREFS_KEY, isEnabled);
        editor.commit();
    }

    public static boolean isVibrationEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        migrateVoicemailVibrationSettingsIfNeeded(prefs);
        return prefs.getBoolean(
                VOICEMAIL_NOTIFICATION_VIBRATION_SHARED_PREFS_KEY, false /* defValue */);
    }

    /**
     * Migrate settings from OLD_VIBRATE_WHEN_KEY to VOICEMAIL_NOTIFICATION_VIBRATE_KEY if the
     * latter does not exist.
     */
    private static void migrateVoicemailVibrationSettingsIfNeeded(SharedPreferences prefs) {
        if (!prefs.contains(VOICEMAIL_NOTIFICATION_VIBRATION_SHARED_PREFS_KEY)) {
            // If vibrateWhen is always, then voicemailVibrate should be true.
            // If it is "only in silent mode", or "never", then voicemailVibrate should be false.
            String vibrateWhen = prefs.getString(
                    OLD_VOICEMAIL_VIBRATE_WHEN_SHARED_PREFS_KEY, OLD_VOICEMAIL_VIBRATION_NEVER);
            boolean voicemailVibrate = vibrateWhen.equals(OLD_VOICEMAIL_VIBRATION_ALWAYS);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(VOICEMAIL_NOTIFICATION_VIBRATION_SHARED_PREFS_KEY, voicemailVibrate)
                    .remove(OLD_VOICEMAIL_VIBRATE_WHEN_SHARED_PREFS_KEY)
                    .commit();
        }
    }

    public static void setRingtoneUri(Context context, Uri ringtoneUri) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String ringtoneUriStr = ringtoneUri != null ? ringtoneUri.toString() : "";

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY, ringtoneUriStr);
        editor.commit();
    }

    public static Uri getRingtoneUri(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.contains(VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY)) {
            return Settings.System.DEFAULT_NOTIFICATION_URI;
        }
        String uriString = prefs.getString(
                VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY, null /* defValue */);
        return !TextUtils.isEmpty(uriString) ? Uri.parse(uriString) : null;
    }

    public static String getRingtoneSharedPreferencesKey() {
        return VOICEMAIL_NOTIFICATION_RINGTONE_SHARED_PREFS_KEY;
    }
}
