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

import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_DESTINATION_NUMBER_STRING;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_PORT_NUMBER_INT;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_PREFETCH_BOOL;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_SSL_PORT_NUMBER_INT;
import static com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper.KEY_VVM_TYPE_STRING;

import android.os.PersistableBundle;

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.Arrays;

public class TelephonyVvmConfigManagerTest extends TestCase {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<list name=\"carrier_config_list\">\n";
    private static final String XML_FOOTER = "</list>";

    private static final String CARRIER = "  <pbundle_as_map>\n"
            + "    <string-array name=\"mccmnc\">\n"
            + "      <item value=\"12345\"/>\n"
            + "      <item value=\"67890\"/>\n"
            + "    </string-array>\n"
            + "    <int name=\"vvm_port_number_int\" value=\"54321\"/>\n"
            + "    <string name=\"vvm_destination_number_string\">11111</string>\n"
            + "    <string-array name=\"carrier_vvm_package_name_string_array\">\n"
            + "      <item value=\"com.android.phone\"/>\n"
            + "    </string-array>\n"
            + "    <string name=\"vvm_type_string\">vvm_type_omtp</string>\n"
            + "    <boolean name=\"vvm_cellular_data_required\" value=\"true\"/>\n"
            + "    <boolean name=\"vvm_prefetch\" value=\"true\"/>\n"
            + "    <int name=\"vvm_ssl_port_number_int\" value=\"997\"/>\n"
            + "    <string-array name=\"vvm_disabled_capabilities_string_array\">\n"
            + "      <item value =\"foo\"/>\n"
            + "      <item value =\"bar\"/>\n"
            + "    </string-array>\n"
            + "  </pbundle_as_map>\n";

    private static final String CARRIER_EMPTY = "<pbundle_as_map></pbundle_as_map>\n";


    public void testLoadConfigFromXml() {
        TelephonyVvmConfigManager manager = createManager(XML_HEADER + CARRIER + XML_FOOTER);
        verifyCarrier(manager.getConfig("12345"));
        verifyCarrier(manager.getConfig("67890"));
    }

    public void testLoadConfigFromXml_Multiple() {
        TelephonyVvmConfigManager manager =
                createManager(XML_HEADER + CARRIER + CARRIER + XML_FOOTER);
        verifyCarrier(manager.getConfig("12345"));
        verifyCarrier(manager.getConfig("67890"));
    }

    public void testLoadConfigFromXml_Empty() {
        createManager(XML_HEADER + CARRIER_EMPTY + XML_FOOTER);
    }


    private void verifyCarrier(PersistableBundle config) {
        assertTrue(Arrays.equals(new String[]{"12345", "67890"},
                config.getStringArray(TelephonyVvmConfigManager.KEY_MCCMNC)));
        assertEquals(54321, config.getInt(KEY_VVM_PORT_NUMBER_INT));
        assertEquals("11111", config.getString(KEY_VVM_DESTINATION_NUMBER_STRING));
        assertTrue(Arrays.equals(new String[]{"com.android.phone"},
                config.getStringArray(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY)));
        assertEquals("vvm_type_omtp", config.getString(KEY_VVM_TYPE_STRING));
        assertEquals(true, config.getBoolean(KEY_VVM_PREFETCH_BOOL));
        assertEquals(true, config.getBoolean(KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL));
        assertEquals(997, config.getInt(KEY_VVM_SSL_PORT_NUMBER_INT));
        assertTrue(Arrays.equals(new String[]{"foo", "bar"},
                config.getStringArray(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY)));
    }

    private TelephonyVvmConfigManager createManager(String xml) {
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));
            return new TelephonyVvmConfigManager(parser);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

}
