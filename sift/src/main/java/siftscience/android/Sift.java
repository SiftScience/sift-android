// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import java.util.StringJoiner;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The public API of the Sift client library.
 */
public final class Sift {

    //================================================================================
    // Static members
    //================================================================================

    private static final String TAG = Sift.class.getName();

    static final String SDK_VERSION = BuildConfig.VERSION_NAME;

    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static volatile SiftImpl instance;
    private static volatile DevicePropertiesCollector devicePropertiesCollector;
    private static volatile AppStateCollector appStateCollector;
    private static volatile String unboundUserId;
    private static volatile boolean hasUnboundUserId = false;
    private static volatile ExecutorService executors;

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
    public static void open(@NonNull Context context,
                            Config config,
                            String activityName) {
        synchronized (Sift.class) {
            if (instance == null) {
                Context c = context.getApplicationContext();
                instance = new SiftImpl(c, config, unboundUserId, hasUnboundUserId);
                devicePropertiesCollector = new DevicePropertiesCollector(instance, c);
                appStateCollector = new AppStateCollector(instance, c);
                unboundUserId = null;
                hasUnboundUserId = false;
            } else {
                if (config != null) {
                    instance.setConfig(config);
                }
            }
        }

        appStateCollector.setActivityName(activityName == null ?
                context.getClass().getSimpleName() : activityName);
    }

    public static void open(@NonNull Context context, String activityName) {
        open(context, null, activityName);
    }

    public static void open(@NonNull Context context, Config config) {
        open(context, config, null);
    }

    public static void open(@NonNull Context context) {
        open(context, null, null);
    }

    /**
     * Call Sift.collect() after the Sift.open() call in each Activity.
     *
     * Collect SDK events for Device Properties and Application State.
     */
    public static void collect() {
        if (executors == null || (executors != null && executors.isShutdown())) {
            executors = Executors.newSingleThreadScheduledExecutor();
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                appStateCollector.collect();
                devicePropertiesCollector.collect();
            }
        };
        executors.submit(runnable);
    }

    /**
     * Request an immediate upload for the collected events in the queue disregard the queue config.
     *
     * Sift.collect() will collect events for Device Properties and Application State
     * in to their specified queue. When collecting events, Sift will comply with their
     * queue config (so the some events might not be uploaded).
     *
     * If queue is nil, this method will do nothing.
     */
    public static void upload() {
        if (instance != null) {
            instance.forceUploadAppStateEvent();
            instance.forceUploadDevicePropertiesEvent();
        }
    }

    /**
     * Call Sift.pause() in the onPause() callback of each Activity.
     *
     * Persists instance state to disk and disconnects location services.
     */
    public static void pause() {
        SiftImpl localInstance = instance;
        if (localInstance != null) {
            localInstance.save();
        }

        AppStateCollector localAppStateCollector = appStateCollector;
        if (localAppStateCollector != null) {
            localAppStateCollector.disconnectLocationServices();
        }
    }

    public static void resume(@NonNull Context context) {
        resume(context, null);
    }

    public static void resume(@NonNull Context context, String activityName) {
        AppStateCollector localAppStateCollector = appStateCollector;
        if (localAppStateCollector != null) {
            localAppStateCollector.reconnectLocationServices();
            localAppStateCollector.setActivityName(activityName == null ?
                    context.getClass().getSimpleName() : activityName);
        }
    }

    /**
     * Call Sift.close() in the onDestroy() callback of each Activity.
     *
     * Terminate executor used for collecting Device Properties and Application State.
     */
    public static void close() {
        if (executors != null && !executors.isShutdown()){
            executors.shutdown();
            try {
                if (!executors.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.d(TAG, "Some tasks are not terminated yet before timeout");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted when awaiting executor termination", e);
            }
        }
    }

    public static synchronized void setUserId(String userId) {
        if (instance != null) {
            instance.setUserId(userId);
        } else {
            unboundUserId = userId;
            hasUnboundUserId = true;
        }
    }

    public static synchronized void unsetUserId() {
        setUserId(null);
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

        @RequiresApi(api = Build.VERSION_CODES.N)
        boolean isValid() {

            StringJoiner configurationErrors = new StringJoiner(", ");

            if (accountId == null || accountId.isEmpty()) {
                configurationErrors.add("accountId");
            }

            if (beaconKey == null || beaconKey.isEmpty()) {
                configurationErrors.add("beacon key");
            }

            if (serverUrlFormat == null || serverUrlFormat.isEmpty()) {
                configurationErrors.add("server URL format");
            }

            boolean valid = configurationErrors.length() == 0;

            if (!valid) {
                Log.d(TAG, "The following configuration properties are missing or empty: " +
                        configurationErrors.toString());
            }

            return valid;
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

    private Sift() {

    }
}
