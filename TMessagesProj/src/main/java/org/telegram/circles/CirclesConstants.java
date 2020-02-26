package org.telegram.circles;

import java.util.regex.Pattern;

public final class CirclesConstants {
    public static final int CONNECT_TIMEOUT = 10;
    public static final int READ_TIMEOUT = 20;
    public static final int WRITE_TIMEOUT = 60;

    public static final long BOT_MESSAGE_WAIT_TIMEOUT = 30000;

    //public static final String BASE_URL = BuildConfig.DEBUG ? "https://api.dev.randomcoffee.us" : "https://api.circles.is";
    public static final String BASE_URL = "https://api.circles.is";
    //private static final String BOT_HANDLE = BuildConfig.DEBUG ? "@circlesdevbot" : "@circlesadminbot";
    public static final String BOT_HANDLE = "@circlesadminbot";

    public static final Pattern authTokenPattern = Pattern.compile("[a-zA-Z0-9. _\\-=]{100,}");

    public static final String PREFERENCES_NAME = "telefrost_settings";


    private CirclesConstants() {}
}
