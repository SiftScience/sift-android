// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Sift {

    /**
     * The Gson object shared within this package, which is configured
     * to generate JSON messages complied with our API doc.
     */
    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
