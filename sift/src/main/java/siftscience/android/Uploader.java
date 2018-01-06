// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Base64;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.sift.api.representations.MobileEventJson;
import com.sift.api.representations.ListRequestJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Accept batches of events and send them to the Sift server serially
 * in a background thread.
 */
class Uploader {
    private static final String TAG = Uploader.class.getName();

    interface ConfigProvider {
        Sift.Config getConfig();
    }

    @VisibleForTesting static final int REJECTION_LIMIT = 3;
    private static final long INITIAL_BACKOFF = TimeUnit.SECONDS.toMillis(5);

    /**
     * The state of uploader.
     *
     * We implement the uploader as a finite state machine.  Although
     * this class is not strictly necessary, it's easier to trace the
     * state transition if we bundle them together in one place.
     */
    private static class State {
        /** The HTTP request we are currently sending. */
        Request request;

        /**
         * The amount of milliseconds we will wait for sending the
         * current batch again when the last HTTP request failed.
         */
        final long initialBackoff;
        long backoff;
        long nextUploadTime;

        /** The number of times that this batch has been rejected. */
        int numRejects;

        State(long initialBackoff) {
            this.initialBackoff = initialBackoff;
            reset();
        }

        void reset() {
            request = null;
            backoff = initialBackoff;
            nextUploadTime = 0;
            numRejects = 0;
        }
    }

    /**
     * Wrapper of a FIFO of events (each group of events is called a
     * batch) to provide atomic operations.
     */
    private static class Batches {
        @SerializedName(value="batches")
        private final List<List<MobileEventJson>> batches;

        Batches() {
            batches = new LinkedList<>();
        }

        synchronized String archive() throws JsonParseException {
            return Sift.GSON.toJson(this);
        }

        /** Append a batch to the end of FIFO. */
        synchronized void append(List<MobileEventJson> batch) {
            batches.add(batch);
        }

        /** Peek the first batch. */
        synchronized List<MobileEventJson> peek() {
            return batches.isEmpty() ? null : batches.get(0);
        }

        /** Remove the first batch. */
        synchronized void pop() {
            if (!batches.isEmpty()) {
                batches.remove(0);
            }
        }
    }

    private final ScheduledExecutorService executor;
    private final State state;
    private final Future unarchiveFuture;

    private final ConfigProvider configProvider;
    private final OkHttpClient client;

    private Batches batches = new Batches();

    // These two semaphores are used as counters in unit tests
    @VisibleForTesting
    Semaphore onRequestCompletion;
    @VisibleForTesting
    Semaphore onRequestRejection;

    Uploader(String archive,
             ScheduledExecutorService executor,
             ConfigProvider configProvider) throws IOException {
        this(archive, INITIAL_BACKOFF, executor, configProvider, new OkHttpClient());
    }

    @VisibleForTesting
    Uploader(final String archive,
             long initialBackoff,
             ScheduledExecutorService executor,
             ConfigProvider configProvider,
             OkHttpClient client) throws IOException {

        state = new State(initialBackoff);
        this.executor = executor;
        this.configProvider = configProvider;
        this.client = client;

        this.unarchiveFuture = this.executor.submit(new Runnable() {
            @Override
            public void run() {
                synchronized (state) {
                    batches = unarchive(archive);
                    safeSubmit(checkState);
                }
            }
        });
    }

    String archive() throws JsonParseException {
        try {
            this.unarchiveFuture.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
        }

        return batches.archive();
    }

    Batches unarchive(String archive) {
        if (archive == null) {
            return new Batches();
        }

        try {
            return Sift.GSON.fromJson(archive, Batches.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Batches unarchive", e);
            return new Batches();
        }
    }


    void upload(List<MobileEventJson> events) {
        try {
            this.unarchiveFuture.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
        }

        Log.d(TAG, String.format("Append batch: size=%d", events.size()));
        if (!events.isEmpty()) {
            batches.append(events);
        }
        safeSubmit(checkState);
    }

    /**
     * This is the core of the uploader finite state machine that checks the state,
     * performs related actions, and advances the state.
     */
    private final Runnable checkState = new Runnable() {
        @Override
        public void run() {
            synchronized (state) {
                if (state.request == null) {
                    try {
                        state.request = makeRequest();
                    } catch (IOException e) {
                        Log.e(TAG, "Encountered IOException in makeRequest", e);
                    }
                }
                if (state.request == null) {
                    // Nothing to upload at the moment; check again a minute later
                    safeSchedule(checkState, 60, TimeUnit.SECONDS);
                    return;
                }

                long now = Time.now();
                if (state.nextUploadTime > now) {
                    // We've woken up too early; go back to sleep
                    long delay = state.nextUploadTime - now;
                    safeSchedule(checkState, delay, TimeUnit.MILLISECONDS);
                    return;
                }

                try {
                    Log.d(TAG, "Send HTTP request");
                    // try-with needs API level 19+ :(
                    Response response = client.newCall(state.request).execute();
                    int code = response.code();
                    String body = response.body() == null ? null : response.body().string();
                    response.close();

                    if (code == 200) {
                        // Success, reset the state and move on to the next batch
                        state.reset();
                        batches.pop();
                        safeSubmit(checkState);

                        // This is for testing
                        if (onRequestCompletion != null) {
                            onRequestCompletion.release();
                        }

                        return;
                    } else if (code == 400) {
                        state.numRejects = REJECTION_LIMIT;
                    } else {
                        state.numRejects++;
                    }

                    Log.e(TAG, String.format(
                            "HTTP error when uploading batch: status=%d response=%s", code, body));


                } catch (IOException e) {
                    Log.e(TAG, "Network error when uploading batch", e);
                }

                if (state.numRejects >= REJECTION_LIMIT) {
                    // This request has been rejected repeatedly;
                    // reset the state and move on to the next batch
                    Log.e(TAG, "Drop batch due to repeated rejection");
                    state.reset();
                    batches.pop();

                    safeSubmit(checkState);

                    // This is for testing
                    if (onRequestRejection != null) {
                        onRequestRejection.release();
                    }

                    return;
                }

                // This request was rejected; retry this request later
                safeSchedule(checkState, state.backoff, TimeUnit.MILLISECONDS);
                state.nextUploadTime = now + state.backoff;
                state.backoff *= 2;  // Exponential backoff.
            }
        }
    };

    private static final MediaType JSON = MediaType.parse("application/json");

    // StandardCharsets.US_ASCII is defined in API level 19 and we are
    // targeting API level 16.
    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Make an HTTP request object from the first batch of events. */
    @Nullable
    private Request makeRequest() throws IOException {
        List<MobileEventJson> events = batches.peek();
        if (events == null) {
            return null;  // Nothing to upload
        }

        Sift.Config config = configProvider.getConfig();
        if (config == null) {
            Log.d(TAG, "Missing Sift.Config object");
            return null;
        }

        if (config.accountId == null ||
                config.beaconKey == null ||
                config.serverUrlFormat == null) {
            Log.w(TAG, "Missing account ID, beacon key, and/or server URL format");
            return null;
        }

        Log.d(TAG, String.format("Create HTTP request for batch: size=%d", events.size()));

        String encodedBeaconKey =  Base64.encodeToString(config.beaconKey.getBytes(US_ASCII),
                Base64.NO_WRAP);

        ListRequestJson<MobileEventJson> request = ListRequestJson.<MobileEventJson>newBuilder()
                .withData(events)
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream gzip = new GZIPOutputStream(os);
        Writer writer = new OutputStreamWriter(gzip, UTF8);
        Sift.GSON.toJson(request, writer);
        writer.close();

        return new Request.Builder()
                .url(String.format(config.serverUrlFormat, config.accountId))
                .header("Authorization", "Basic " + encodedBeaconKey)
                .header("Content-Encoding", "gzip")
                .put(RequestBody.create(JSON, os.toByteArray()))
                .build();
    }

    private void safeSchedule(Runnable command, long delay, TimeUnit unit) {
        try {
            executor.schedule(command, delay, unit);
        } catch (RejectedExecutionException e) {
            if (!executor.isShutdown()) {
                Log.e(TAG, "Dropped scheduled task due to RejectedExecutionException");
            }
        }
    }

    private void safeSubmit(Runnable command) {
        try {
            executor.submit(command);
        } catch (RejectedExecutionException e) {
            if (!executor.isShutdown()) {
                Log.e(TAG, "Dropped submitted task due to RejectedExecutionException");
            }
        }
    }
}
