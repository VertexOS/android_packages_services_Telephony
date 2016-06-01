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

import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;
import com.android.phone.vvm.omtp.sms.Vvm3MessageSender;

/**
 * A flavor of OMTP protocol with a different provisioning process
 *
 * Used by carriers such as Verizon Wireless
 */
public class Vvm3Protocol extends VisualVoicemailProtocol {

    private static String TAG = "Vvm3Protocol";

    private static String IMAP_CHANGE_TUI_PWD_FORMAT = "CHANGE_TUI_PWD PWD=%1s OLD_PWD=%2s";

    public Vvm3Protocol() {
        Log.d(TAG, "Vvm3Protocol created");
    }

    @Override
    public void startActivation(OmtpVvmCarrierConfigHelper config) {
        // VVM3 does not support activation SMS.
        // Send a status request which will start the provisioning process if the user is not
        // provisioned.
        config.requestStatus();
    }

    @Override
    public void startDeactivation(OmtpVvmCarrierConfigHelper config) {
        // VVM3 does not support deactivation.
        // do nothing.
    }

    @Override
    public void startProvisioning(OmtpVvmCarrierConfigHelper config, Bundle data) {
        Log.d(TAG, "start vvm3 provisioning");
        // TODO: implement (b/28697797).
    }

    @Override
    public OmtpMessageSender createMessageSender(SmsManager smsManager, short applicationPort,
            String destinationNumber) {
        return new Vvm3MessageSender(smsManager, applicationPort, destinationNumber);
    }

    @Override
    public String getCommand(String command) {
        if (command == OmtpConstants.IMAP_CHANGE_TUI_PWD_FORMAT) {
            return IMAP_CHANGE_TUI_PWD_FORMAT;
        }
        return super.getCommand(command);
    }
}
