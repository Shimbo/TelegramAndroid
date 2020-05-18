package org.telegram.circles.utils;

import android.util.Log;

import org.telegram.messenger.BuildConfig;

public final class Logger {
    private Logger() {}

    private static final String TAG = "CIRCLES";

    public static void d(Object message) {
        if (BuildConfig.DEBUG && message != null) {
            Log.d(TAG, message.toString());
        }
    }

    public static void e(Object message) {
        if (BuildConfig.DEBUG && message != null) {
            Log.e(TAG, message.toString());
        }
    }

    public static void e(Throwable e) {
        if (BuildConfig.DEBUG & e != null) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void e(Object message, Throwable e) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, message != null ? message.toString() : "", e);
        }
    }
}
