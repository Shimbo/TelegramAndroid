package org.telegram.circles.utils;

import org.telegram.circles.CirclesConstants;

public final class Utils {
    public static boolean messageLooksLikeToken(String text) {
        return CirclesConstants.authTokenPattern.matcher(text).matches();
    }

    public static boolean isSystemBotMessage(String text) {
        return messageLooksLikeToken(text);
    }

    public static boolean isSystemUserMessage(String text) {
        return text.startsWith("/start");
    }


    private Utils() {}
}
