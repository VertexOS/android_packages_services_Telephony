/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.phone.vvm.omtp.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.telecom.PhoneAccountHandle;

import com.android.phone.PhoneUtils;
/**
 * Base class for network request call backs for visual voicemail syncing with the Imap server.
 * This handles retries and network requests.
 */
public abstract class OmtpVvmNetworkRequestCallback extends ConnectivityManager.NetworkCallback {
    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;

    protected Context mContext;
    protected PhoneAccountHandle mPhoneAccount;
    protected String mAction;
    protected NetworkRequest mNetworkRequest;
    private ConnectivityManager mConnectivityManager;

    public OmtpVvmNetworkRequestCallback(Context context, PhoneAccountHandle phoneAccount,
            String action) {
        mContext = context;
        mPhoneAccount = phoneAccount;
        mAction = action;
        mNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(
                        Integer.toString(PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccount)))
                .build();
    }

    public NetworkRequest getNetworkRequest() {
        return mNetworkRequest;
    }

    @Override
    public void onLost(Network network) {
        releaseNetwork();
    }

    @Override
    public void onUnavailable() {
        releaseNetwork();
    }

    public void requestNetwork() {
        getConnectivityManager().requestNetwork(getNetworkRequest(), this,
                NETWORK_REQUEST_TIMEOUT_MILLIS);
    }

    public void releaseNetwork() {
        getConnectivityManager().unregisterNetworkCallback(this);
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }
}
