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

import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.vvm.omtp.sms.StatusMessage;

import junit.framework.TestCase;

@VisibleForTesting
public class StatusMessageTest extends TestCase {

    public void testStatusMessage() {
        Bundle bundle = new Bundle();
        bundle.putString(OmtpConstants.PROVISIONING_STATUS, "status");
        bundle.putString(OmtpConstants.RETURN_CODE, "code");
        bundle.putString(OmtpConstants.SUBSCRIPTION_URL, "url");
        bundle.putString(OmtpConstants.SERVER_ADDRESS, "address");
        bundle.putString(OmtpConstants.TUI_ACCESS_NUMBER, "tui");
        bundle.putString(OmtpConstants.CLIENT_SMS_DESTINATION_NUMBER, "sms");
        bundle.putString(OmtpConstants.IMAP_PORT, "1234");
        bundle.putString(OmtpConstants.IMAP_USER_NAME, "username");
        bundle.putString(OmtpConstants.IMAP_PASSWORD, "password");
        bundle.putString(OmtpConstants.SMTP_PORT, "s1234");
        bundle.putString(OmtpConstants.SMTP_USER_NAME, "susername");
        bundle.putString(OmtpConstants.SMTP_PASSWORD, "spassword");
        bundle.putString(OmtpConstants.TUI_PASSWORD_LENGTH, "4-7");

        StatusMessage message = new StatusMessage(bundle);
        assertEquals("status", message.getProvisioningStatus());
        assertEquals("code", message.getReturnCode());
        assertEquals("url", message.getSubscriptionUrl());
        assertEquals("address", message.getServerAddress());
        assertEquals("tui", message.getTuiAccessNumber());
        assertEquals("sms", message.getClientSmsDestinationNumber());
        assertEquals("1234", message.getImapPort());
        assertEquals("username", message.getImapUserName());
        assertEquals("password", message.getImapPassword());
        assertEquals("s1234", message.getSmtpPort());
        assertEquals("susername", message.getSmtpUserName());
        assertEquals("spassword", message.getSmtpPassword());
        assertEquals("4-7", message.getTuiPasswordLength());
    }

    public void testSyncMessage_EmptyBundle() {
        StatusMessage message = new StatusMessage(new Bundle());
        assertEquals("", message.getProvisioningStatus());
        assertEquals("", message.getReturnCode());
        assertEquals("", message.getSubscriptionUrl());
        assertEquals("", message.getServerAddress());
        assertEquals("", message.getTuiAccessNumber());
        assertEquals("", message.getClientSmsDestinationNumber());
        assertEquals("", message.getImapPort());
        assertEquals("", message.getImapUserName());
        assertEquals("", message.getImapPassword());
        assertEquals("", message.getSmtpPort());
        assertEquals("", message.getSmtpUserName());
        assertEquals("", message.getSmtpPassword());
        assertEquals("", message.getTuiPasswordLength());
    }
}
