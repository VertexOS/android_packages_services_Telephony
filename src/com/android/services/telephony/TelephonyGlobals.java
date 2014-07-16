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

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;

/**
 * Singleton entry point for the telephony-services app. Initializes ongoing systems relating to
 * PSTN calls. This is started when the device starts and will be restarted automatically
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

    private TtyManager mTtyManager;

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

        Phone phone = PhoneFactory.getDefaultPhone();
        if (phone != null) {
            mTtyManager = new TtyManager(mContext, phone);
        }
    }

    /**
     * Sets up incoming call notifiers for all the connection services.
     */
    private void setupIncomingCallNotifiers() {
        PhoneProxy defaultPhone = (PhoneProxy) PhoneFactory.getDefaultPhone();
        Log.i(this, "Default phone: %s.", defaultPhone);

        Log.i(this, "Registering the PSTN listener.");
        mPtsnIncomingCallNotifier = new PstnIncomingCallNotifier(defaultPhone);
    }
}
