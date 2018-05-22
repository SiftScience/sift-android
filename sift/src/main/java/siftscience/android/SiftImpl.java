package siftscience.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.JsonSyntaxException;
import com.sift.api.representations.MobileEventJson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation for Sift instance.
 */
class SiftImpl {
    private static final String TAG = SiftImpl.class.getName();
    private static final String ARCHIVE_NAME = "siftscience";

    static final String DEVICE_PROPERTIES_QUEUE_IDENTIFIER = "siftscience.android.device";
    static final String APP_STATE_QUEUE_IDENTIFIER = "siftscience.android.app";

    private static final Queue.Config DEVICE_PROPERTIES_QUEUE_CONFIG = new Queue.Config.Builder()
            .withAcceptSameEventAfter(TimeUnit.HOURS.toMillis(1))
            .withUploadWhenMoreThan(0)
            .withUploadWhenOlderThan(TimeUnit.MINUTES.toMillis(1))
            .build();

    private static final Queue.Config APP_STATE_QUEUE_CONFIG = new Queue.Config.Builder()
            .withUploadWhenMoreThan(8)
            .withUploadWhenOlderThan(TimeUnit.MINUTES.toMillis(1))
            .build();



    //================================================================================
    // Instance members
    //================================================================================

    private final SharedPreferences archives;
    private final TaskManager taskManager;
    private Sift.Config config;
    private String userId;
    private final Map<String, Queue> queues;
    private final Uploader uploader;

    private final Queue.UserIdProvider userIdProvider = new Queue.UserIdProvider() {
        @Override
        public String getUserId() {
            return SiftImpl.this.getUserId();
        }
    };

    private final Queue.UploadRequester uploadRequester = new Queue.UploadRequester() {
        @Override
        public void requestUpload(List<MobileEventJson> events) {
            SiftImpl.this.upload(events);
        }
    };

    private final Uploader.ConfigProvider configProvider = new Uploader.ConfigProvider() {
        @Override
        public Sift.Config getConfig() {
            return SiftImpl.this.getConfig();
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

    SiftImpl(Context context, Sift.Config config,
             Sift.Config unboundConfig, boolean hasUnboundConfig,
             String unboundUserId, boolean hasUnboundUserId) {
        this(context, config, unboundConfig, hasUnboundConfig, unboundUserId, hasUnboundUserId,
                new TaskManager());
    }

    String archiveConfig() {
        return Sift.GSON.toJson(config);
    }

    Sift.Config unarchiveConfig(String archive) {
        if (archive == null) {
            return config == null ? new Sift.Config() : config;
        }

        try {
            return Sift.GSON.fromJson(archive, Sift.Config.class);
        } catch (JsonSyntaxException e) {
            Log.d(TAG, "Encountered exception in Sift.Config unarchive", e);
            return config == null ? new Sift.Config() : config;
        }
    }



    //================================================================================
    // Instance API
    //================================================================================

    SiftImpl(Context context, Sift.Config conf, Sift.Config unboundConfig, boolean hasUnboundConfig,
             String unboundUserId, boolean hasUnboundUserId, TaskManager taskManager) {
        this.archives = context.getSharedPreferences(ARCHIVE_NAME, Context.MODE_PRIVATE);
        this.taskManager = taskManager;
        this.config = conf;
        if (conf == null && hasUnboundConfig) {
            this.config = unboundConfig;
        }
        if (hasUnboundUserId) {
            this.userId = unboundUserId;
            Log.d(TAG, String.format("Using unbound User ID: %s", userId));
        }
        this.queues = new HashMap<>();
        this.uploader = new Uploader(taskManager, configProvider);
        this.taskManager.submit(new UnarchiveTask(hasUnboundUserId, hasUnboundConfig));
    }

    /**
     * @return the configuration for the Sift instance
     */
    synchronized Sift.Config getConfig() {
        if (config != null) {
            return config;
        } else {
            return unarchiveConfig(archives.getString(ArchiveKey.CONFIG.key, null));
        }
    }

    /**
     * Sets the configuration for the Sift instance
     *
     * @param config
     */
    void setConfig(Sift.Config config) {
        this.taskManager.submit(new SetConfigTask(config));
    }

    /**
     * @return the user ID for the Sift instance
     */
    synchronized String getUserId() {
        return this.userId;
    }

    /**
     * Sets the user ID for the Sift instance
     *
     * @param userId
     */
    void setUserId(String userId) {
        this.taskManager.submit(new SetUserIdTask(userId));
    }

    /**
     * Unsets the user ID for the Sift instance.
     */
    void unsetUserId() {
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
    void save() {
        this.taskManager.submit(new ArchiveTask());
    }

    void stop() {
        this.taskManager.shutdown();
    }

    void appendAppStateEvent(MobileEventJson event) {
        this.taskManager.submit(new AppendTask(
                APP_STATE_QUEUE_IDENTIFIER,
                event
        ));
    }

    void appendDevicePropertiesEvent(MobileEventJson event) {
        this.taskManager.submit(new AppendTask(
                DEVICE_PROPERTIES_QUEUE_IDENTIFIER,
                event
        ));
    }

    void upload(List<MobileEventJson> events) {
        this.uploader.upload(events);
    }

    Queue createQueue(@NonNull String identifier, Queue.Config config) {
        if (getQueue(identifier) != null) {
            throw new IllegalStateException(String.format("Queue exists: %s", identifier));
        }

        Queue queue = new Queue(null, userIdProvider, uploadRequester, config);
        queues.put(identifier, queue);
        Log.i(TAG, String.format("Created new %s queue", identifier));
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
                Log.d(TAG, String.format("Archived Sift.Config: %s", archiveConfig()));
                editor.putString(ArchiveKey.USER_ID.key, getUserId());
                Log.d(TAG, String.format("Archived User ID: %s", getUserId()));
                for (Map.Entry<String, Queue> entry : queues.entrySet()) {
                    String identifier = ArchiveKey.getKeyForQueueIdentifier(entry.getKey());
                    Log.d(TAG, String.format("Archived %s Queue", identifier));
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
        private final boolean hasUnboundConfig;
        private final boolean hasUnboundUserId;

        public UnarchiveTask(boolean hasUnboundConfig, boolean hasUnboundUserId) {
            this.hasUnboundConfig = hasUnboundConfig;
            this.hasUnboundUserId = hasUnboundUserId;
        }

        @Override
        public void run() {
            String archive;

            // Unarchive Config only if we don't have one from open or unbound calls
            if (config == null) {
                // Unarchive Sift config
                archive = archives.getString(ArchiveKey.CONFIG.key, null);
                config = unarchiveConfig(archive);
                Log.d(TAG, String.format("Unarchived Sift.Config: %s", archive));
            }

            // Unarchive User ID if we didn't have an unbound one from the Sift class
            if (!this.hasUnboundUserId) {
                userId = archives.getString(ArchiveKey.USER_ID.key, null);
                Log.d(TAG, String.format("Unarchived User ID: %s", userId));
            }

            // Unarchive Queues
            for (Map.Entry<String, ?> entry : archives.getAll().entrySet()) {
                String identifier = ArchiveKey.getQueueIdentifier(entry.getKey());
                archive = (String) entry.getValue();

                if (identifier != null) {
                    if (identifier.equals(DEVICE_PROPERTIES_QUEUE_IDENTIFIER)) {
                        Queue queue = new Queue(archive, userIdProvider, uploadRequester,
                                DEVICE_PROPERTIES_QUEUE_CONFIG);
                        Log.d(TAG, "Unarchived Device Properties Queue");
                        queues.put(identifier, queue);
                    }

                    if (identifier.equals(APP_STATE_QUEUE_IDENTIFIER)) {
                        Queue queue = new Queue(archive, userIdProvider, uploadRequester,
                                APP_STATE_QUEUE_CONFIG);
                        Log.d(TAG, "Unarchived App State Queue");
                        queues.put(identifier, queue);
                    }
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
            synchronized (this) {
                SiftImpl.this.userId = this.userId;
            }
        }
    }

    private class SetConfigTask implements Runnable {
        private Sift.Config config;

        SetConfigTask(Sift.Config config) {
            this.config = config;
        }

        @Override
        public void run() {
            synchronized (this) {
                SiftImpl.this.config = this.config;
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
}
