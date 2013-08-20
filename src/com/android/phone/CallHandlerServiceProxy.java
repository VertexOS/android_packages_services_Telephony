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

import com.android.phone.AudioRouter.AudioModeListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.ICallHandlerService;
import com.android.services.telephony.common.ICallCommandService;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for passing through call state changes to the CallHandlerService.
 */
public class CallHandlerServiceProxy extends Handler implements CallModeler.Listener,
        AudioModeListener {

    private static final String TAG = CallHandlerServiceProxy.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);


    private AudioRouter mAudioRouter;
    private CallCommandService mCallCommandService;
    private CallModeler mCallModeler;
    private Context mContext;
    private ICallHandlerService mCallHandlerService;
    private ServiceConnection mConnection;

    public CallHandlerServiceProxy(Context context, CallModeler callModeler,
            CallCommandService callCommandService, AudioRouter audioRouter) {
        mContext = context;
        mCallCommandService = callCommandService;
        mCallModeler = callModeler;
        mAudioRouter = audioRouter;

        mAudioRouter.addAudioModeListener(this);
        mCallModeler.addListener(this);
    }

    @Override
    public void onDisconnect(Call call) {
        if (mCallHandlerService != null) {
            try {
                if (DBG) Log.d(TAG, "onDisconnect: " + call);
                mCallHandlerService.onDisconnect(call);
                maybeUnbind();
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onDisconnect ", e);
            }
        }
    }

    @Override
    public void onIncoming(Call call, ArrayList<String> textResponses) {
        if (maybeBindToService() && mCallHandlerService != null) {
            try {
                if (DBG) Log.d(TAG, "onIncoming: " + call);
                mCallHandlerService.onIncoming(call, textResponses);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onUpdate", e);
            }
        }
    }

    @Override
    public void onUpdate(List<Call> calls, boolean fullUpdate) {
        if (maybeBindToService() && mCallHandlerService != null) {
            try {
                if (DBG) Log.d(TAG, "onUpdate: " + calls.toString());
                mCallHandlerService.onUpdate(calls, fullUpdate);
                maybeUnbind();
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onUpdate", e);
            }
        }
    }

    @Override
    public void onAudioModeChange(int previousMode, int newMode) {
        // Just do a simple log for now.
        Log.i(TAG, "Updating with new audio mode: " + AudioMode.toString(newMode) +
                " from " + AudioMode.toString(previousMode));

        if (mCallHandlerService != null) {
            try {
                if (DBG) Log.d(TAG, "onSupportAudioModeChange");

                mCallHandlerService.onAudioModeChange(newMode);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onAudioModeChange", e);
            }
        }
    }

    @Override
    public void onSupportedAudioModeChange(int modeMask) {
        if (mCallHandlerService != null) {
            try {
                if (DBG) Log.d(TAG, "onSupportAudioModeChange: " + AudioMode.toString(modeMask));

                mCallHandlerService.onSupportedAudioModeChange(modeMask);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception handling onAudioModeChange", e);
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
                Log.i(TAG, "Disconnected from UI service.");
                mCallHandlerService = null;

                // clean up our current binding.
                mContext.unbindService(mConnection);
                mConnection = null;

                // potentially attempt to rebind if there are still active calls.
                maybeBindToService();
            }
        };

        final Intent serviceIntent = new Intent(ICallHandlerService.class.getName());
        final ComponentName component = new ComponentName(
                mContext.getResources().getString(R.string.incall_ui_default_package),
                mContext.getResources().getString(R.string.incall_ui_default_class));
        serviceIntent.setComponent(component);
        if (!mContext.bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Cound not bind to ICallHandlerService");
        }
    }

    /**
     * Checks To see if there are any calls left.  If not, unbind the callhandler service.
     */
    private void maybeUnbind() {
        if (!mCallModeler.hasLiveCall()) {
            if (mConnection != null) {
                mContext.unbindService(mConnection);
                mConnection = null;
            }
        }
    }

    /**
     * Checks to see if there are any active calls.  If so, binds the call handler service.
     * @return true if already bound. False otherwise.
     */
    private boolean maybeBindToService() {
        if (mCallModeler.hasLiveCall()) {
            // mConnection is set to non-null once an attempt is made to connect.
            // We do not check against mCallHandlerService here because we could potentially
            // create multiple bindings to the UI.
            if (mConnection != null) {
                return true;
            }
            setupServiceConnection();
        }
        return false;
    }

    /**
     * Called when the in-call UI service is connected.  Send command interface to in-call.
     */
    private void onCallHandlerServiceConnected(ICallHandlerService callHandlerService) {
        mCallHandlerService = callHandlerService;

        try {
            mCallHandlerService.setCallCommandService(mCallCommandService);

            // start with a full update
            onUpdate(mCallModeler.getFullList(), true);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception calling CallHandlerService::setCallCommandService", e);
        }
    }
}
