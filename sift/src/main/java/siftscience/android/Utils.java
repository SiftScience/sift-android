// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.MobileEventJson;

/**
 * Util methods.
 */

public class Utils {
    public static boolean eventsAreBasicallyEqual(MobileEventJson first,
                                         MobileEventJson second) {
        long currentTime = Time.now();
        return MobileEventJson.newBuilder(first).withTime(currentTime).build()
                .equals(MobileEventJson.newBuilder(second).withTime(currentTime).build());
    }

    public static boolean equals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }
}
