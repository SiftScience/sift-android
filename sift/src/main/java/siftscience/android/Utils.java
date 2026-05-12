// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.MobileEventJson;

/**
 * Util methods.
 */

public class Utils {
    public static boolean eventsAreBasicallyEqual(MobileEventJson first,
                                                  MobileEventJson second) {
        if (first == null ^ second == null) {
            return false;
        } else if (first == second) {
            return true;
        }

        // KLUDGE: jsonschema2pojo doesn't generate copy constructors, so back up one time,
        // override it, then restore it.
        Long firstTime = first.getTime();
        first.setTime(second.getTime());
        boolean result = first.equals(second);
        first.setTime(firstTime);
        return result;
    }

    public static boolean equals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }
}
