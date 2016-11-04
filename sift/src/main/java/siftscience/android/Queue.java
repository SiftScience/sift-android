// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The main class for batching events before sending them to the Sift
 * server with configurable batching policy.
 */
public class Queue {
    private static final String TAG = Queue.class.getName();

    /**
     * Configure queue's batching policy.
     *
     * At the moment the batching policy is not sophisticated.  When
     * either the number of events or the age of the last event exceeds
     * some threshold, all events of this queue will be uploaded to the
     * Sift server.
     */
    public static class Config {

        /**
         * When you are appending an event, if it basically equals to
         * the last event and the last event was appended less than
         * `acceptSameEventAfter` milliseconds ago, the new event will
         * be ignored (non-positive value will be ignored).
         *
         * This may optimize some resource usage when you expect that
         * the value of events appended to this queue is rarely changed.
         */
        public final long acceptSameEventAfter;

        /**
         * Upload events when the number of events is more than
         * `uploadWhenMoreThan` (non-positive value will be ignored).
         */
        public final int uploadWhenMoreThan;

        /**
         * Upload events when the last event was appended more than
         * `uploadWhenOlderThan` milliseconds ago (non-positive value
         * will be ignored).
         */
        public final long uploadWhenOlderThan;

        // The default no-args constructor for Gson.
        Config() {
            this(0, 0, 0);
        }

        private Config(long acceptSameEventAfter,
                       int uploadWhenMoreThan,
                       long uploadWhenOlderThan) {
            this.acceptSameEventAfter = acceptSameEventAfter;
            this.uploadWhenMoreThan = uploadWhenMoreThan;
            this.uploadWhenOlderThan = uploadWhenOlderThan;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Config)) {
                return false;
            }
            Config that = (Config) other;
            return acceptSameEventAfter == that.acceptSameEventAfter &&
                    uploadWhenMoreThan == that.uploadWhenMoreThan &&
                    uploadWhenOlderThan == that.uploadWhenOlderThan;
        }

        public static class Builder {
            private long acceptSameEventAfter = 0;
            public Builder withAcceptSameEventAfter(long acceptSameEventAfter) {
                this.acceptSameEventAfter = acceptSameEventAfter;
                return this;
            }

            private int uploadWhenMoreThan = 0;
            public Builder withUploadWhenMoreThan(int uploadWhenMoreThan) {
                this.uploadWhenMoreThan = uploadWhenMoreThan;
                return this;
            }

            private long uploadWhenOlderThan = 0;
            public Builder withUploadWhenOlderThan(long uploadWhenOlderThan) {
                this.uploadWhenOlderThan = uploadWhenOlderThan;
                return this;
            }

            public Config build() {
                return new Config(acceptSameEventAfter, uploadWhenMoreThan, uploadWhenOlderThan);
            }
        }
    }

    interface UserIdProvider {
        /**
         * Return the current user ID.
         *
         * A queue object will use this ID when an event object does
         * not have one.
         */
        String getUserId();
    }

    interface UploadRequester {
        /**
         * Request an upload.
         *
         * A queue object would normally request an upload when its
         * events are ready for upload (but note that events of other
         * queues may not be ready yet).
         *
         * This method should not be blocking.
         */
        void requestUpload();
    }

    // States that are archived.
    private static class State {

        Config config;
        List<Event> queue;
        Event lastEvent;
        long lastEventTimestamp;

        State() {
            config = new Config();
            queue = Lists.newLinkedList();
            lastEvent = null;
            lastEventTimestamp = 0;
        }
    }

    private final State state;
    private final ListeningScheduledExecutorService executor;

    private final UserIdProvider userIdProvider;
    private final UploadRequester uploadRequester;

    Queue(String archive,
          ListeningScheduledExecutorService executor,
          UserIdProvider userIdProvider,
          UploadRequester uploadRequester) {
        state = archive == null ? new State() : Sift.GSON.fromJson(archive, State.class);
        this.executor = executor;
        this.userIdProvider = userIdProvider;
        this.uploadRequester = uploadRequester;
    }

    synchronized String archive() {
        return Sift.GSON.toJson(state);
    }

    /** Return the queue configuration. */
    public synchronized Config getConfig() {
        return state.config;
    }

    /** Replace the queue configuration. */
    public synchronized void setConfig(Config config) {
        state.config = config;
    }

    /**
     * Append an event to this queue.
     *
     * The event could be ignored if `acceptSameEventAfter` is set.
     */
    public synchronized void append(@NonNull Event event) {
        long now = Time.now();

        if (event.userId == null) {
            event.userId = userIdProvider.getUserId();
        }

        if (state.config.acceptSameEventAfter > 0 &&
                now < state.lastEventTimestamp + state.config.acceptSameEventAfter &&
                state.lastEvent != null &&
                state.lastEvent.basicallyEquals(event)) {
            Log.d(TAG, String.format("Drop the same event of type \"%s\"", event.type));
            return;  // Drop the same event.
        }

        Log.i(TAG, String.format("Append event of type \"%s\"", event.type));
        state.queue.add(event);
        state.lastEvent = event;
        state.lastEventTimestamp = now;

        if (isEventsReadyForUpload(now)) {
            uploadRequester.requestUpload();
        } else if (state.config.uploadWhenOlderThan > 0) {
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    if (isEventsReadyForUpload(Time.now())) {
                        uploadRequester.requestUpload();
                    }
                }
            }, state.config.uploadWhenOlderThan, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * True if events of this queue are ready for upload as specified
     * by the queue config.
     */
    synchronized boolean isEventsReadyForUpload(long now) {
        return (state.config.uploadWhenMoreThan > 0 &&
                state.queue.size() > state.config.uploadWhenMoreThan) ||
               (state.config.uploadWhenOlderThan > 0 &&
                !state.queue.isEmpty() &&
                now > state.lastEventTimestamp + state.config.uploadWhenOlderThan);
    }

    /** Transfer the ownership of the events. */
    synchronized List<Event> transfer() {
        List<Event> events = state.queue;
        state.queue = Lists.newLinkedList();
        return events;
    }
}
