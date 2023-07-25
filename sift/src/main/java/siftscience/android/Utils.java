// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.MobileEventJson;

import java.util.List;

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
        Long firstTime = first.time;
        first.time = second.time;
        boolean result = first.equals(second);
        first.time = firstTime;
        return result;
    }

    public static boolean equals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }

    public static boolean equals(List<AccountKey> a, List<AccountKey> b) {
        if (a == null || b == null) {
            return a == b;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
