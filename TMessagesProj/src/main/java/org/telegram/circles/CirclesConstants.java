package org.telegram.circles;

import org.telegram.messenger.BuildConfig;

import java.util.regex.Pattern;

public final class CirclesConstants {
    public static final int CONNECT_TIMEOUT = 10;
    public static final int READ_TIMEOUT = 20;
    public static final int WRITE_TIMEOUT = 60;

    static final long BOT_MESSAGE_WAIT_TIMEOUT = 30000L;
    static final long SEND_MEMBERS_INTERVAL = 60L * 60L * 1000L;

    static final long CIRCLES_CACHE_UPDATE_INTERVAL = 2L * 60L * 1000L;

    public static final String BASE_URL = BuildConfig.DEBUG ? "https://api.peerboard.dev/" : "https://api.peerboard.org/";
    //public static final String BASE_URL = "https://api.peerboard.org/tgfork/";
    static final String BOT_HANDLE = BuildConfig.DEBUG ? "@TelefrostDevBot" : "@TelefrostConciergeBot";
    //public static final String BOT_HANDLE = "@TelefrostConciergeBot";
    static final String NEWS_CHANNEL_HANDLE = "@telefrostnews";

    public static final Pattern authTokenPattern = Pattern.compile("[a-zA-Z0-9. _\\-=]{100,}");

    static final String PREFERENCES_NAME = "telefrost_settings";

    public static final long DEFAULT_CIRCLE_ID_PERSONAL = 0;
    public static final long DEFAULT_CIRCLE_ID_ARCHIVED = 1;

    public static final int LOAD_MAX_USERS_AT_A_TIME = 200;

    private CirclesConstants() {}
}
