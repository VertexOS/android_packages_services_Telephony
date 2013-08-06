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

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallHandlerService;
import com.android.services.telephony.common.ICallCommandService;

import java.util.List;

/**
 * This class is responsible for passing through call state changes to the CallHandlerService.
 */
public class CallHandlerServiceProxy extends Handler implements CallModeler.Listener {

    private static final String TAG = CallHandlerServiceProxy.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);


    private Context mContext;
    private CallModeler mCallModeler;
    private ServiceConnection mConnection;
    private ICallHandlerService mCallHandlerService;
    private CallCommandService mCallCommandService;

    public CallHandlerServiceProxy(Context context, CallModeler callModeler,
            CallCommandService callCommandService) {
        mContext = context;
        mCallCommandService = callCommandService;
        mCallModeler = callModeler;

        setupServiceConnection();
        mCallModeler.addListener(this);

        // start the whole process
        onUpdate(mCallModeler.getFullList(), true);
    }

    @Override
    public void onDisconnect(Call call) {
        if (mCallHandlerService != null) {
            try {
                mCallHandlerService.onDisconnect(call);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onDisconnect ", e);
            }
        }
    }

    @Override
    public void onUpdate(List<Call> calls, boolean fullUpdate) {
        if (mCallHandlerService != null) {
            try {
                mCallHandlerService.onUpdate(calls, fullUpdate);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onUpdate", e);
            }
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
    private void onCallHandlerServiceConnected(ICallHandlerService callHandlerService) {
        mCallHandlerService = callHandlerService;

        try {
            mCallHandlerService.setCallCommandService(mCallCommandService);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception calling CallHandlerService::setCallCommandService", e);
        }
    }
}
