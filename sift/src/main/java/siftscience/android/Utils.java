package siftscience.android;

import com.sift.api.representations.MobileEventJson;

/**
 * Created by gary on 2/1/17.
 */

public class Utils {
    public static boolean eventsAreBasicallyEqual(MobileEventJson first,
                                         MobileEventJson second) {
        long currentTime = Time.now();
        return MobileEventJson.newBuilder(first).withTime(currentTime).build()
                .equals(MobileEventJson.newBuilder(second).withTime(currentTime).build());
    }
}
