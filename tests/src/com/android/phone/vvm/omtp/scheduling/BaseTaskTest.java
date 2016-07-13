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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.support.test.runner.AndroidJUnit4;

import com.android.phone.vvm.omtp.scheduling.Task.TaskId;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BaseTaskTest extends BaseTaskTestBase {


    @Test
    public void testBaseTask() {
        DummyBaseTask task = (DummyBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, DummyBaseTask.class, 123));
        assertTrue(task.getId().equals(new TaskId(1, 123)));
        assertTrue(!task.hasStarted());
        assertTrue(!task.hasRun);
        mService.runNextTask();
        assertTrue(task.hasStarted());
        assertTrue(task.hasRun);
        verify(task.policy).onBeforeExecute();
        verify(task.policy).onCompleted();
    }

    @Test
    public void testFail() {
        FailingBaseTask task = (FailingBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, FailingBaseTask.class, 0));
        mService.runNextTask();
        verify(task.policy).onFail();
    }

    @Test
    public void testDuplicated() {
        DummyBaseTask task1 = (DummyBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, DummyBaseTask.class, 123));
        verify(task1.policy, never()).onDuplicatedTaskAdded();

        DummyBaseTask task2 = (DummyBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, DummyBaseTask.class, 123));
        verify(task1.policy).onDuplicatedTaskAdded();

        mService.runNextTask();
        assertTrue(task1.hasRun);
        assertTrue(!task2.hasRun);
    }

    @Test
    public void testDuplicated_DifferentSubId() {
        DummyBaseTask task1 = (DummyBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, DummyBaseTask.class, 123));
        verify(task1.policy, never()).onDuplicatedTaskAdded();

        DummyBaseTask task2 = (DummyBaseTask) submitTask(
                BaseTask.createIntent(mTestContext, DummyBaseTask.class, 456));
        verify(task1.policy, never()).onDuplicatedTaskAdded();
        mService.runNextTask();
        assertTrue(task1.hasRun);
        assertTrue(!task2.hasRun);

        mService.runNextTask();
        assertTrue(task2.hasRun);
    }

    @Test
    public void testReadyTime() {
        BaseTask task = spy(new DummyBaseTask());
        assertTrue(task.getReadyInMilliSeconds() == 0);
        mTime = 500;
        assertTrue(task.getReadyInMilliSeconds() == -500);
        task.setExecutionTime(1000);
        assertTrue(task.getReadyInMilliSeconds() == 500);
    }

    public static class DummyBaseTask extends BaseTask {

        public Policy policy;
        public boolean hasRun = false;

        public DummyBaseTask() {
            super(1);
            policy = mock(Policy.class);
            addPolicy(policy);
        }

        @Override
        public void onExecuteInBackgroundThread() {
            hasRun = true;
        }
    }

    public static class FailingBaseTask extends BaseTask {

        public Policy policy;
        public FailingBaseTask() {
            super(1);
            policy = mock(Policy.class);
            addPolicy(policy);
        }

        @Override
        public void onExecuteInBackgroundThread() {
            fail();
        }
    }
}
