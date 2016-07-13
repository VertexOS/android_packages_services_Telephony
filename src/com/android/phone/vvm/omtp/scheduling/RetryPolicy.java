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

package com.android.phone.vvm.omtp.scheduling;

import android.content.Intent;

import com.android.phone.vvm.omtp.VvmLog;

/**
 * A task with this policy will automatically re-queue itself if {@link BaseTask#fail()} has been
 * called during {@link BaseTask#onExecuteInBackgroundThread()}. A task will be retried at most
 * <code>retryLimit</code> times and with a <code>retryDelayMillis</code> interval in between.
 */
public class RetryPolicy implements Policy {

    private static final String TAG = "RetryPolicy";
    private static final String EXTRA_RETRY_COUNT = "extra_retry_count";

    private final int mRetryLimit;
    private final int mRetryDelayMillis;

    private BaseTask mTask;

    private int mRetryCount;
    private boolean mFailed;

    public RetryPolicy(int retryLimit, int retryDelayMillis) {
        mRetryLimit = retryLimit;
        mRetryDelayMillis = retryDelayMillis;
    }

    @Override
    public void onCreate(BaseTask task, Intent intent, int flags, int startId) {
        mTask = task;
        mRetryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0);
        if (mRetryCount > 0) {
            VvmLog.d(TAG, "retry #" + mRetryCount + " for " + mTask + " queued, executing in "
                    + mRetryDelayMillis);
            mTask.setExecutionTime(mTask.getTimeMillis() + mRetryDelayMillis);
        }
    }

    @Override
    public void onBeforeExecute() {

    }

    @Override
    public void onCompleted() {
        if (!mFailed) {
            return;
        }
        if (mRetryCount >= mRetryLimit) {
            VvmLog.d(TAG, "Retry limit for " + mTask + " reached");
            return;
        }

        Intent intent = mTask.createRestartIntent();
        intent.putExtra(EXTRA_RETRY_COUNT, mRetryCount + 1);

        mTask.getContext().startService(intent);
    }

    @Override
    public void onFail() {
        mFailed = true;
    }

    @Override
    public void onDuplicatedTaskAdded() {

    }
}
