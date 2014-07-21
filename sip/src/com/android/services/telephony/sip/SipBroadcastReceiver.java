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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.UserHandle;
import android.telecomm.TelecommManager;
import android.util.Log;

import java.util.List;

/**
 * Broadcast receiver that handles SIP-related intents.
 */
public class SipBroadcastReceiver extends BroadcastReceiver {
    private static final String PREFIX = "[SipBroadcastReceiver] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */

    @Override
    public void onReceive(Context context, final Intent intent) {
        String action = intent.getAction();

        if (!SipUtil.isVoipSupported(context)) {
            if (VERBOSE) log("SIP VOIP not supported: " + action);
            return;
        }

        if (action.equals(SipManager.ACTION_SIP_INCOMING_CALL)) {
            takeCall(context, intent);
        } else if (action.equals(SipManager.ACTION_SIP_SERVICE_UP)) {
            registerAllProfiles(context);
        } else {
            if (VERBOSE) log("onReceive, action not processed: " + action);
        }
    }

    private void takeCall(Context context, Intent intent) {
        if (VERBOSE) log("takeCall, intent: " + intent);

        Bundle extras = new Bundle();
        extras.putParcelable(SipUtil.EXTRA_INCOMING_CALL_INTENT, intent);

        TelecommManager.from(context).addNewIncomingCall(
                SipConnectionService.getPhoneAccountHandle(context), extras);
    }

    private void registerAllProfiles(final Context context) {
        if (VERBOSE) log("registerAllProfiles, start auto registration");
        final SipSharedPreferences sipSharedPreferences = new SipSharedPreferences(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                SipManager sipManager = SipManager.newInstance(context);
                SipProfileDb profileDb = new SipProfileDb(context);
                String primaryProfile = sipSharedPreferences.getPrimaryAccount();

                List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();

                for (SipProfile profile : sipProfileList) {
                    boolean isPrimaryProfile = profile.getUriString().equals(primaryProfile);
                    if (profile.getAutoRegistration() || isPrimaryProfile) {
                        if (VERBOSE) log("registerAllProfiles, profile: " + profile);
                        try {
                            sipManager.open(profile,
                                    SipUtil.createIncomingCallPendingIntent(context), null);
                        } catch (SipException e) {
                            log("registerAllProfiles, profile: " + profile.getProfileName() +
                                    ", exception: " + e);
                        }
                    }
                }
            }}
        ).start();
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
