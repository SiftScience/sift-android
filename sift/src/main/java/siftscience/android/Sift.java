// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.common.base.Preconditions;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Sift {

    /**
     * Configurations of the Sift object.  Use the builder class to
     * create configurations.
     */
    public static class Config {

        private static final String DEFAULT_SERVER_URL_FORMAT =
                "https://api3.siftscience.com/v3/accounts/%s/mobile_events";

        /** Your account ID; default to null. */
        public final String accountId;

        /** Your beacon key; default to null. */
        public final String beaconKey;

        /**
         * The location of the API endpoint.  You may overwrite this
         * for testing.
         */
        public final String serverUrlFormat;

        // The default no-args constructor for Gson.
        Config() {
            this(null, null, null);
        }

        private Config(String accountId,
                       String beaconKey,
                       String serverUrlFormat) {
            this.accountId = accountId;
            this.beaconKey = beaconKey;
            this.serverUrlFormat = serverUrlFormat;
        }

        public static class Builder {
            private String accountId;
            public Builder withAccountId(String accountId) {
                this.accountId = Preconditions.checkNotNull(accountId);
                return this;
            }

            private String beaconKey;
            public Builder withBeaconKey(String beaconKey) {
                this.beaconKey = Preconditions.checkNotNull(beaconKey);
                return this;
            }

            private String serverUrlFormat = DEFAULT_SERVER_URL_FORMAT;
            public Builder withServerUrlFormat(String serverUrlFormat) {
                this.serverUrlFormat = Preconditions.checkNotNull(serverUrlFormat);
                return this;
            }

            public Config build() {
                return new Config(
                        Preconditions.checkNotNull(accountId),
                        Preconditions.checkNotNull(beaconKey),
                        Preconditions.checkNotNull(serverUrlFormat)
                );
            }
        }
    }

    /**
     * The Gson object shared within this package, which is configured
     * to generate JSON messages complied with our API doc.
     */
    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
