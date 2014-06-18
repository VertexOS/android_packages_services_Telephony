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

import android.content.Context;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;

/**
 * Singleton entry point for the telephony-services app. Initializes ongoing systems relating to
 * PSTN and SIP calls. This is started when the device starts and will be restarted automatically
 * if it goes away for any reason (e.g., crashes).
 * This is separate from the actual Application class because we only support one instance of this
 * app - running as the default user. {@link com.android.phone.PhoneApp} determines whether or not
 * we are running as the default user and if we are, then initializes and runs this class's
 * {@link #onCreate}.
 */
public class TelephonyGlobals {
    /** The application context. */
    private final Context mContext;

    /** Handles incoming calls for PSTN calls. */
    private PstnIncomingCallNotifier mPtsnIncomingCallNotifier;

    /**
     * Persists the specified parameters.
     *
     * @param context The application context.
     */
    public TelephonyGlobals(Context context) {
        mContext = context.getApplicationContext();
    }

    public void onCreate() {
        setupIncomingCallNotifiers();
    }

    /**
     * Sets up incoming call notifiers for all the call services.
     */
    private void setupIncomingCallNotifiers() {
        PhoneProxy defaultPhone = (PhoneProxy) PhoneFactory.getDefaultPhone();
        Log.i(this, "Default phone: %s.", defaultPhone);

        Log.i(this, "Registering the PSTN listener.");
        mPtsnIncomingCallNotifier = new PstnIncomingCallNotifier(defaultPhone);

        // TODO(santoscordon): Do SIP.  SIP will require a slightly different solution since it
        // doesn't have a phone object in the same way as PSTN calls. Additionally, the user can
        // set up SIP to do outgoing calls, but not listen for incoming calls (uses extra battery).
        // We need to make sure we take all of that into account.
    }
}
