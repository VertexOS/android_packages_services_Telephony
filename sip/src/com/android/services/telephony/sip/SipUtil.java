/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import com.android.phone.R;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.provider.Settings;
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.TelecommManager;
import android.text.TextUtils;

import java.util.ArrayList;

public class SipUtil {
    static final String LOG_TAG = "SIP";
    static final String EXTRA_INCOMING_CALL_INTENT =
            "com.android.services.telephony.sip.incoming_call_intent";
    static final String EXTRA_PHONE_ACCOUNT =
            "com.android.services.telephony.sip.phone_account";

    private SipUtil() {
    }

    public static boolean isVoipSupported(Context context) {
        return SipManager.isVoipSupported(context) &&
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_built_in_sip_phone) &&
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_voice_capable);
    }

    static PendingIntent createIncomingCallPendingIntent(
            Context context, String sipUri) {
        Intent intent = new Intent(context, SipBroadcastReceiver.class);
        intent.setAction(SipManager.ACTION_SIP_INCOMING_CALL);
        intent.putExtra(EXTRA_PHONE_ACCOUNT, SipUtil.createAccountHandle(context, sipUri));
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static boolean isPhoneIdle(Context context) {
        TelecommManager manager = (TelecommManager) context.getSystemService(
                Context.TELECOMM_SERVICE);
        if (manager != null) {
            return !manager.isInCall();
        }
        return true;
    }

    /**
     * Creates a {@link PhoneAccountHandle} from the specified SIP URI.
     */
    static PhoneAccountHandle createAccountHandle(Context context, String sipUri) {
        return new PhoneAccountHandle(
                new ComponentName(context, SipConnectionService.class), sipUri);
    }

    /**
     * Determines the SIP Uri for a specified {@link PhoneAccountHandle}.
     *
     * @param phoneAccountHandle The {@link PhoneAccountHandle}.
     * @return The SIP Uri.
     */
    static String getSipUriFromPhoneAccount(PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle == null) {
            return null;
        }

        String sipUri = phoneAccountHandle.getId();
        if (TextUtils.isEmpty(sipUri)) {
            return null;
        }
        return sipUri;
    }

    /**
     * Determines if the {@link android.telecomm.PhoneAccount} associated with a {@link SipProfile}
     * is enabled.
     *
     * @param context The {@link Context}.
     * @param profile The {@link SipProfile}.
     * @return {@code True} if the {@code PhoneAccount} is enabled.
     */
    static boolean isPhoneAccountEnabled(Context context, SipProfile profile) {
        PhoneAccount phoneAccount = TelecommManager.from(context)
                .getPhoneAccount(SipUtil.createAccountHandle(context, profile.getUriString()));
        return phoneAccount != null && phoneAccount.isEnabled();
    }

    /**
     * Creates a PhoneAccount for a SipProfile.
     *
     * @param context The context
     * @param profile The SipProfile.
     * @return The PhoneAccount.
     */
    static PhoneAccount createPhoneAccount(Context context, SipProfile profile) {

        PhoneAccountHandle accountHandle =
                SipUtil.createAccountHandle(context, profile.getUriString());

        final ArrayList<String> supportedUriSchemes = new ArrayList<String>();
        supportedUriSchemes.add(PhoneAccount.SCHEME_SIP);
        if (useSipForPstnCalls(context)) {
            supportedUriSchemes.add(PhoneAccount.SCHEME_TEL);
        }

        PhoneAccount.Builder builder = PhoneAccount.builder(accountHandle, profile.getDisplayName())
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setAddress(Uri.parse(profile.getUriString()))
                .setShortDescription(profile.getDisplayName())
                .setIconResId(R.drawable.ic_dialer_sip_black_24dp)
                .setSupportedUriSchemes(supportedUriSchemes);

        return builder.build();
    }

    /**
     * Determines if the user has chosen to use SIP for PSTN calls as well as SIP calls.
     * @param context The context.
     * @return {@code True} if SIP should be used for PSTN calls.
     */
    private static boolean useSipForPstnCalls(Context context) {
        final SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(context);
        return sipSharedPreferences.getSipCallOption().equals(Settings.System.SIP_ALWAYS);
    }
}
