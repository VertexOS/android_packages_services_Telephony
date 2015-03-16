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
package com.android.phone.vvm.omtp.sms;

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.telephony.SmsManager;
import android.text.TextUtils;

import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.services.telephony.Log;

import java.io.UnsupportedEncodingException;

/**
 * Implementation of {@link OmtpMessageSender} interface.
 * <p>
 * Provides simple APIs to send different types of mobile originated OMTP SMS to the VVM server.
 */
public class OmtpMessageSenderImpl implements OmtpMessageSender {
    private static final String TAG = "OmtpMessageSender";
    private final SmsManager mSmsManager;
    private final short mApplicationPort;
    private final String mDestinationNumber;
    private final String mClientType;
    private final String mProtocolVersion;
    private final String mClientPrefix;

    /**
     * Creates a new instance of OmtpMessageSenderImpl.
     *
     * @param smsManager SMS sending library. There is a different SmsManager for each SIM.
     * @param applicationPort If set to a value > 0 then a binary sms is sent to this port number.
     *            Otherwise, a standard text SMS is sent.
     * @param defaultDestinationNumber Destination number to be used.
     * @param clientType The "ct" field to be set in the MO message. This is the value used by the
     *            VVM server to identify the client. Certain VVM servers require a specific agreed
     *            value for this field.
     * @param protocolVersion OMTP protocol version.
     * @param clientPrefix The client prefix requested to be used by the server in its MT messages.
     */
    public OmtpMessageSenderImpl(SmsManager smsManager, short applicationPort,
            String defaultDestinationNumber, String clientType, String protocolVersion,
            String clientPrefix) {
        mSmsManager = smsManager;
        mApplicationPort = applicationPort;
        mDestinationNumber = defaultDestinationNumber;
        mClientType = clientType;
        mProtocolVersion = protocolVersion;
        mClientPrefix = clientPrefix;
    }


    // Activate message:
    // V1.1: Activate:pv=<value>;ct=<value>
    // V1.2: Activate:pv=<value>;ct=<value>;pt=<value>;<Clientprefix>
    // V1.3: Activate:pv=<value>;ct=<value>;pt=<value>;<Clientprefix>
    @Override
    public void requestVvmActivation(@Nullable PendingIntent sentIntent) {
        StringBuilder sb = new StringBuilder().append(OmtpConstants.ACTIVATE_REQUEST);

        appendProtocolVersionAndClientType(sb);
        if (TextUtils.equals(mProtocolVersion, OmtpConstants.PROTOCOL_VERSION1_2) ||
                TextUtils.equals(mProtocolVersion, OmtpConstants.PROTOCOL_VERSION1_3)) {
            appendApplicationPort(sb);
            appendClientPrefix(sb);
        }

        sendSms(sb.toString(), sentIntent);
    }

    // Deactivate message:
    // V1.1: Deactivate:pv=<value>;ct=<string>
    // V1.2: Deactivate:pv=<value>;ct=<string>
    // V1.3: Deactivate:pv=<value>;ct=<string>
    @Override
    public void requestVvmDeactivation(@Nullable PendingIntent sentIntent) {
        StringBuilder sb = new StringBuilder().append(OmtpConstants.DEACTIVATE_REQUEST);
        appendProtocolVersionAndClientType(sb);

        sendSms(sb.toString(), sentIntent);
    }

    // Status message:
    // V1.1: STATUS
    // V1.2: STATUS
    // V1.3: STATUS:pv=<value>;ct=<value>;pt=<value>;<Clientprefix>
    @Override
    public void requestVvmStatus(@Nullable PendingIntent sentIntent) {
        StringBuilder sb = new StringBuilder().append(OmtpConstants.STATUS_REQUEST);

        if (TextUtils.equals(mProtocolVersion, OmtpConstants.PROTOCOL_VERSION1_3)) {
            appendProtocolVersionAndClientType(sb);
            appendApplicationPort(sb);
            appendClientPrefix(sb);
        }

        sendSms(sb.toString(), sentIntent);
    }

    private void sendSms(String text, PendingIntent sentIntent) {
        // If application port is set to 0 then send simple text message, else send data message.
        if (mApplicationPort == 0) {
            Log.v(TAG, String.format("Sending TEXT sms '%s' to %s", text, mDestinationNumber));
            mSmsManager.sendTextMessage(mDestinationNumber, null, text, sentIntent, null);
        } else {
            byte[] data;
            try {
                data = text.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("Failed to encode: " + text);
            }
            Log.v(TAG, String.format("Sending BINARY sms '%s' to %s:%d", text, mDestinationNumber,
                    mApplicationPort));
            mSmsManager.sendDataMessage(mDestinationNumber, null, mApplicationPort, data,
                    sentIntent, null);
        }
    }

    private void appendProtocolVersionAndClientType(StringBuilder sb) {
        sb.append(OmtpConstants.SMS_PREFIX_SEPARATOR);
        appendField(sb, OmtpConstants.PROTOCOL_VERSION, mProtocolVersion);
        sb.append(OmtpConstants.SMS_FIELD_SEPARATOR);
        appendField(sb, OmtpConstants.CLIENT_TYPE, mClientType);
    }

    private void appendApplicationPort(StringBuilder sb) {
        sb.append(OmtpConstants.SMS_FIELD_SEPARATOR);
        appendField(sb, OmtpConstants.APPLICATION_PORT, mApplicationPort);
    }

    private void appendClientPrefix(StringBuilder sb) {
        sb.append(OmtpConstants.SMS_FIELD_SEPARATOR);
        sb.append(mClientPrefix);
    }

    private void appendField(StringBuilder sb, String field, Object value) {
        sb.append(field).append(OmtpConstants.SMS_KEY_VALUE_SEPARATOR).append(value);
    }
}
