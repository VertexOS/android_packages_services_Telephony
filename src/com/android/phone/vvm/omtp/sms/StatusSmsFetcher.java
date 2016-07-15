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

package com.android.phone.vvm.omtp.sms;

import android.annotation.MainThread;
import android.annotation.WorkerThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.VoicemailContract;
import com.android.phone.Assert;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;
import com.android.phone.vvm.omtp.protocol.VisualVoicemailProtocol;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Intercepts a incoming STATUS SMS with a blocking call.
 */
public class StatusSmsFetcher extends BroadcastReceiver implements Closeable {

    private static final String TAG = "VvmStatusSmsFetcher";

    private static final long STATUS_SMS_TIMEOUT_MILLIS = 60_000;

    private CompletableFuture<Bundle> mFuture = new CompletableFuture<>();

    private final Context mContext;
    private final int mSubId;

    public StatusSmsFetcher(Context context, int subId) {
        mContext = context;
        mSubId = subId;
        IntentFilter filter = new IntentFilter(VoicemailContract.ACTION_VOICEMAIL_SMS_RECEIVED);
        context.registerReceiver(this, filter);
    }

    @Override
    public void close() throws IOException {
        mContext.unregisterReceiver(this);
    }

    @WorkerThread
    public Bundle get()
            throws InterruptedException, ExecutionException, TimeoutException {
        Assert.isNotMainThread();
        return mFuture.get(STATUS_SMS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    @MainThread
    public void onReceive(Context context, Intent intent) {
        Assert.isMainThread();
        int subId = intent.getExtras().getInt(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID);

        if (mSubId != subId) {
            return;
        }
        String eventType = intent.getExtras()
                .getString(VoicemailContract.EXTRA_VOICEMAIL_SMS_PREFIX);

        if (eventType.equals(OmtpConstants.STATUS_SMS_PREFIX)) {
            mFuture.complete(intent.getBundleExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS));
            return;
        }

        if (eventType.equals(OmtpConstants.SYNC_SMS_PREFIX)) {
            return;
        }

        VvmLog.i(TAG, "VVM SMS with event " + eventType
                + " received, attempting to translate to STATUS SMS");
        OmtpVvmCarrierConfigHelper helper = new OmtpVvmCarrierConfigHelper(context, subId);
        VisualVoicemailProtocol protocol = helper.getProtocol();
        if (protocol == null) {
            return;
        }
        Bundle translatedBundle = protocol.translateStatusSmsBundle(helper, eventType,
                intent.getBundleExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS));

        if (translatedBundle != null) {
            VvmLog.i(TAG, "Translated to STATUS SMS");
            mFuture.complete(translatedBundle);
        }
    }
}
