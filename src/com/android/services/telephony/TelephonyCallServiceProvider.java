/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import android.telecomm.CallServiceDescriptor;
import android.telecomm.CallServiceLookupResponse;
import android.telecomm.CallServiceProvider;

import java.util.ArrayList;

/**
 * This class is used to get a list of all CallServices.
 */
public class TelephonyCallServiceProvider extends CallServiceProvider {
    /** {@inheritDoc} */
    @Override
    public void lookupCallServices(CallServiceLookupResponse response) {
        ArrayList<CallServiceDescriptor> descriptors = new ArrayList<CallServiceDescriptor>();
        descriptors.add(CallServiceDescriptor.newBuilder(this)
                   .setCallService(PstnConnectionService.class)
                   .setNetworkType(CallServiceDescriptor.FLAG_PSTN)
                   .build());
        descriptors.add(CallServiceDescriptor.newBuilder(this)
                .setCallService(SipConnectionService.class)
                .setNetworkType(CallServiceDescriptor.FLAG_WIFI |
                        CallServiceDescriptor.FLAG_MOBILE)
                .build());
        response.setCallServiceDescriptors(descriptors);
    }
}
