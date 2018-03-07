// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by gary on 3/1/18.
 */

class TaskManager {
    private static final String TAG = TaskManager.class.getName();
    private final ScheduledExecutorService executor;

    TaskManager() {
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    void submit(Runnable task) {
        Log.d(TAG, String.format("%s submitted", task.getClass().getSimpleName()));

        try {
            this.executor.submit(task);
        } catch (RejectedExecutionException e) {
            Log.d(TAG, "Dropped submitted task due to RejectedExecutionException");
        }
    }

    void schedule(Runnable task, long delay, TimeUnit unit) {
        Log.d(TAG, String.format("%s submitted", task.getClass().getSimpleName()));

        try {
            this.executor.schedule(task, delay, unit);
        } catch (RejectedExecutionException e) {
            Log.d(TAG, "Dropped scheduled task due to RejectedExecutionException");
        }
    }

    void shutdown() {
        Log.d(TAG, "Shutting down");

        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(1, TimeUnit.SECONDS)) {
                Log.w(TAG, "Some tasks are not terminated yet before timeout");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when awaiting executor termination", e);
        }
    }
}
