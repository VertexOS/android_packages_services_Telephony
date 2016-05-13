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
 * limitations under the License.
 */

package com.android.phone.common.mail.store.imap;

import junit.framework.TestCase;

public class DigestMd5UtilsTest extends TestCase {

    public void testGetResponse() {
        // Example data from RFC 2831.4
        DigestMd5Utils.Data data = new DigestMd5Utils.Data();
        data.username = "chris";
        data.password = "secret";
        data.realm = "elwood.innosoft.com";
        data.nonce = "OA6MG9tEQGm2hh";
        data.cnonce = "OA6MHXh6VqTrRk";
        data.nc = "00000001";
        data.qop = "auth";
        data.digestUri = "imap/elwood.innosoft.com";
        String response = DigestMd5Utils.getResponse(data, false);
        assertEquals("d388dad90d4bbd760a152321f2143af7", response);
    }

    public void testGetResponse_ResponseAuth() {
        // Example data from RFC 2831.4
        DigestMd5Utils.Data data = new DigestMd5Utils.Data();
        data.username = "chris";
        data.password = "secret";
        data.realm = "elwood.innosoft.com";
        data.nonce = "OA6MG9tEQGm2hh";
        data.cnonce = "OA6MHXh6VqTrRk";
        data.nc = "00000001";
        data.qop = "auth";
        data.digestUri = "imap/elwood.innosoft.com";
        String response = DigestMd5Utils.getResponse(data, true);
        assertEquals("ea40f60335c427b5527b84dbabcdfffd", response);
    }

}
