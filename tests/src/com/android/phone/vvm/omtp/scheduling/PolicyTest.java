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

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PolicyTest extends BaseTaskTestBase {

    private static int sExecuteCounter;

    @Before
    public void setUpPolicyTest() {
        sExecuteCounter = 0;
    }

    @Test
    public void testPostponePolicy() {
        Task task = submitTask(BaseTask.createIntent(mTestContext, PostponeTask.class, 0));
        mService.runNextTask();
        assertTrue(task.getReadyInMilliSeconds() == 1000);
        submitTask(BaseTask.createIntent(mTestContext, PostponeTask.class, 0));
        assertTrue(task.getReadyInMilliSeconds() == 1000);
        mTime = 500;
        submitTask(BaseTask.createIntent(mTestContext, PostponeTask.class, 0));
        assertTrue(task.getReadyInMilliSeconds() == 1000);
        mTime = 2500;
        mService.runNextTask();
        assertTrue(sExecuteCounter == 1);
    }

    @Test
    public void testRetryPolicy() {
        Task task = submitTask(BaseTask.createIntent(mTestContext, FailingRetryTask.class, 0));
        mService.runNextTask();
        // Should queue retry at 1000
        assertTrue(sExecuteCounter == 1);
        mService.runNextTask();
        assertTrue(sExecuteCounter == 1);
        mTime = 1500;
        mService.runNextTask();
        // Should queue retry at 2500
        assertTrue(sExecuteCounter == 2);
        mService.runNextTask();
        assertTrue(sExecuteCounter == 2);
        mTime = 2000;
        mService.runNextTask();
        assertTrue(sExecuteCounter == 2);
        mTime = 3000;
        mService.runNextTask();
        // No more retries are queued.
        assertTrue(sExecuteCounter == 3);
        mService.runNextTask();
        assertTrue(sExecuteCounter == 3);
        mTime = 4500;
        mService.runNextTask();
        assertTrue(sExecuteCounter == 3);
    }

    @Test
    public void testMinimalIntervalPolicy() {
        MinimalIntervalPolicyTask task1 = (MinimalIntervalPolicyTask) submitTask(
                BaseTask.createIntent(mTestContext, MinimalIntervalPolicyTask.class, 0));
        mService.runNextTask();
        assertTrue(task1.hasRan);
        MinimalIntervalPolicyTask task2 = (MinimalIntervalPolicyTask) submitTask(
                BaseTask.createIntent(mTestContext, MinimalIntervalPolicyTask.class, 0));
        mService.runNextTask();
        assertTrue(!task2.hasRan);

        mTime = 1500;
        mService.runNextTask();

        MinimalIntervalPolicyTask task3 = (MinimalIntervalPolicyTask) submitTask(
                BaseTask.createIntent(mTestContext, MinimalIntervalPolicyTask.class, 0));
        mService.runNextTask();
        assertTrue(task3.hasRan);
    }

    public abstract static class PolicyTestTask extends BaseTask {

        public PolicyTestTask() {
            super(1);
        }

        @Override
        public void onExecuteInBackgroundThread() {
            sExecuteCounter++;
        }
    }

    public static class PostponeTask extends PolicyTestTask {

        PostponeTask() {
            addPolicy(new PostponePolicy(1000));
        }
    }

    public static class FailingRetryTask extends PolicyTestTask {

        public FailingRetryTask() {
            addPolicy(new RetryPolicy(2, 1000));
        }

        @Override
        public void onExecuteInBackgroundThread() {
            super.onExecuteInBackgroundThread();
            fail();
        }
    }

    public static class MinimalIntervalPolicyTask extends PolicyTestTask {

        boolean hasRan;

        MinimalIntervalPolicyTask() {
            addPolicy(new MinimalIntervalPolicy(1000));
        }

        @Override
        public void onExecuteInBackgroundThread() {
            super.onExecuteInBackgroundThread();
            hasRan = true;
        }
    }

}
