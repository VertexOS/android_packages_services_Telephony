/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_CARRIER_VVM_PACKAGE_NAME_STRING;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_DESTINATION_NUMBER_STRING;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_PORT_NUMBER_INT;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_PREFETCH_BOOL;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_SSL_PORT_NUMBER_INT;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_TYPE_STRING;

import android.os.PersistableBundle;
import android.test.AndroidTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class OmtpVvmCarrierConfigHelperTest extends AndroidTestCase {

    private static final String CARRIER_TYPE = "omtp.carrier";
    private static final String CARRIER_PACKAGE_NAME = "omtp.carrier.package";
    private static final boolean CARRIER_CELLULAR_REQUIRED = false;
    private static final boolean CARRIER_PREFETCH = true;
    private static final String CARRIER_DESTINATION_NUMBER = "123";
    private static final int CARRIER_APPLICATION_PORT = 456;
    private static final int DEFAULT_SSL_PORT = 0;
    private static final Set<String> DEFAULT_DISABLED_CAPABILITIES = null;

    private static final String TELEPHONY_TYPE = "omtp.telephony";
    private static final String[] TELEPHONY_PACKAGE_NAMES = {"omtp.telephony.package"};
    private static final boolean TELEPHONY_CELLULAR_REQUIRED = true;
    private static final boolean TELEPHONY_PREFETCH = false;
    private static final String TELEPHONY_DESTINATION_NUMBER = "321";
    private static final int TELEPHONY_APPLICATION_PORT = 654;
    private static final int TELEPHONY_SSL_PORT = 997;
    private static final String[] TELEPHONY_DISABLED_CAPABILITIES = {"foo"};

    private OmtpVvmCarrierConfigHelper mHelper;

    public void testCarrierConfig() {
        mHelper = new OmtpVvmCarrierConfigHelper(getContext(), createCarrierConfig(), null);
        verifyCarrierConfig();
        verifyDefaultExtraConfig();
    }

    public void testTelephonyConfig() {
        mHelper = new OmtpVvmCarrierConfigHelper(getContext(), null, createTelephonyConfig());
        verifyTelephonyConfig();
        verifyTelephonyExtraConfig();
    }

    public void testMixedConfig() {
        mHelper = new OmtpVvmCarrierConfigHelper(getContext(), createCarrierConfig(),
                createTelephonyConfig());
        verifyCarrierConfig();
        verifyTelephonyExtraConfig();
    }

    private PersistableBundle createCarrierConfig() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_VVM_TYPE_STRING, CARRIER_TYPE);
        bundle.putString(KEY_CARRIER_VVM_PACKAGE_NAME_STRING,
                CARRIER_PACKAGE_NAME);
        bundle.putBoolean(KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL,
                CARRIER_CELLULAR_REQUIRED);
        bundle.putBoolean(KEY_VVM_PREFETCH_BOOL,
                CARRIER_PREFETCH);
        bundle.putString(KEY_VVM_DESTINATION_NUMBER_STRING,
                CARRIER_DESTINATION_NUMBER);
        bundle.putInt(KEY_VVM_PORT_NUMBER_INT, CARRIER_APPLICATION_PORT);
        return bundle;
    }

    private void verifyCarrierConfig() {
        assertEquals(CARRIER_TYPE, mHelper.getVvmType());
        assertEquals(new HashSet<>(Arrays.asList(CARRIER_PACKAGE_NAME)),
                mHelper.getCarrierVvmPackageNames());
        assertEquals(CARRIER_CELLULAR_REQUIRED, mHelper.isCellularDataRequired());
        assertEquals(CARRIER_PREFETCH, mHelper.isPrefetchEnabled());
        assertEquals(CARRIER_APPLICATION_PORT, mHelper.getApplicationPort());
        assertEquals(CARRIER_DESTINATION_NUMBER, mHelper.getDestinationNumber());
    }


    private void verifyDefaultExtraConfig() {
        assertEquals(DEFAULT_SSL_PORT, mHelper.getSslPort());
        assertEquals(DEFAULT_DISABLED_CAPABILITIES, mHelper.getDisabledCapabilities());
    }


    private PersistableBundle createTelephonyConfig() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_VVM_TYPE_STRING, TELEPHONY_TYPE);
        bundle.putStringArray(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY,
                TELEPHONY_PACKAGE_NAMES);
        bundle.putBoolean(KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL,
                TELEPHONY_CELLULAR_REQUIRED);
        bundle.putBoolean(KEY_VVM_PREFETCH_BOOL,
                TELEPHONY_PREFETCH);
        bundle.putString(KEY_VVM_DESTINATION_NUMBER_STRING,
                TELEPHONY_DESTINATION_NUMBER);
        bundle.putInt(KEY_VVM_PORT_NUMBER_INT, TELEPHONY_APPLICATION_PORT);
        bundle.putInt(KEY_VVM_SSL_PORT_NUMBER_INT, TELEPHONY_SSL_PORT);
        bundle.putStringArray(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY,
                TELEPHONY_DISABLED_CAPABILITIES);
        return bundle;
    }

    private void verifyTelephonyConfig() {
        assertEquals(TELEPHONY_TYPE, mHelper.getVvmType());
        assertEquals(new HashSet<>(Arrays.asList(TELEPHONY_PACKAGE_NAMES)),
                mHelper.getCarrierVvmPackageNames());
        assertEquals(TELEPHONY_CELLULAR_REQUIRED, mHelper.isCellularDataRequired());
        assertEquals(TELEPHONY_PREFETCH, mHelper.isPrefetchEnabled());
        assertEquals(TELEPHONY_APPLICATION_PORT, mHelper.getApplicationPort());
        assertEquals(TELEPHONY_DESTINATION_NUMBER, mHelper.getDestinationNumber());
    }

    private void verifyTelephonyExtraConfig() {
        assertEquals(TELEPHONY_SSL_PORT, mHelper.getSslPort());
        assertEquals(new HashSet<>(Arrays.asList(TELEPHONY_DISABLED_CAPABILITIES)),
                mHelper.getDisabledCapabilities());
    }
}
