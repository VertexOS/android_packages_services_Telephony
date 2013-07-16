/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.phone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

import com.android.services.telephony.common.ICallMonitorService;

/**
 * This class is responsible for passing through call state changes to the CallMonitorService.
 */
public class CallMonitorServiceProxy extends Handler {

    private static final String TAG = CallMonitorServiceProxy.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private Context mContext;
    private CallStateMonitor mCallStateMonitor;
    private ServiceConnection mConnection;
    private ICallMonitorService mCallMonitorService;

    public CallMonitorServiceProxy(Context context, CallStateMonitor callStateMonitor) {
        mContext = context;
        mCallStateMonitor = callStateMonitor;

        mCallStateMonitor.addListener(this);
        setupServiceConnection();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case CallStateMonitor.PHONE_NEW_RINGING_CONNECTION:
                onNewRingingConnection((AsyncResult) msg.obj);
                break;

            default:
                //super.handleMessage(msg);
                break;
        }
    }

    /**
     * Sets up the connection with ICallMonitorService
     */
    private void setupServiceConnection() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                if (DBG) {
                    Log.d(TAG, "Service Connected");
                }
                mCallMonitorService = ICallMonitorService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                mCallMonitorService = null;
            }
        };

        Intent serviceIntent = new Intent(ICallMonitorService.class.getName());
        if (!mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Cound not bind to ICallMonitorService");
        }
    }

    /**
     * Send notification of a new incoming call.
     */
    private void onNewRingingConnection(AsyncResult result) {
        if (mCallMonitorService != null) {
            try {
                mCallMonitorService.onIncomingCall(42);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onIncomingCall:" + e);
            }
        }
    }
}
