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
import static org.mockito.Mockito.when;

import android.support.test.runner.AndroidJUnit4;

import com.android.phone.vvm.omtp.scheduling.Task.TaskId;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class TaskSchedulerServiceTest extends TaskSchedulerServiceTestBase {

    @Test
    public void testTaskIdComparison() {
        TaskId id1 = new TaskId(1, 1);
        TaskId id2 = new TaskId(1, 1);
        TaskId id3 = new TaskId(1, 2);
        assertTrue(id1.equals(id2));
        assertTrue(id1.equals(id1));
        assertTrue(!id1.equals(id3));
    }

    @Test
    public void testAddDuplicatedTask() throws TimeoutException {
        TestTask task1 = (TestTask) submitTask(
                TaskSchedulerService.createIntent(mTestContext, TestTask.class));
        TestTask task2 = (TestTask) submitTask(
                TaskSchedulerService.createIntent(mTestContext, TestTask.class));
        assertTrue(task1.onDuplicatedTaskAddedCounter.invokedOnce());
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyNotRan(task2);
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyNotRan(task2);
    }

    @Test
    public void testAddDuplicatedTaskAfterFirstCompleted() throws TimeoutException {
        TestTask task1 = (TestTask) submitTask(
                TaskSchedulerService.createIntent(mTestContext, TestTask.class));
        mService.runNextTask();
        verifyRanOnce(task1);
        TestTask task2 = (TestTask) submitTask(
                TaskSchedulerService.createIntent(mTestContext, TestTask.class));
        assertTrue(task1.onDuplicatedTaskAddedCounter.neverInvoked());
        mService.runNextTask();
        verifyRanOnce(task2);
    }

    @Test
    public void testAddMultipleTask() {
        TestTask task1 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(1, 0)));
        TestTask task2 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(2, 0)));
        TestTask task3 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(1, 1)));
        assertTrue(task1.onDuplicatedTaskAddedCounter.neverInvoked());
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyNotRan(task2);
        verifyNotRan(task3);
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyRanOnce(task2);
        verifyNotRan(task3);
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyRanOnce(task2);
        verifyRanOnce(task3);
    }

    @Test
    public void testNotReady() {
        TestTask task1 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(1, 0)));
        task1.readyInMilliseconds = 1000;
        mService.runNextTask();
        verifyNotRan(task1);
        TestTask task2 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(2, 0)));
        mService.runNextTask();
        verifyNotRan(task1);
        verifyRanOnce(task2);
        task1.readyInMilliseconds = 50;
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyRanOnce(task2);
    }

    @Test
    public void testInvalidTaskId() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(new TaskId(Task.TASK_INVALID, 0));
        thrown.expect(AssertionError.class);
        mService.addTask(task);
    }

    @Test
    public void testDuplicatesAllowedTaskId() {
        TestTask task1 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(Task.TASK_ALLOW_DUPLICATES, 0)));
        TestTask task2 = (TestTask) submitTask(
                putTaskId(TaskSchedulerService.createIntent(mTestContext, TestTask.class),
                        new TaskId(Task.TASK_ALLOW_DUPLICATES, 0)));
        assertTrue(task1.onDuplicatedTaskAddedCounter.neverInvoked());
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyNotRan(task2);
        mService.runNextTask();
        verifyRanOnce(task1);
        verifyRanOnce(task2);
    }
}
