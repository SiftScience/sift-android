// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.sift.api.representations.MobileEventJson;

/** The main class of the Sift client library. */
public class Sift {
    public static final String SDK_VERSION = BuildConfig.VERSION_NAME;
    private static final String TAG = Sift.class.getName();

    private static Sift instance;
    private static int openCount;

    private static DevicePropertiesCollector devicePropertiesCollector;
    private static AppStateCollector appStateCollector;

    /**
     * Call this method in every Activity instance's `onCreate()`
     * method.
     */
    public static synchronized void open(@NonNull Context context) {
        open(context, null);
    }

    /**
     * Call this method in your application's main Activity.
     */
    public static synchronized void open(@NonNull Context context, Config config) {
        if (instance == null) {
            try {
                Context c = context.getApplicationContext();

                instance = new Sift(c, config);
                devicePropertiesCollector = new DevicePropertiesCollector(instance, c);
                appStateCollector = new AppStateCollector(instance, c,
                        context.getClass().getSimpleName(), get().executor);
            } catch (IOException e) {
                Log.e(TAG, "Encountered IOException in open", e);
            }
        }
        openCount++;
    }

    /**
     * Invoke a collection for DevicePropertiesCollector only.
     * AppStateCollector will wait for location callback.
     */
    public static synchronized void collect() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                devicePropertiesCollector.collect();
            }
        });
    }

    /**
     * Return the shared Sift object.
     */
    @Nullable
    public static synchronized Sift get() {
        return instance;
    }

    /**
     * Call this method in every Activity instance's `onDestroy()`
     * method.  In the last calling Activity, this method will release
     * the shared Sift object.
     *
     * Note that Android runtime does not guarantee to call your
     * `onDestroy()` method and so you should call `Sift.save()` in
     * your `onPause()` or `onSaveInstanceState()` method.
     */
    public static synchronized void close() {
        if (instance != null) {
            instance.save();
        }
        openCount--;
        if (openCount < 0) {
            Log.w(TAG, "Sift.close() is not paired with Sift.open()");
        }
        if (openCount <= 0) {
            if (instance != null) {
                instance.stop();
            }
            instance = null;
            openCount = 0;
        }
    }

    /**
     * Configurations of the Sift object.  Use the builder class to
     * create configurations.
     */
    public static class Config {

        private static final String DEFAULT_SERVER_URL_FORMAT =
                "https://api3.siftscience.com/v3/accounts/%s/mobile_events";

        /** Your account ID; defaults to null. */
        @SerializedName(value="account_id", alternate={"accountId"})
        public final String accountId;

        /** Your beacon key; defaults to null. */
        @SerializedName(value="beacon_key", alternate={"beaconKey"})
        public final String beaconKey;

        /** The location of the API endpoint. May be overwritten for testing. */
        @SerializedName(value="server_url_format", alternate={"serverUrlFormat"})
        public final String serverUrlFormat;

        /** Whether to allow location collection; defaults to false. */
        @SerializedName(value="disallow_location_collection", alternate={"disallowLocationCollection"})
        public final boolean disallowLocationCollection;

        // The default no-args constructor for JSON.
        Config() {
            this(null, null, DEFAULT_SERVER_URL_FORMAT, false);
        }

        private Config(String accountId,
                       String beaconKey,
                       String serverUrlFormat,
                       boolean disallowLocationCollection) {
            this.accountId = accountId;
            this.beaconKey = beaconKey;
            this.serverUrlFormat = serverUrlFormat;
            this.disallowLocationCollection = disallowLocationCollection;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Config)) {
                return false;
            }
            Config that  = (Config) other;
            return Utils.equals(accountId, that.accountId) &&
                    Utils.equals(beaconKey, that.beaconKey) &&
                    Utils.equals(serverUrlFormat, that.serverUrlFormat) &&
                    Utils.equals(disallowLocationCollection, that.disallowLocationCollection);
        }

        public static class Builder {

            public Builder() {
                serverUrlFormat = DEFAULT_SERVER_URL_FORMAT;
            }

            public Builder(Config config) {
                accountId = config.accountId;
                beaconKey = config.beaconKey;
                serverUrlFormat = config.serverUrlFormat;
                disallowLocationCollection = config.disallowLocationCollection;
            }

            private String accountId;
            public Builder withAccountId(String accountId) {
                this.accountId = accountId;
                return this;
            }

            private String beaconKey;
            public Builder withBeaconKey(String beaconKey) {
                this.beaconKey = beaconKey;
                return this;
            }

            private String serverUrlFormat;
            public Builder withServerUrlFormat(String serverUrlFormat) {
                this.serverUrlFormat = serverUrlFormat;
                return this;
            }

            private boolean disallowLocationCollection;
            public Builder withDisallowLocationCollection(boolean disallowLocationCollection) {
                this.disallowLocationCollection = disallowLocationCollection;
                return this;
            }

            public Config build() {
                return new Config(accountId, beaconKey, serverUrlFormat,
                        disallowLocationCollection);
            }
        }
    }

    // Technically we don't need a naming policy since we have full @SerializedName coverage –
    // this is just defensive to ensure consistent writes in case we leave something out
    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    // The default queue
    public static final String DEVICE_PROPERTIES_QUEUE_IDENTIFIER = "siftscience.android.device";
    private static final Queue.Config DEVICE_PROPERTIES_QUEUE_CONFIG = new Queue.Config.Builder()
            .withAcceptSameEventAfter(TimeUnit.HOURS.toMillis(1))
            .withUploadWhenMoreThan(0)
            .build();

    public static final String APP_STATE_QUEUE_IDENTIFIER = "siftscience.android.app";
    private static final Queue.Config APP_STATE_QUEUE_CONFIG = new Queue.Config.Builder()
            .withUploadWhenMoreThan(32)
            .withUploadWhenOlderThan(TimeUnit.MINUTES.toMillis(1))
            .build();

    private static final String ARCHIVE_NAME = "siftscience";

    /** Keys we use in the archive. */
    private enum ArchiveKey {
        CONFIG("config"),
        USER_ID("user_id"),
        UPLOADER("uploader"),  // For the Uploader instance
        QUEUE("queue");  // For Queue instances

        public final String key;

        ArchiveKey(String key) {
            this.key = key;
        }

        static String getKeyForQueueIdentifier(String identifier) {
            return String.format("%s/%s", QUEUE.key, identifier);
        }

        static String getQueueIdentifier(String key) {
            if (!key.startsWith(QUEUE.key)) {
                return null;
            }
            int index = key.indexOf('/');
            return index == -1 ? null : key.substring(index + 1);
        }
    }

    private final SharedPreferences archives;
    private final ScheduledExecutorService executor;
    private Config config;
    private String userId;
    private final Uploader uploader;
    private final Map<String, Queue> queues;

    private final Queue.UserIdProvider userIdProvider = new Queue.UserIdProvider() {
        @Override
        public String getUserId() {
            return Sift.this.getUserId();
        }
    };

    private final Queue.UploadRequester uploadRequester = new Queue.UploadRequester() {
        @Override
        public void requestUpload() {
            Sift.this.upload(false);
        }
    };

    private final Uploader.ConfigProvider configProvider = new Uploader.ConfigProvider() {
        @Override
        public Config getConfig() {
            return Sift.this.getConfig();
        }
    };

    private Sift(Context context, Config config) throws IOException {
        this(context, config, Executors.newSingleThreadScheduledExecutor());
    }

    private Config unarchive(String archive, Config c) {
        if (archive == null) {
            return c == null ? new Config() : c;
        }

        try {
            return Sift.GSON.fromJson(archive, Config.class);
        } catch (JsonParseException e) {
            Log.e(TAG, "Encountered JsonProcessingException in Config constructor", e);
            return new Config();
        }
    }

    @VisibleForTesting
    String archiveConfig() {
        return GSON.toJson(config);
    }

    Config unarchiveConfig(String archive, Config c) {
        if (archive == null) {
            return c == null ? new Config() : c;
        }

        try {
            return Sift.GSON.fromJson(archive, Config.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Sift config unarchive");
            return c == null ? new Config() : c;
        }
    }

    @VisibleForTesting
    String archiveConfig() {
        return Sift.GSON.toJson(config);
    }

    Config unarchiveConfig(String archive, Config c) {
        if (archive == null) {
            return c == null ? new Config() : c;
        }

        try {
            return Sift.GSON.fromJson(archive, Config.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Sift config unarchive", e);
            return c == null ? new Config() : c;
        }
    }

    @VisibleForTesting
    Sift(Context context, Config conf, ScheduledExecutorService executor)
            throws IOException {
        archives = context.getSharedPreferences(ARCHIVE_NAME, Context.MODE_PRIVATE);
        this.executor = executor;

        // Load archived data
        String archive;

        archive = archives.getString(ArchiveKey.CONFIG.key, null);

        config = unarchiveConfig(archive, conf);

        userId = archives.getString(ArchiveKey.USER_ID.key, null);

        archive = archives.getString(ArchiveKey.UPLOADER.key, null);
        uploader = new Uploader(archive, executor, configProvider);

        queues = new HashMap<>();
        for (Map.Entry<String, ?> entry : archives.getAll().entrySet()) {
            String identifier = ArchiveKey.getQueueIdentifier(entry.getKey());
            if (identifier != null) {
                Log.d(TAG, String.format("Load queue \"%s\"", identifier));
                archive = (String) entry.getValue();
                Queue queue = new Queue(archive, userIdProvider, uploadRequester);
                queues.put(identifier, queue);
            }
        }

        // Construction is completed; now you may call instance methods.
        // Create the queues if they don't exist.
        if (!queues.containsKey(DEVICE_PROPERTIES_QUEUE_IDENTIFIER)) {
            createQueue(DEVICE_PROPERTIES_QUEUE_IDENTIFIER, DEVICE_PROPERTIES_QUEUE_CONFIG);
        }

        if (!queues.containsKey(APP_STATE_QUEUE_IDENTIFIER)) {
            createQueue(APP_STATE_QUEUE_IDENTIFIER, APP_STATE_QUEUE_CONFIG);
        }
    }

    @VisibleForTesting
    void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                Log.w(TAG, "Some tasks are not terminated yet before timeout");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when awaiting executor", e);
        }
    }

    /**
     * Save Sift object states (including sub-objects like Uploader and
     * Queue).  You should call this in your `onPause()` or
     * `onSaveInstanceState()` method.
     */
    public synchronized void save() {
        Log.d(TAG, "Save Sift object states");
        SharedPreferences.Editor editor = archives.edit();
        editor.clear();
        try {
            editor.putString(ArchiveKey.CONFIG.key, archiveConfig());
            editor.putString(ArchiveKey.USER_ID.key, userId);
            editor.putString(ArchiveKey.UPLOADER.key, uploader.archive());
            for (Map.Entry<String, Queue> entry : queues.entrySet()) {
                String identifier = ArchiveKey.getKeyForQueueIdentifier(entry.getKey());
                Log.d(TAG, String.format("Save queue \"%s\"", identifier));
                editor.putString(identifier, entry.getValue().archive());
            }
            editor.apply();
        } catch (JsonParseException e) {
            Log.e(TAG, "Encountered JsonProcessingException in save", e);
        }
        this.appStateCollector.disconnectLocationServices();
    }

    public synchronized void resume() {
        Log.d(TAG, "Save Sift object states");
        this.appStateCollector.reconnectLocationServices();
    }

    /** Return the configurations of this Sift object. */
    public synchronized Config getConfig() {
        return config;
    }

    /** Replace the configurations of this Sift object. */
    public synchronized void setConfig(Config config) {
        this.config = config;
    }

    /**
     * Return the default user ID.  We will use this ID if you didn't
     * set one in the Event object.
     */
    public synchronized String getUserId() {
        return this.userId;
    }

    /**
     * Set the default user ID.  We will use this ID if you didn't set
     * one in the Event object.
     */
    public synchronized void setUserId(String userId) {
        this.userId = userId;
    }

    public synchronized void unsetUserId() {
        this.userId = null;
    }

    /** Create an event queue. */
    public synchronized Queue createQueue(@NonNull String identifier, Queue.Config config)
            throws IOException {
        if (getQueue(identifier) != null) {
            throw new IllegalStateException("queue exists: " + identifier);
        }

        Log.i(TAG, String.format("Create queue \"%s\"", identifier));
        Queue queue = new Queue(null, userIdProvider, uploadRequester);
        queue.setConfig(config);
        queues.put(identifier, queue);
        return queue;
    }

    /**
     * Return the event queue for the given identifier or null if none
     * exists.
     */
    @Nullable
    public synchronized Queue getQueue(@NonNull String identifier) {
        return queues.get(identifier);
    }

    /**
     * Request an upload.  If `force` is true, it will disregard the
     * queue's batching policy.
     */
    public synchronized void upload(boolean force) {
        // Upload all queues if any are ready for upload
        boolean shouldUpload = force;
        Iterator<Queue> iterator = queues.values().iterator();
        while (!shouldUpload && iterator.hasNext()) {
            shouldUpload = iterator.next().isEventsReadyForUpload(Time.now());
        }

        if (shouldUpload) {
            List<MobileEventJson> events = new ArrayList<>();

            for (Queue queue : queues.values()) {
                events.addAll(queue.transfer());
            }

            if (!events.isEmpty()) {
                uploader.upload(events);
            }
        }
    }
}
