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

import android.annotation.Nullable;
import android.net.Network;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.SmsManager;
import android.util.Log;

import com.android.phone.common.mail.MessagingException;
import com.android.phone.settings.VisualVoicemailSettingsUtil;
import com.android.phone.settings.VoicemailChangePinDialogPreference;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.imap.ImapHelper;
import com.android.phone.vvm.omtp.sms.OmtpMessageSender;
import com.android.phone.vvm.omtp.sms.StatusMessage;
import com.android.phone.vvm.omtp.sms.Vvm3MessageSender;
import com.android.phone.vvm.omtp.sync.VvmNetworkRequestCallback;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * A flavor of OMTP protocol with a different provisioning process
 *
 * Used by carriers such as Verizon Wireless
 */
public class Vvm3Protocol extends VisualVoicemailProtocol {

    private static String TAG = "Vvm3Protocol";

    private static String IMAP_CHANGE_TUI_PWD_FORMAT = "CHANGE_TUI_PWD PWD=%1$s OLD_PWD=%2$s";
    private static String IMAP_CHANGE_VM_LANG_FORMAT = "CHANGE_VM_LANG Lang=%1$s";
    private static String IMAP_CLOSE_NUT = "CLOSE_NUT";

    private static String ISO639_Spanish = "es";

    private static String VVM3_VM_LANGUAGE_ENGLISH_STANDARD = "1";
    private static String VVM3_VM_LANGUAGE_SPANISH_STANDARD = "2";

    private static final int PIN_LENGTH = 6;

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
    public void startProvisioning(PhoneAccountHandle phoneAccountHandle,
            OmtpVvmCarrierConfigHelper config, StatusMessage message, Bundle data) {
        Log.i(TAG, "start vvm3 provisioning");
        if ("U".equals(message.getProvisioningStatus())) {
            Log.i(TAG, "Provisioning status: Unknown. VVM subscription not implemented.");
            // TODO: implement (b/28697797).
        } else if ("N".equals(message.getProvisioningStatus())) {
            Log.i(TAG, "setting up new user");
            VisualVoicemailSettingsUtil.setVisualVoicemailCredentialsFromStatusMessage(
                    config.getContext(), phoneAccountHandle, message);
            startProvisionNewUser(phoneAccountHandle, config, message);
        }
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
        if (command == OmtpConstants.IMAP_CLOSE_NUT) {
            return IMAP_CLOSE_NUT;
        }
        if (command == OmtpConstants.IMAP_CHANGE_VM_LANG_FORMAT) {
            return IMAP_CHANGE_VM_LANG_FORMAT;
        }
        return super.getCommand(command);
    }

    private void startProvisionNewUser(PhoneAccountHandle phoneAccountHandle,
            OmtpVvmCarrierConfigHelper config, StatusMessage message) {
        new Vvm3ProvisioningNetworkRequestCallback(config, phoneAccountHandle, message)
                .requestNetwork();
    }

    private static class Vvm3ProvisioningNetworkRequestCallback extends VvmNetworkRequestCallback {

        private final OmtpVvmCarrierConfigHelper mConfig;
        private final StatusMessage mMessage;

        public Vvm3ProvisioningNetworkRequestCallback(OmtpVvmCarrierConfigHelper config,
                PhoneAccountHandle phoneAccountHandle, StatusMessage message) {
            super(config, phoneAccountHandle);
            mConfig = config;
            mMessage = message;
        }

        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.i(TAG, "new user: network available");
            ImapHelper helper = new ImapHelper(mContext, mPhoneAccount, network);

            try {

                // VVM3 has inconsistent error language code to OMTP. Just issue a raw command
                // here.
                // TODO(b/29082671): use LocaleList
                if (Locale.getDefault().getLanguage()
                        .equals(new Locale(ISO639_Spanish).getLanguage())) {
                    // Spanish
                    helper.changeVoicemailTuiLanguage(VVM3_VM_LANGUAGE_SPANISH_STANDARD);
                } else {
                    // English
                    helper.changeVoicemailTuiLanguage(VVM3_VM_LANGUAGE_ENGLISH_STANDARD);
                }
                Log.i(TAG, "new user: language set");

                if (setPin(helper)) {
                    // Only close new user tutorial if the PIN has been changed.
                    helper.closeNewUserTutorial();
                    Log.i(TAG, "new user: NUT closed");

                    mConfig.requestStatus();
                }
            } catch (MessagingException | IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        private boolean setPin(ImapHelper helper) throws IOException, MessagingException {
            String defaultPin = getDefaultPin();
            if (defaultPin == null) {
                return false;
            }

            if (VoicemailChangePinDialogPreference.getDefaultOldPin(mContext, mPhoneAccount)
                    != null) {
                // The pin was already set
                return true;
            }
            String newPin = generatePin();
            if (helper.changePin(defaultPin, newPin) == OmtpConstants.CHANGE_PIN_SUCCESS) {
                VoicemailChangePinDialogPreference
                        .setDefaultOldPIN(mContext, mPhoneAccount, newPin);

                // TODO(b/29082418): set CONFIGURATION_STATE to VVM3_CONFIGURATION_PIN_NOT_SET
                // to prompt the user to set the PIN
            }
            Log.i(TAG, "new user: PIN set");
            return true;
        }

        @Nullable
        private String getDefaultPin() {
            // The IMAP username is [phone number]@example.com
            String username = mMessage.getImapUserName();
            try {
                String number = username.substring(0, username.indexOf('@'));
                if (number.length() < 4) {
                    Log.e(TAG, "unable to extract number from IMAP username");
                    return null;
                }
                return "1" + number.substring(number.length() - 4);
            } catch (StringIndexOutOfBoundsException e) {
                Log.e(TAG, "unable to extract number from IMAP username");
                return null;
            }

        }
    }



    private static String generatePin() {
        SecureRandom random = new SecureRandom();
        // TODO(b/29102412): generate base on the length requirement from the server
        return String.format("%010d", Math.abs(random.nextLong())).substring(0, PIN_LENGTH);

    }
}
