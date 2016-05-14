/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import java.util.Arrays;
import java.util.Set;

/**
 * Stores the config values needed for visual voicemail sms filtering. The values from
 * OmtpVvmCarrierConfigHelper are stored here during activation instead. These values are read and
 * written through TelephonyManager.
 */
public class VisualVoicemailSmsFilterConfig {

    private static final String VVM_SMS_FILTER_COFIG_SHARED_PREFS_KEY_PREFIX =
            "vvm_sms_filter_config_";
    private static final String ENABLED_KEY = "_enabled";
    private static final String PREFIX_KEY = "_prefix";
    private static final String ORIGINATING_NUMBERS_KEY = "_originating_numbers";
    private static final String DESTINATION_PORT_KEY = "_destination_port";

    public static void setVisualVoicemailSmsFilterEnabled(Context context, int subId,
            boolean value) {
        setBoolean(context, subId, ENABLED_KEY, value);
    }

    public static boolean isVisualVoicemailSmsFilterEnabled(Context context, String packageName,
            int subId) {
        return getBoolean(context, packageName, subId, ENABLED_KEY);
    }

    public static void setVisualVoicemailSmsFilterClientPrefix(Context context, int subId,
            String prefix) {
        setString(context, subId, PREFIX_KEY, prefix);
    }

    public static String getVisualVoicemailSmsFilterClientPrefix(Context context,
            String packageName, int subId) {
        return getString(context, packageName, subId, PREFIX_KEY);
    }

    public static void setVisualVoicemailSmsFilterOriginatingNumbers(Context context, int subId,
            String[] numbers) {
        ArraySet<String> set = new ArraySet<>();
        set.addAll(Arrays.asList(numbers));
        setStringSet(context, subId, ORIGINATING_NUMBERS_KEY, set);
    }

    public static String[] getVisualVoicemailSmsFilterOriginatingNumbers(Context context,
            String packageName, int subId) {
        Set<String> numbers = getStringSet(context, packageName, subId, ORIGINATING_NUMBERS_KEY);
        return numbers.toArray(new String[numbers.size()]);
    }

    public static void setVisualVoicemailSmsFilterDestinationPort(Context context, int subId,
            int port) {
        setInt(context, subId, DESTINATION_PORT_KEY, port);
    }

    public static int getVisualVoicemailSmsFilterDestinationPort(Context context,
            String packageName, int subId) {
        return getInt(context, packageName, subId, DESTINATION_PORT_KEY,
                TelephonyManager.VVM_SMS_FILTER_DESTINATION_PORT_ANY);
    }

    private static int getInt(Context context, String packageName, int subId, String key,
            int defaultValue) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getInt(makePerPhoneAccountKey(packageName, subId, key), defaultValue);
    }

    private static void setInt(Context context, int subId, String key, int value) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putInt(makePerPhoneAccountKey(context.getOpPackageName(), subId, key), value);
        editor.apply();
    }

    private static boolean getBoolean(Context context, String packageName, int subId, String key) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getBoolean(makePerPhoneAccountKey(packageName, subId, key), false);
    }

    private static void setBoolean(Context context, int subId, String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putBoolean(makePerPhoneAccountKey(context.getOpPackageName(), subId, key), value);
        editor.apply();
    }

    private static String getString(Context context, String packageName, int subId, String key) {
        SharedPreferences prefs = getSharedPreferences(context);
        return prefs.getString(makePerPhoneAccountKey(packageName, subId, key), null);
    }

    private static void setString(Context context, int subId, String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(makePerPhoneAccountKey(context.getOpPackageName(), subId, key), value);
        editor.apply();
    }

    private static Set<String> getStringSet(Context context, String packageName, int subId,
            String key) {
        return getSharedPreferences(context)
                .getStringSet(makePerPhoneAccountKey(packageName, subId, key), null);
    }

    private static void setStringSet(Context context, int subId, String key, Set<String> value) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putStringSet(makePerPhoneAccountKey(context.getOpPackageName(), subId, key), value);
        editor.apply();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context.createDeviceProtectedStorageContext());
    }

    private static String makePerPhoneAccountKey(String packageName, int subId, String key) {
        // TODO: make sure subId is persistent enough to serve as a key
        return VVM_SMS_FILTER_COFIG_SHARED_PREFS_KEY_PREFIX + packageName + "_"
                + subId + key;
    }
}
