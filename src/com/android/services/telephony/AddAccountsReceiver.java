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

package com.android.services.telephony;

import com.android.phone.R;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecomm.PhoneAccount;
import android.telecomm.PhoneAccountMetadata;
import android.telecomm.TelecommManager;

public class AddAccountsReceiver extends BroadcastReceiver {

    private static String SCHEME_TEL = "tel";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(this, "onReceive");
        try {
            TelecommManager telecommManager = TelecommManager.from(context);
            telecommManager.clearAccounts(context.getPackageName());
            PhoneAccountMetadata[] accounts = makeAccounts(context);
            for (int i = 0; i < accounts.length; i++) {
                telecommManager.registerPhoneAccount(accounts[i]);
            }
        } catch (Exception e) {
            Log.e(this, e, "onReceive");
            throw e;
        }
    }

    public static PhoneAccountMetadata[] makeAccounts(Context context) {
        ComponentName componentName = new ComponentName(
                context.getPackageName(),
                TelephonyConnectionService.class.getName());
        return new PhoneAccountMetadata[]{
                new PhoneAccountMetadata(
                        new PhoneAccount(componentName, "sim_0"),
                        Uri.fromParts(SCHEME_TEL, "650-555-1212", null),
                        PhoneAccountMetadata.CAPABILITY_CALL_PROVIDER,
                        R.drawable.fab_ic_call,
                        "Zero SIM Account",
                        "Short description for SIM card zero",
                        false),
                new PhoneAccountMetadata(
                        new PhoneAccount(componentName, "sim_1"),
                        Uri.fromParts(SCHEME_TEL, "650-555-1234", null),
                        PhoneAccountMetadata.CAPABILITY_CALL_PROVIDER,
                        R.drawable.fab_ic_call,
                        "One SIM Account",
                        "Short description for SIM card one",
                        false)
        };
    }
}
