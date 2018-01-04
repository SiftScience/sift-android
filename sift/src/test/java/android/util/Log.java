// Copyright (c) 2017 Sift Science. All rights reserved.

package android.util;

/**
 * Mock real Android's Log class for testing (unfortunately android.jar
 * does not mock these).
 */
public class Log {

    private Log() {
        throw new AssertionError();
    }

    public static int d(String tag, String msg) {
        return log("DEBUG", tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return log("DEBUG", tag, msg, tr);
    }

    public static int e(String tag, String msg) {
        return log("ERROR", tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return log("ERROR", tag, msg, tr);
    }


    public static int w(String tag, String msg) {
        return log("WARN", tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return log("WARN", tag, msg, tr);
    }

    public static int i(String tag, String msg) {
        return log("INFO", tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return log("INFO", tag, msg, tr);
    }

    private static int log(String level, String tag, String msg) {
        System.err.println(String.format("%5s: %s: %s", level, tag, msg));
        return 0;
    }

    private static int log(String level, String tag, String msg, Throwable tr) {
        System.err.println(String.format("%5s: %s: %s", level, tag, msg));
        if (tr != null) {
            tr.printStackTrace(System.err);
        }
        return 0;
    }
}
