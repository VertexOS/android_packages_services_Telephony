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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.android.phone.Assert;
import com.android.phone.vvm.omtp.scheduling.Task.TaskId;
import com.android.phone.vvm.omtp.scheduling.TaskSchedulerService.MainThreadHandler;
import com.android.phone.vvm.omtp.scheduling.TaskSchedulerService.WorkerThreadHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class TaskSchedulerServiceTestBase {

    private static final String EXTRA_ID = "test_extra_id";
    private static final String EXTRA_SUB_ID = "test_extra_sub_id";

    public TaskSchedulerService mService;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    public Context mTargetContext;
    public Context mTestContext;

    private static boolean sIsMainThread = true;

    private final TestMessageSender mMessageSender = new TestMessageSender();

    public static Intent putTaskId(Intent intent, TaskId taskId) {
        intent.putExtra(EXTRA_ID, taskId.id);
        intent.putExtra(EXTRA_SUB_ID, taskId.subId);
        return intent;
    }

    public static TaskId getTaskId(Intent intent) {
        return new TaskId(intent.getIntExtra(EXTRA_ID, 0), intent.getIntExtra(EXTRA_SUB_ID, 0));
    }

    @Before
    public void setUp() throws TimeoutException {
        Assert.setIsMainThreadForTesting(true);
        mTargetContext = InstrumentationRegistry.getTargetContext();
        IBinder binder = null;
        // bindService() might returns null on 2nd invocation because the service is not unbinded
        // yet. See https://code.google.com/p/android/issues/detail?id=180396
        while (binder == null) {
            binder = mServiceRule
                    .bindService(new Intent(mTargetContext, TaskSchedulerService.class));
        }
        mService = ((TaskSchedulerService.LocalBinder) binder).getService();
        mTestContext = createTestContext(mTargetContext, mService);
        mService.setMessageSenderForTest(mMessageSender);
        mService.setTaskAutoRunDisabledForTest(true);
        mService.setContextForTest(mTestContext);
    }

    @After
    public void tearDown() {
        Assert.setIsMainThreadForTesting(null);
        mService.setTaskAutoRunDisabledForTest(false);
        mService.clearTasksForTest();
        mService.stopSelf();
    }

    public Task submitTask(Intent intent) {
        Task task = mService.createTask(intent, 0, 0);
        mService.addTask(task);
        return task;
    }

    public static void verifyRanOnce(TestTask task) {
        assertTrue(task.onBeforeExecuteCounter.invokedOnce());
        assertTrue(task.executeCounter.invokedOnce());
        assertTrue(task.onCompletedCounter.invokedOnce());
    }

    public static void verifyNotRan(TestTask task) {
        assertTrue(task.onBeforeExecuteCounter.neverInvoked());
        assertTrue(task.executeCounter.neverInvoked());
        assertTrue(task.onCompletedCounter.neverInvoked());
    }

    public static class TestTask implements Task {

        public int readyInMilliseconds;

        private TaskId mId;

        public final InvocationCounter onCreateCounter = new InvocationCounter();
        public final InvocationCounter onBeforeExecuteCounter = new InvocationCounter();
        public final InvocationCounter executeCounter = new InvocationCounter();
        public final InvocationCounter onCompletedCounter = new InvocationCounter();
        public final InvocationCounter onDuplicatedTaskAddedCounter = new InvocationCounter();

        @Override
        public void onCreate(Context context, Intent intent, int flags, int startId) {
            onCreateCounter.invoke();
            mId = getTaskId(intent);
        }

        @Override
        public TaskId getId() {
            return mId;
        }

        @Override
        public long getReadyInMilliSeconds() {
            Assert.isMainThread();
            return readyInMilliseconds;
        }

        @Override
        public void onBeforeExecute() {
            Assert.isMainThread();
            onBeforeExecuteCounter.invoke();
        }

        @Override
        public void onExecuteInBackgroundThread() {
            Assert.isNotMainThread();
            executeCounter.invoke();
        }

        @Override
        public void onCompleted() {
            Assert.isMainThread();
            onCompletedCounter.invoke();
        }

        @Override
        public void onDuplicatedTaskAdded(Task task) {
            Assert.isMainThread();
            onDuplicatedTaskAddedCounter.invoke();
        }
    }

    public static class InvocationCounter {

        private int mCounter;

        public void invoke() {
            mCounter++;
        }

        public boolean invokedOnce() {
            return mCounter == 1;
        }

        public boolean neverInvoked() {
            return mCounter == 0;
        }
    }

    private class TestMessageSender extends TaskSchedulerService.MessageSender {

        @Override
        public void send(Message message) {
            if (message.getTarget() instanceof MainThreadHandler) {
                Assert.setIsMainThreadForTesting(true);
            } else if (message.getTarget() instanceof WorkerThreadHandler) {
                Assert.setIsMainThreadForTesting(false);
            } else {
                throw new AssertionError("unexpected Handler " + message.getTarget());
            }
            message.getTarget().handleMessage(message);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }

    private static Context createTestContext(Context targetContext, TaskSchedulerService service) {
        TestContext context = mock(TestContext.class);
        when(context.getService()).thenReturn(service);
        when(context.startService(any())).thenCallRealMethod();
        when(context.getPackageName()).thenReturn(targetContext.getPackageName());
        return context;
    }

    public abstract class TestContext extends Context {

        @Override
        public ComponentName startService(Intent service) {
            getService().onStartCommand(service, 0, 0);
            return null;
        }

        public abstract TaskSchedulerService getService();
    }
}
