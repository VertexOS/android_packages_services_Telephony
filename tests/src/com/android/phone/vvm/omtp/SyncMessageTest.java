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

import com.android.phone.vvm.omtp.sms.SyncMessage;

import junit.framework.TestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class SyncMessageTest extends TestCase {

    public void testSyncMessage() {
        Bundle bundle = new Bundle();
        bundle.putString(OmtpConstants.SYNC_TRIGGER_EVENT, "event");
        bundle.putString(OmtpConstants.MESSAGE_UID, "uid");
        bundle.putString(OmtpConstants.MESSAGE_LENGTH, "1");
        bundle.putString(OmtpConstants.CONTENT_TYPE, "type");
        bundle.putString(OmtpConstants.SENDER, "sender");
        bundle.putString(OmtpConstants.NUM_MESSAGE_COUNT, "2");
        bundle.putString(OmtpConstants.TIME, "29/08/1997 02:14 -0400");

        SyncMessage message = new SyncMessage(bundle);
        assertEquals("event", message.getSyncTriggerEvent());
        assertEquals("uid", message.getId());
        assertEquals(1, message.getLength());
        assertEquals("type", message.getContentType());
        assertEquals("sender", message.getSender());
        assertEquals(2, message.getNewMessageCount());
        try {
            assertEquals(new SimpleDateFormat(
                    OmtpConstants.DATE_TIME_FORMAT, Locale.US)
                    .parse("29/08/1997 02:14 -0400").getTime(), message.getTimestampMillis());
        } catch (ParseException e) {
            throw new AssertionError(e.toString());
        }
    }

    public void testSyncMessage_EmptyBundle() {
        SyncMessage message = new SyncMessage(new Bundle());
        assertEquals("", message.getSyncTriggerEvent());
        assertEquals("", message.getId());
        assertEquals(0, message.getLength());
        assertEquals("", message.getContentType());
        assertEquals("", message.getSender());
        assertEquals(0, message.getNewMessageCount());
        assertEquals(0, message.getTimestampMillis());
    }
}
