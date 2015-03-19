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
package com.android.phone.vvm.omtp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;

/**
 * In order to determine when a SIM is added or removed, this class listens for changes to the
 * SIM state. On SIM state "absent", this means a SIM was removed. On SIM state "loaded", this means
 * a SIM is ready.
 *
 * When a SIM is added, send an activate SMS. When a SIM is removed, remove the sync accounts and
 * change the status in the voicemail_status table.
 */
public class SimChangeReceiver extends BroadcastReceiver {
    private final String TAG = "SimChangeReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        Log.i(TAG, state);
        switch (state) {
            case IccCardConstants.INTENT_VALUE_ICC_ABSENT:
                OmtpVvmSyncAccountManager.getInstance(context).removeInactiveAccounts();
                break;
            case IccCardConstants.INTENT_VALUE_ICC_LOADED:
                //TODO: get carrier configuration values and send activation SMS
                break;
        }
    }
}