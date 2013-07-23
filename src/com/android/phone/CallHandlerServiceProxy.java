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

import com.android.services.telephony.common.ICallHandlerService;
import com.android.services.telephony.common.ICallCommandService;

/**
 * This class is responsible for passing through call state changes to the CallHandlerService.
 */
public class CallHandlerServiceProxy extends Handler {

    private static final String TAG = CallHandlerServiceProxy.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);


    private Context mContext;
    private CallStateMonitor mCallStateMonitor;
    private ServiceConnection mConnection;
    private ICallHandlerService mCallHandlerService;
    private CallCommandService mCallCommandService;

    public CallHandlerServiceProxy(Context context, CallStateMonitor callStateMonitor,
            CallCommandService callCommandService) {
        mContext = context;
        mCallStateMonitor = callStateMonitor;
        mCallCommandService = callCommandService;

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
     * Sets up the connection with ICallHandlerService
     */
    private void setupServiceConnection() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                if (DBG) {
                    Log.d(TAG, "Service Connected");
                }
                onCallHandlerServiceConnected(ICallHandlerService.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // TODO(klp): handle the case where the in call ui crashed or gets destroyed.
                // In the near term, we need to re-bind to the service when ever it's gone.
                // Longer term, we need a way to catch the crash and allow the users to choose
                // a different in-call screen.
                Log.e(TAG, "Yikes! no in call ui!");
                mCallHandlerService = null;
            }
        };

        Intent serviceIntent = new Intent(ICallHandlerService.class.getName());
        if (!mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Cound not bind to ICallHandlerService");
        }
    }

    /**
     * Called when the in-call UI service is connected.  Send command interface to in-call.
     */
    private void onCallHandlerServiceConnected(ICallHandlerService CallHandlerService) {
        mCallHandlerService = CallHandlerService;

        try {
            mCallHandlerService.setCallCommandService(mCallCommandService);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception calling CallHandlerService::onConnected. " + e);
        }
    }

    /**
     * Send notification of a new incoming call.
     */
    private void onNewRingingConnection(AsyncResult result) {
        if (mCallHandlerService != null) {
            try {
                mCallHandlerService.onIncomingCall(0);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onIncomingCall:" + e);
            }
        } else {
            Log.wtf(TAG, "Call handle service has not connected!  Cannot accept incoming call.");
        }
    }
}
