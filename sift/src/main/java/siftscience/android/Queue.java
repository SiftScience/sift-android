// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.sift.api.representations.MobileEventJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data structure for holding events until they are ready for upload.
 */
public class Queue {
    private static final String TAG = Queue.class.getName();
    private final UserIdProvider userIdProvider;
    private final UploadRequester uploadRequester;

    /**
     * Configuration for Queue's batching policy.
     */
    public static class Config {

        /**
         * Time after which an event that is basically the same as the most
         * recently appended event can be appended again.
         */
        @SerializedName(value="accept_same_event_after", alternate={"acceptSameEventAfter"})
        public final long acceptSameEventAfter;

        /**
         * Max queue depth before flush and upload request.
         */
        @SerializedName(value="upload_when_more_than", alternate={"uploadWhenMoreThan"})
        public final int uploadWhenMoreThan;

        /**
         * Max queue age before flush and upload request.
         */
        @SerializedName(value="upload_when_older_than", alternate={"uploadWhenOlderThan"})
        public final long uploadWhenOlderThan;

        Config() {
            this(0, -1, 0);
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

            private int uploadWhenMoreThan = -1;
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
        String getUserId();
    }

    interface UploadRequester {
        void requestUpload(List<MobileEventJson> events);
    }

    private static class State {
        @SerializedName("config")
        Config config;
        @SerializedName("queue")
        List<MobileEventJson> queue;
        @SerializedName(value="last_event", alternate={"lastEvent"})
        MobileEventJson lastEvent;
        @SerializedName(value="last_upload_timestamp", alternate={"lastUploadTimestamp"})
        long lastUploadTimestamp;

        State() {
            config = new Config();
            queue = new ArrayList<>();
            lastEvent = null;
            lastUploadTimestamp = 0;
        }
    }

    private final State state;

    Queue(String archive,
          UserIdProvider userIdProvider,
          UploadRequester uploadRequester) {
        state = unarchive(archive);

        this.userIdProvider = userIdProvider;
        this.uploadRequester = uploadRequester;
    }

    /**
     *
     * @return
     * @throws JsonParseException
     */
    String archive() throws JsonParseException {
        return Sift.GSON.toJson(state);
    }

    /**
     *
     * @param archive
     * @return
     */
    State unarchive(String archive) {
        if (archive == null) {
            return new State();
        }

        try {
            return Sift.GSON.fromJson(archive, State.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Queue state unarchive", e);
            return new State();
        }
    }

    /**
     *
     * @return
     */
    Config getConfig() {
        return state.config;
    }

    /**
     *
     * @param config
     */
    void setConfig(Config config) {
        state.config = config;
    }

    /**
     *
     * @return
     */
    public List<MobileEventJson> flush() {
        List<MobileEventJson> events = state.queue;
        state.queue = new ArrayList<>();
        return events;
    }

    /**
     *
     * @param event
     */
    void append(@NonNull MobileEventJson event) {
        long now = Time.now();

        if (event.userId == null) {
            event = MobileEventJson.newBuilder(event)
                    .withUserId(userIdProvider.getUserId())
                    .build();
        }

        if (state.config.acceptSameEventAfter > 0 &&
                state.lastEvent != null &&
                now < state.lastEvent.time + state.config.acceptSameEventAfter &&
                Utils.eventsAreBasicallyEqual(state.lastEvent, event)) {
            Log.d(TAG, String.format("Drop duplicate event \"%s\"", event.toString()));
            return;
        }

        Log.d(TAG, String.format("Append event \"%s\"", event.toString()));
        state.queue.add(event);
        state.lastEvent = event;

        if (this.isReadyForUpload(now)) {
            state.lastUploadTimestamp = now;
            this.uploadRequester.requestUpload(flush());
        }
    }

    /**
     *
     * @param now
     * @return
     */
    private boolean isReadyForUpload(long now) {
        return (state.config.uploadWhenMoreThan >= 0 &&
                state.queue.size() > state.config.uploadWhenMoreThan) ||
               (state.config.uploadWhenOlderThan > 0 &&
                !state.queue.isEmpty() &&
                now > state.lastUploadTimestamp + state.config.uploadWhenOlderThan);
    }
}
