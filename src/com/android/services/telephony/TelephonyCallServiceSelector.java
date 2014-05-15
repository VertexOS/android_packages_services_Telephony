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

import android.telecomm.CallInfo;
import android.telecomm.CallServiceDescriptor;
import android.telecomm.CallServiceSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which call service should be used to place outgoing calls or to switch the call to.
 */
public class TelephonyCallServiceSelector extends CallServiceSelector {

    /** {@inheritDoc} */
    @Override
    protected void select(CallInfo callInfo, List<CallServiceDescriptor> descriptors) {
        ArrayList<CallServiceDescriptor> selectedDescriptors =
                new ArrayList<CallServiceDescriptor>();

        CallServiceDescriptor descriptor = getDescriptor(descriptors, CdmaCallService.class);
        if (descriptor != null) {
            if (CdmaCallService.shouldSelect(this, callInfo)) {
                selectedDescriptors.add(descriptor);
            }
        }
        descriptor = getDescriptor(descriptors, GsmCallService.class);
        if (descriptor != null) {
            if (GsmCallService.shouldSelect(this, callInfo)) {
                selectedDescriptors.add(descriptor);
            }
        }
        descriptor = getDescriptor(descriptors, SipCallService.class);
        if (descriptor != null) {
            if (SipCallService.shouldSelect(this, callInfo)) {
                selectedDescriptors.add(descriptor);
            }
        }

        getAdapter().setSelectedCallServices(callInfo.getId(), selectedDescriptors);
    }

    private CallServiceDescriptor getDescriptor(
            List<CallServiceDescriptor> descriptors, Class<?> clazz) {

        for (CallServiceDescriptor descriptor : descriptors) {
            if (!getPackageName().equals(descriptor.getServiceComponent().getPackageName())) {
                continue;
            }

            if (clazz.getName().equals(descriptor.getServiceComponent().getClassName())) {
                return descriptor;
            }
        }

        return null;
    }
}
