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

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.Assert;
import com.android.phone.NeededForTesting;
import com.android.phone.vvm.omtp.VvmLog;
import com.android.phone.vvm.omtp.scheduling.Task.TaskId;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A service to queue and run {@link Task} on a worker thread. Only one task will be ran at a time,
 * and same task cannot exist in the queue at the same time. The service will be started when a
 * intent is received, and stopped when there are no more tasks in the queue.
 */
public class TaskSchedulerService extends Service {

    private static final String TAG = "TaskSchedulerService";

    private static final int READY_TOLERANCE_MILLISECONDS = 100;
    /**
     * When there are no more tasks to be run the service should be stopped. But when all tasks has
     * finished there might still be more tasks in the message queue waiting to be processed,
     * especially the ones submitted in {@link Task#onCompleted()}. Wait for a while before stopping
     * the service to make sure there are no pending messages.
     */
    private static final int STOP_DELAY_MILLISECONDS = 5_000;
    private static final String EXTRA_CLASS_NAME = "extra_class_name";

    private static final String WAKE_LOCK_TAG = "TaskSchedulerService_wakelock";

    // The thread to run tasks on
    private volatile WorkerThreadHandler mWorkerThreadHandler;

    private Context mContext = this;
    /**
     * Used by tests to turn task handling into a single threaded process by calling {@link
     * Handler#handleMessage(Message)} directly
     */
    private MessageSender mMessageSender = new MessageSender();

    private MainThreadHandler mMainThreadHandler;

    private WakeLock mWakeLock;

    /**
     * Main thread only, access through {@link #getTasks()}
     */
    private final Queue<Task> mTasks = new ArrayDeque<>();
    private boolean mWorkerThreadIsBusy = false;

    private final Runnable mStopServiceWithDelay = new Runnable() {
        @Override
        public void run() {
            VvmLog.d(TAG, "Stopping service");
            stopSelf();
        }
    };
    /**
     * Should attempt to run the next task when a task has finished or been added.
     */
    private boolean mTaskAutoRunDisabledForTesting = false;

    @VisibleForTesting
    final class WorkerThreadHandler extends Handler {

        public WorkerThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        @WorkerThread
        public void handleMessage(Message msg) {
            Assert.isNotMainThread();
            Task task = (Task) msg.obj;
            try {
                VvmLog.v(TAG, "executing task " + task);
                task.onExecuteInBackgroundThread();
            } catch (Throwable throwable) {
                VvmLog.e(TAG, "Exception while executing task " + task + ":", throwable);
            }

            Message schedulerMessage = mMainThreadHandler.obtainMessage();
            schedulerMessage.obj = task;
            mMessageSender.send(schedulerMessage);
        }
    }

    @VisibleForTesting
    final class MainThreadHandler extends Handler {

        public MainThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        @MainThread
        public void handleMessage(Message msg) {
            Assert.isMainThread();
            Task task = (Task) msg.obj;
            getTasks().remove(task);
            task.onCompleted();
            mWorkerThreadIsBusy = false;
            maybeRunNextTask();
        }
    }

    @Override
    @MainThread
    public void onCreate() {
        super.onCreate();
        mWakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        mWakeLock.acquire();
        HandlerThread thread = new HandlerThread("VvmTaskSchedulerService");
        thread.start();

        mWorkerThreadHandler = new WorkerThreadHandler(thread.getLooper());
        mMainThreadHandler = new MainThreadHandler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        mWorkerThreadHandler.getLooper().quit();
        mWakeLock.release();
    }

    @Override
    @MainThread
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Assert.isMainThread();
        Task task = createTask(intent, flags, startId);
        if (task == null) {
            VvmLog.e(TAG, "cannot create task form intent");
        } else {
            addTask(task);
        }
        // STICKY means the service will be automatically restarted will the last intent if it is
        // killed.
        return START_NOT_STICKY;
    }

    @MainThread
    @VisibleForTesting
    void addTask(Task task) {
        Assert.isMainThread();
        if (task.getId().id == Task.TASK_INVALID) {
            throw new AssertionError("Task id was not set to a valid value before adding.");
        }
        if (task.getId().id != Task.TASK_ALLOW_DUPLICATES) {
            Task oldTask = getTask(task.getId());
            if (oldTask != null) {
                oldTask.onDuplicatedTaskAdded(task);
                return;
            }
        }
        mMainThreadHandler.removeCallbacks(mStopServiceWithDelay);
        getTasks().add(task);
        maybeRunNextTask();

    }

    @MainThread
    @Nullable
    private Task getTask(TaskId taskId) {
        Assert.isMainThread();
        for (Task task : getTasks()) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    @MainThread
    private Queue<Task> getTasks() {
        Assert.isMainThread();
        return mTasks;
    }

    /**
     * Create an intent that will queue the <code>task</code>
     */
    public static Intent createIntent(Context context, Class<? extends Task> task) {
        Intent intent = new Intent(context, TaskSchedulerService.class);
        intent.putExtra(EXTRA_CLASS_NAME, task.getName());
        return intent;
    }

    @VisibleForTesting
    @MainThread
    @Nullable
    Task createTask(@Nullable Intent intent, int flags, int startId) {
        Assert.isMainThread();
        if (intent == null) {
            return null;
        }
        String className = intent.getStringExtra(EXTRA_CLASS_NAME);
        VvmLog.d(TAG, "create task:" + className);
        if (className == null) {
            throw new IllegalArgumentException("EXTRA_CLASS_NAME expected");
        }
        try {
            Task task = (Task) Class.forName(className).newInstance();
            task.onCreate(mContext, intent, flags, startId);
            return task;
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @MainThread
    private void maybeRunNextTask() {
        Assert.isMainThread();
        if (mWorkerThreadIsBusy) {
            return;
        }
        if (mTaskAutoRunDisabledForTesting) {
            // If mTaskAutoRunDisabledForTesting is true, runNextTask() must be explicitly called
            // to run the next task.
            return;
        }

        runNextTask();
    }

    @VisibleForTesting
    @MainThread
    void runNextTask() {
        Assert.isMainThread();
        if (getTasks().isEmpty()) {
            prepareStop();
            return;
        }
        Long minimalWaitTime = null;
        for (Task task : getTasks()) {
            long waitTime = task.getReadyInMilliSeconds();
            if (waitTime < READY_TOLERANCE_MILLISECONDS) {
                task.onBeforeExecute();
                Message message = mWorkerThreadHandler.obtainMessage();
                message.obj = task;
                mWorkerThreadIsBusy = true;
                mMessageSender.send(message);
                return;
            } else {
                if (minimalWaitTime == null || waitTime < minimalWaitTime) {
                    minimalWaitTime = waitTime;
                }
            }
        }
        VvmLog.d(TAG, "minimal wait time:" + minimalWaitTime);
        if (!mTaskAutoRunDisabledForTesting && minimalWaitTime != null) {
            // No tests are currently ready. Sleep until the next one should be.
            // If a new task is added during the sleep the service will wake immediately.
            mMainThreadHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeRunNextTask();
                }
            }, minimalWaitTime);
        }
    }

    private void prepareStop() {
        VvmLog.d(TAG,
                "No more tasks, stopping service if no task are added in "
                        + STOP_DELAY_MILLISECONDS + " millis");
        mMainThreadHandler.postDelayed(mStopServiceWithDelay, STOP_DELAY_MILLISECONDS);
    }

    static class MessageSender {

        public void send(Message message) {
            message.sendToTarget();
        }
    }

    @NeededForTesting
    void setContextForTest(Context context) {
        mContext = context;
    }

    @NeededForTesting
    void setTaskAutoRunDisabledForTest(boolean value) {
        mTaskAutoRunDisabledForTesting = value;
    }

    @NeededForTesting
    void setMessageSenderForTest(MessageSender sender) {
        mMessageSender = sender;
    }

    @NeededForTesting
    void clearTasksForTest() {
        mTasks.clear();
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @NeededForTesting
    class LocalBinder extends Binder {

        @NeededForTesting
        public TaskSchedulerService getService() {
            return TaskSchedulerService.this;
        }
    }
}
