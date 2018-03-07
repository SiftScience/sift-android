// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

/**
 * Simple wrapper for mocking System.currentTimeMillis() in unit tests.
 */
class Time {

    /** Setting this to non-zero would mock the time. */
    static long currentTime = 0;

    /** Return the current time since the Epoch in milliseconds. */
    static long now() {
        return currentTime != 0 ? currentTime : System.currentTimeMillis();
    }
}
