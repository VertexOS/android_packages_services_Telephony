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

package com.android.phone.vvm.omtp.sms;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.VoicemailContract;
import android.telecom.PhoneAccountHandle;

import com.android.phone.PhoneUtils;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

/**
 * Performs visual voicemail provisioning in background thread. Not exported.
 */
public class OmtpProvisioningService extends IntentService {

    public OmtpProvisioningService() {
        super("OmtpProvisioningService");
    }

    /**
     * Create an intent to start OmtpProvisioningService from a {@link
     * VoicemailContract.ACTION_VOICEMAIL_SMS_RECEIVED} intent.
     */
    public static Intent getProvisionIntent(Context context, Intent messageIntent) {
        Intent serviceIntent = new Intent(context, OmtpProvisioningService.class);

        serviceIntent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID,
                messageIntent.getExtras().getInt(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID));
        serviceIntent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS,
                messageIntent.getExtras().getBundle(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS));

        return serviceIntent;
    }

    @Override
    public void onHandleIntent(Intent intent) {
        int subId = intent.getExtras().getInt(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID);
        PhoneAccountHandle phone = PhoneAccountHandleConverter.fromSubId(subId);

        Bundle data = intent.getExtras().getBundle(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS);

        StatusMessage message = new StatusMessage(data);
        startProvisioning(phone, message, data);
    }

    private void startProvisioning(PhoneAccountHandle phone, StatusMessage message, Bundle data) {
        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(this,
                PhoneUtils.getSubIdForPhoneAccountHandle(phone));

    }
}
