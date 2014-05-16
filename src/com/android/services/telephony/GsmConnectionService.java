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
import android.net.Uri;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.Constants;
import com.android.services.telecomm.ConnectionRequest;

/**
 * Connnection service that uses GSM.
 */
public class GsmConnectionService extends PstnConnectionService {
    /** {@inheritDoc} */
    @Override
    protected Phone getPhone() {
        return PhoneFactory.getDefaultPhone();
    }

    /** {@inheritDoc} */
    @Override
    protected boolean canCall(Uri handle) {
        return canCall(this, handle);
    }

    // TODO: Refactor this out when CallServiceSelector is deprecated
    /* package */ static boolean canCall(Context context, Uri handle) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM
                && Constants.SCHEME_TEL.equals(handle.getScheme());
    }

    /** {@inheritDoc} */
    @Override
    protected TelephonyConnection onCreateTelephonyConnection(
            ConnectionRequest request, Connection connection) {
        return new GsmConnection(getPhone(), connection);
    }
}
