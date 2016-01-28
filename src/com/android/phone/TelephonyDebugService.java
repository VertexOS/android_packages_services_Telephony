/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.internal.telephony.DebugService;
import com.android.internal.telephony.ITelephonyDebug;
import com.android.internal.telephony.ITelephonyDebugSubscriber;
import com.android.internal.telephony.TelephonyEvent;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A debug service for telephony.
 */
public class TelephonyDebugService extends Service {
    private static String TAG = "TelephonyDebugService";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;
    private DebugService mDebugService = new DebugService();

    /** Constructor */
    public TelephonyDebugService() {
        if (DBG) Log.d(TAG, "TelephonyDebugService()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mDebugService.dump(fd, pw, args);
    }

    private final int MAX_NUMBER_OF_EVENTS = 100;
    private final int MIN_TIME_OFFSET = 900000; // 15 minutes
    private final List<TelephonyEvent> mEvents = new ArrayList<TelephonyEvent>();
    private long mLastSentEventTimeMillis = System.currentTimeMillis();

    /**
     * Implementation of the ITelephonyDebug interface.
     */
    private final ITelephonyDebug.Stub mBinder = new ITelephonyDebug.Stub() {

        private final List<ITelephonyDebugSubscriber> mSubscribers = new ArrayList<>();

        public void writeEvent(long timestamp, int phoneId, int tag,
                int param1, int param2, Bundle data) {
            final TelephonyEvent ev = new TelephonyEvent(timestamp, phoneId, tag,
                    param1, param2, data);
            TelephonyEvent[] events = null;

            if (VDBG) {
                Log.v(TAG, "writeEvent(" + ev.toString() + ")");
            }

            synchronized (mEvents) {
                mEvents.add(ev);

                final long currentTimeMillis = System.currentTimeMillis();
                final long timeOffset = currentTimeMillis - mLastSentEventTimeMillis;
                if (timeOffset > MIN_TIME_OFFSET
                        || timeOffset < 0 // system time has changed
                        || mEvents.size() >= MAX_NUMBER_OF_EVENTS) {
                    // batch events
                    mLastSentEventTimeMillis = currentTimeMillis;
                    events = new TelephonyEvent[mEvents.size()];
                    mEvents.toArray(events);
                    mEvents.clear();
                }
            }

            if (events != null) {
                synchronized (mSubscribers) {
                    for (ITelephonyDebugSubscriber s : mSubscribers) {
                        try {
                            s.onEvents(events);
                        } catch (RemoteException ex) {
                            Log.e(TAG, "RemoteException " + ex);
                        }
                    }
                }
            }
        }

        public void subscribe(ITelephonyDebugSubscriber subscriber) {
            if (VDBG) Log.v(TAG, "subscribe");
            synchronized (mSubscribers) {
                mSubscribers.add(subscriber);
            }

            synchronized (mEvents) {
                try {
                    // send cached events
                    TelephonyEvent[] events = new TelephonyEvent[mEvents.size()];
                    mEvents.toArray(events);
                    subscriber.onEvents(events);
                } catch (RemoteException ex) {
                    Log.e(TAG, "RemoteException " + ex);
                }
            }
        }

        public void unsubscribe(ITelephonyDebugSubscriber subscriber) {
            if (VDBG) Log.v(TAG, "unsubscribe");
            synchronized (mSubscribers) {
                mSubscribers.remove(subscriber);
            }
        }
    };
}

