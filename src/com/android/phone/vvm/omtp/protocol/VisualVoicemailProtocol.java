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

import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsManager;

import com.android.phone.vvm.omtp.DefaultOmtpEventHandler;
import com.android.phone.vvm.omtp.OmtpEvents;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;

public abstract class VisualVoicemailProtocol {

    public void startActivation(OmtpVvmCarrierConfigHelper config) {
        OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
        if (messageSender != null) {
            messageSender.requestVvmActivation(null);
        }
    }

    public void startDeactivation(OmtpVvmCarrierConfigHelper config) {
        OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
        if (messageSender != null) {
            messageSender.requestVvmDeactivation(null);
        }
    }

    public void startProvisioning(OmtpVvmCarrierConfigHelper config, Bundle data) {
        // Do nothing
    }

    public void requestStatus(OmtpVvmCarrierConfigHelper config) {
        OmtpMessageSender messageSender = ProtocolHelper.getMessageSender(this, config);
        if (messageSender != null) {
            messageSender.requestVvmStatus(null);
        }
    }

    public abstract OmtpMessageSender createMessageSender(SmsManager smsManager,
            short applicationPort, String destinationNumber);

    /**
     * Translate an OMTP IMAP command to the protocol specific one. For example, changing the TUI
     * password on OMTP is XCHANGE_TUI_PWD, but on CVVM and VVM3 it is CHANGE_TUI_PWD.
     *
     * @param command A String command in {@link com.android.phone.vvm.omtp.OmtpConstants}, the exact
     * instance should be used instead of its' value.
     * @returns Translated command, or {@code null} if not available in this protocol
     */
    public String getCommand(String command) {
        return command;
    }

    public void handleEvent(Context context, int subId, OmtpEvents event) {
        DefaultOmtpEventHandler.handleEvent(context, subId, event);
    }
}
