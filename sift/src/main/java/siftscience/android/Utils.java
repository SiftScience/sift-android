// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.MobileEventJson;

/**
 * Util methods
 */

public class Utils {
    public static boolean eventsAreBasicallyEqual(MobileEventJson first,
                                         MobileEventJson second) {
        long currentTime = Time.now();
        return MobileEventJson.newBuilder(first).withTime(currentTime).build()
                .equals(MobileEventJson.newBuilder(second).withTime(currentTime).build());
    }
}
