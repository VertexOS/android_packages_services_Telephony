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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipManager;
import android.telecomm.TelecommManager;

public class SipUtil {
    static final String LOG_TAG = "SIP";
    static final String EXTRA_INCOMING_CALL_INTENT =
            "com.android.services.telephony.sip.incoming_call_intent";
    static final String GATEWAY_PROVIDER_PACKAGE =
            "com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE";
    static final String SCHEME_TEL = "tel";
    static final String SCHEME_SIP = "sip";

    private static boolean sIsVoipSupported;
    private static boolean sIsVoipSupportedInitialized;

    private SipUtil() {
    }

    public static boolean isVoipSupported(Context context) {
        if (!sIsVoipSupportedInitialized) {
            sIsVoipSupported = SipManager.isVoipSupported(context) &&
                    context.getResources().getBoolean(
                            com.android.internal.R.bool.config_built_in_sip_phone) &&
                    context.getResources().getBoolean(
                            com.android.internal.R.bool.config_voice_capable);
        }
        return sIsVoipSupported;
    }

    static PendingIntent createIncomingCallPendingIntent(Context context) {
        Intent intent = new Intent(context, SipBroadcastReceiver.class);
        intent.setAction(SipManager.ACTION_SIP_INCOMING_CALL);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static boolean isPhoneIdle(Context context) {
        TelecommManager manager = (TelecommManager) context.getSystemService(
                Context.TELECOMM_SERVICE);
        if (manager != null) {
            return !manager.isInAPhoneCall();
        }
        return true;
    }
}
