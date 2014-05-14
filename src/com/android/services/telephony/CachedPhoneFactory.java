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

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * Creates and caches phone objects for use with call services. Incoming call listening and the call
 * service itself exist independently (and across threads) but need to share their references to the
 * {@link Phone} objects that they use. This class is used to provide those cached references.
 * TODO(santoscordon): Investigate if this functionality can be folded into PhoneFactory once
 * PhoneFactory is no longer being used by the old system.
 */
class CachedPhoneFactory {
    private static Phone sCdmaPhone;
    private static Phone sGsmPhone;

    /**
     * @return The GSM Phone instance.
     */
    public static synchronized Phone getGsmPhone() {
        if (sGsmPhone == null) {
            sGsmPhone = PhoneFactory.getGsmPhone();
        }
        return sGsmPhone;
    }

    /**
     * @return The CDMA Phone instance.
     */
    public static synchronized Phone getCdmaPhone() {
        if (sCdmaPhone == null) {
            sCdmaPhone = PhoneFactory.getCdmaPhone();
        }
        return sCdmaPhone;
    }
}
