package org.telegram.circles;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import static android.content.Context.MODE_PRIVATE;

class Preferences {
    private final static String BOT_PEER_ID_KEY = "bot_peer_id";
    private final static String AUTH_TOKEN_KEY = "auth_token_key";

    private Long botPeerId = null;
    private String authToken = null;

    private final SharedPreferences prefs;

    Preferences(int accountNum) {
        ApplicationLoader app = (ApplicationLoader) ApplicationLoader.applicationContext;
        prefs = app.getSharedPreferences(CirclesConstants.PREFERENCES_NAME+"_"+accountNum, MODE_PRIVATE);
        reload();
    }

    Long getBotPeerId() {
        return botPeerId;
    }

    void setBotPeerId(Long botPeerId) {
        this.botPeerId = botPeerId;
        if (botPeerId != null) {
            prefs.edit().putLong(BOT_PEER_ID_KEY, botPeerId).apply();
        } else {
            prefs.edit().remove(BOT_PEER_ID_KEY).apply();
        }

    }

    String getAuthToken() {
        return authToken;
    }

    void setAuthToken(String authToken) {
        this.authToken = authToken;
        if (authToken != null) {
            prefs.edit().putString(AUTH_TOKEN_KEY, authToken).apply();
        } else {
            prefs.edit().remove(AUTH_TOKEN_KEY).apply();
        }
    }

    private void reload() {
        botPeerId = prefs.contains(BOT_PEER_ID_KEY)? prefs.getLong(BOT_PEER_ID_KEY, 0) : null;
        authToken = prefs.getString(AUTH_TOKEN_KEY, null);
    }

    void clear() {
        botPeerId = null;
        authToken = null;
        prefs.edit().clear().apply();
    }
}
