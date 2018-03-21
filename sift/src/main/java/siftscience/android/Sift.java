// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.sift.api.representations.MobileEventJson;

/** The public API of the Sift client library. */
public class Sift {

    //================================================================================
    // Static members
    //================================================================================

    public static final String SDK_VERSION = BuildConfig.VERSION_NAME;
    public static final String DEVICE_PROPERTIES_QUEUE_IDENTIFIER = "siftscience.android.device";
    public static final String APP_STATE_QUEUE_IDENTIFIER = "siftscience.android.app";

    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static Sift instance;
    private static int openCount;
    private static DevicePropertiesCollector devicePropertiesCollector;
    private static AppStateCollector appStateCollector;
    private static final String ARCHIVE_NAME = "siftscience";

    private static final Queue.Config DEVICE_PROPERTIES_QUEUE_CONFIG = new Queue.Config.Builder()
            .withAcceptSameEventAfter(TimeUnit.HOURS.toMillis(1))
            .withUploadWhenMoreThan(0)
            .build();

    private static final Queue.Config APP_STATE_QUEUE_CONFIG = new Queue.Config.Builder()
            .withUploadWhenMoreThan(8)
            .withUploadWhenOlderThan(TimeUnit.MINUTES.toMillis(1))
            .build();

    private static final String TAG = Sift.class.getName();



    //================================================================================
    // Static API
    //================================================================================

    /**
     * Call Sift.open() in the onCreate() callback of each Activity.
     *
     * Creates the Sift singleton and collectors if they do not exist,
     * and passes along the current Activity context.
     *
     * For your application's main Activity, make sure to provide a Sift.Config
     * object as the second parameter.
     *
     * If you are integrating per-Activity rather than at the Application level,
     * you can specify the name that will be associated with each Activity event
     * (defaults to the class name of the embedding Activity).
     *
     * There are overloaded methods below for your convenience.
     *
     * @param context the Activity context
     * @param config the Sift.Config object
     * @param activityName the Activity
     */
    public static synchronized void open(@NonNull Context context,
                                         Config config,
                                         String activityName) {
        if (instance == null) {
            try {
                Context c = context.getApplicationContext();
                instance = new Sift(c, config);
                devicePropertiesCollector = new DevicePropertiesCollector(instance, c);
                appStateCollector = new AppStateCollector(instance, c);
            } catch (IOException e) {
                Log.e(TAG, "Encountered IOException in open", e);
            }
        }

        appStateCollector.setActivityName(activityName == null ?
                context.getClass().getSimpleName() : activityName);
        openCount++;
    }

    public static synchronized void open(@NonNull Context context, String activityName) {
        open(context, null, activityName);
    }

    public static synchronized void open(@NonNull Context context, Config config) {
        open(context, config, null);
    }

    public static synchronized void open(@NonNull Context context) {
        open(context, null, null);
    }

    /**
     * Call Sift.collect() after the Sift.open() call in each Activity.
     *
     * Collect SDK events for Device Properties and Application State.
     */
    public static void collect() {
        devicePropertiesCollector.collect();
        appStateCollector.collect();
    }

    /**
     * Call Sift.pause() in the onPause() callback of each Activity.
     *
     * Persists instance state to disk and disconnects location services.
     */
    public static void pause() {
        instance.save();
    }

    /**
     * Call Sift.close() in the onDestroy() callback of each Activity.
     *
     * Persists instance state to disk and disconnects location services.
     * In the last calling Activity, releases the Sift singleton and its
     * executor.
     */
    public static void close() {
        if (instance != null) {
            instance.save();
        }
        openCount--;
        if (openCount < 0) {
            Log.d(TAG, "Sift.close() is not paired with Sift.open()");
        }
        if (openCount <= 0) {
            if (instance != null) {
                instance.stop();
            }
            instance = null;
            openCount = 0;
        }
        appStateCollector.disconnectLocationServices();
    }

    /**
     * @return the Sift singleton instance
     */
    @Nullable
    public static synchronized Sift get() {
        return instance;
    }



    //================================================================================
    // Instance members
    //================================================================================

    private final SharedPreferences archives;
    private final TaskManager taskManager;
    private Config config;
    private String userId;
    private final Map<String, Queue> queues;
    private final Uploader uploader;

    private final Queue.UserIdProvider userIdProvider = new Queue.UserIdProvider() {
        @Override
        public String getUserId() {
            return Sift.this.getUserId();
        }
    };

    private final Queue.UploadRequester uploadRequester = new Queue.UploadRequester() {
        @Override
        public void requestUpload(List<MobileEventJson> events) {
            Sift.this.upload(events);
        }
    };

    private final Uploader.ConfigProvider configProvider = new Uploader.ConfigProvider() {
        @Override
        public Config getConfig() {
            return Sift.this.getConfig();
        }
    };

    private enum ArchiveKey {
        CONFIG("config"),
        USER_ID("user_id"),
        QUEUE("queue");

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

    Sift(Context context, Config config) throws IOException {
        this(context, config, new TaskManager());
    }

    String archiveConfig() {
        return Sift.GSON.toJson(config);
    }

    Config unarchiveConfig(String archive) {
        if (archive == null) {
            return config == null ? new Config() : config;
        }

        try {
            return Sift.GSON.fromJson(archive, Config.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Sift config unarchive", e);
            return config == null ? new Config() : config;
        }
    }



    //================================================================================
    // Instance API
    //================================================================================

    Sift(Context context, Config conf, TaskManager taskManager)
            throws IOException {
        this.archives = context.getSharedPreferences(ARCHIVE_NAME, Context.MODE_PRIVATE);
        this.taskManager = taskManager;
        this.config = conf;
        this.queues = new HashMap<>();
        this.uploader = new Uploader(taskManager, configProvider);
        this.taskManager.submit(new UnarchiveTask());
    }

    /**
     * @return the configuration for the Sift instance
     */
    public synchronized Config getConfig() {
        return config;
    }

    /**
     * Sets the configuration for the Sift instance
     *
     * @param config
     */
    public void setConfig(Config config) {
        this.taskManager.submit(new SetConfigTask(config));
    }

    /**
     * @return the user ID for the Sift instance
     */
    public synchronized String getUserId() {
        return this.userId;
    }

    /**
     * Sets the user ID for the Sift instance
     *
     * @param userId
     */
    public void setUserId(String userId) {
        this.taskManager.submit(new SetUserIdTask(userId));
    }

    /**
     * Unsets the user ID for the Sift instance.
     */
    public void unsetUserId() {
        this.taskManager.submit(new SetUserIdTask(null));
    }

    /**
     * Save Sift object states.
     *
     * Call Sift.get().save() in the onPause() or onSaveInstanceState()
     * callbacks of each Activity.
     *
     * Prefer to use the static method Sift.pause() instead.
     */
    public void save() {
        this.taskManager.submit(new ArchiveTask());
        appStateCollector.disconnectLocationServices();
    }

    public void stop() {
        this.taskManager.shutdown();
    }

    public void resume() {
        appStateCollector.reconnectLocationServices();
    }

    public void appendAppStateEvent(MobileEventJson event) {
        this.taskManager.submit(new AppendTask(
                APP_STATE_QUEUE_IDENTIFIER,
                event
        ));
    }

    public void appendDevicePropertiesEvent(MobileEventJson event) {
        this.taskManager.submit(new AppendTask(
                DEVICE_PROPERTIES_QUEUE_IDENTIFIER,
                event
        ));
    }

    private void upload(List<MobileEventJson> events) {
        this.uploader.upload(events);
    }

    Queue createQueue(@NonNull String identifier, Queue.Config config) {
        if (getQueue(identifier) != null) {
            throw new IllegalStateException(String.format("Queue exists: \"%s\"", identifier));
        }

        Queue queue = new Queue(null, userIdProvider, uploadRequester);
        queue.setConfig(config);
        queues.put(identifier, queue);
        Log.i(TAG, String.format("Created new \"%s\" queue", identifier));
        return queue;
    }

    @Nullable
    Queue getQueue(@NonNull String identifier) {
        return queues.get(identifier);
    }



    //================================================================================
    // Tasks
    //================================================================================

    /**
     * Saves all of the Sift instance state to disk.
     */
    private class ArchiveTask implements Runnable {
        @Override
        public void run() {
            SharedPreferences.Editor editor = archives.edit();
            editor.clear();
            try {
                editor.putString(ArchiveKey.CONFIG.key, archiveConfig());
                Log.d(TAG, String.format("Archived Sift config: \"%s\"", archiveConfig()));
                editor.putString(ArchiveKey.USER_ID.key, getUserId());
                Log.d(TAG, String.format("Archived User ID: \"%s\"", getUserId()));
                for (Map.Entry<String, Queue> entry : queues.entrySet()) {
                    String identifier = ArchiveKey.getKeyForQueueIdentifier(entry.getKey());
                    Log.d(TAG, String.format("Archived \"%s\" Queue", identifier));
                    editor.putString(identifier, entry.getValue().archive());
                }
            } finally {
                editor.apply();
            }
        }
    }

    /**
     * Restores all of the Sift instance state from disk.
     */
    private class UnarchiveTask implements Runnable {
        @Override
        public void run() {
            String archive;

            // Unarchive Sift config
            archive = archives.getString(ArchiveKey.CONFIG.key, null);
            config = unarchiveConfig(archive);
            Log.d(TAG, String.format("Unarchived Sift config: \"%s\"", archive));

            // Unarchive User ID
            userId = archives.getString(ArchiveKey.USER_ID.key, null);
            Log.d(TAG, String.format("Unarchived User ID: \"%s\"", userId));

            // Unarchive Queues
            for (Map.Entry<String, ?> entry : archives.getAll().entrySet()) {
                String identifier = ArchiveKey.getQueueIdentifier(entry.getKey());
                if (identifier != null) {
                    archive = (String) entry.getValue();
                    Queue queue = new Queue(archive, userIdProvider, uploadRequester);
                    Log.d(TAG, String.format("Unarchived \"%s\" Queue", identifier));
                    queues.put(identifier, queue);
                }
            }

            if (!queues.containsKey(DEVICE_PROPERTIES_QUEUE_IDENTIFIER)) {
                createQueue(DEVICE_PROPERTIES_QUEUE_IDENTIFIER, DEVICE_PROPERTIES_QUEUE_CONFIG);
            }

            if (!queues.containsKey(APP_STATE_QUEUE_IDENTIFIER)) {
                createQueue(APP_STATE_QUEUE_IDENTIFIER, APP_STATE_QUEUE_CONFIG);
            }
        }
    }

    private class SetUserIdTask implements Runnable {
        private String userId;

        SetUserIdTask(String userId) {
            this.userId = userId;
        }

        @Override
        public void run() {
            synchronized (Sift.this) {
                Sift.this.userId = this.userId;
            }
        }
    }

    private class SetConfigTask implements Runnable {
        private Sift.Config config;

        SetConfigTask(Config config) {
            this.config = config;
        }

        @Override
        public void run() {
            synchronized (Sift.this) {
                Sift.this.config = this.config;
            }
        }
    }

    /**
     * Appends an event to the specified queue.
     */
    private class AppendTask implements Runnable {
        private String queueIdentifier;
        private MobileEventJson event;

        AppendTask(String queueIdentifier, MobileEventJson event) {
            this.queueIdentifier = queueIdentifier;
            this.event = event;
        }

        @Override
        public void run() {
            Queue queue = getQueue(this.queueIdentifier);
            if (queue != null) {
                queue.append(this.event);
            }
        }
    }



    //================================================================================
    // Instance config
    //================================================================================

    /**
     * Configurations of the Sift object.
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



    //================================================================================
    // Deprecated API (pre-0.9.14)
    //================================================================================

    /**
     * Requests an upload.  If `force` is true, it will disregard the
     * queue's batching policy.
     *
     * This is now a no-op, since we do not want to allow arbitrary
     * uploads to be triggered.
     */
    @Deprecated
    public void upload(boolean force) {
    }
}
