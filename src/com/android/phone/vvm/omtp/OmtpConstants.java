/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
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

/**
 * Wrapper class to hold relevant OMTP constants as defined in the OMTP spec.
 * <p>
 * In essence this is a programmatic representation of the relevant portions of OMTP spec.
 */
public class OmtpConstants {
    public static final String SMS_FIELD_SEPARATOR = ";";
    public static final String SMS_KEY_VALUE_SEPARATOR = "=";
    public static final String SMS_PREFIX_SEPARATOR = ":";

    public static final String CLIENT_PREFIX = "//VVM";

    /** OMTP protocol versions. */
    public static String PROTOCOL_VERSION1_1 = "11";
    public static String PROTOCOL_VERSION1_2 = "12";
    public static String PROTOCOL_VERSION1_3 = "13";

    ///////////////////////// Client/Mobile originated SMS //////////////////////

    /** Mobile Originated requests */
    public static String ACTIVATE_REQUEST = "Activate";
    public static String DEACTIVATE_REQUEST = "Deactivate";
    public static String STATUS_REQUEST = "Status";

    /** fields that can be present in a Mobile Originated OMTP SMS */
    public static String CLIENT_TYPE = "ct";
    public static String APPLICATION_PORT = "pt";
    public static String PROTOCOL_VERSION = "pv";
}
