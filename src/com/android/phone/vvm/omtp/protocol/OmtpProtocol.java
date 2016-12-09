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

package com.android.phone.vvm.omtp.protocol;

import android.telephony.SmsManager;

import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;
import com.android.phone.vvm.omtp.sms.OmtpStandardMessageSender;

public class OmtpProtocol extends VisualVoicemailProtocol {

    @Override
    public OmtpMessageSender createMessageSender(SmsManager smsManager, short applicationPort,
            String destinationNumber) {
        return new OmtpStandardMessageSender(smsManager, applicationPort, destinationNumber,
                null, OmtpConstants.PROTOCOL_VERSION1_1, null);
    }
}
