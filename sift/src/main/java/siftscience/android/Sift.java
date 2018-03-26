// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/** The public API of the Sift client library. */
public class Sift {

    //================================================================================
    // Static members
    //================================================================================

    private static final String TAG = Sift.class.getName();

    static final String SDK_VERSION = BuildConfig.VERSION_NAME;

    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static SiftImpl instance;
    private static int openCount;
    private static DevicePropertiesCollector devicePropertiesCollector;
    private static AppStateCollector appStateCollector;



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
                instance = new SiftImpl(c, config);
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
        if (instance != null) {
            instance.save();
        }
        appStateCollector.disconnectLocationServices();
    }

    public static void resume(@NonNull Context context) {
        appStateCollector.reconnectLocationServices();
        open(context);
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

    public static void setUserId(String userId) {
        instance.setUserId(userId);
    }

    public static void unsetUserId() {
        instance.setUserId(null);
    }

    /**
     * @return the Sift singleton instance
     */
    @Deprecated
    @Nullable
    static synchronized SiftImpl get() {
        return instance;
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
}
