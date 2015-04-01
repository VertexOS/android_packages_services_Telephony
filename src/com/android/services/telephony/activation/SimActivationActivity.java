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
 * limitations under the License.
 */

package com.android.services.telephony.activation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.services.telephony.Log;

/**
 * Invisible activity that handles the android.intent.action.SIM_ACTIVATION_REQUEST intent.
 * This activity is protected by the android.permission.PERFORM_SIM_ACTIVATION permission.
 */
public class SimActivationActivity extends Activity {
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (Intent.ACTION_SIM_ACTIVATION_REQUEST.equals(intent.getAction())) {
            Log.i(this, "Activation requested " + intent);
        }
        finish();
    }
}
