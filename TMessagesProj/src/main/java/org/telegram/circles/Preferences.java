package org.telegram.circles;

import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.telegram.circles.data.CircleData;
import org.telegram.circles.utils.Logger;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

class Preferences {
    private final static String BOT_PEER_ID_KEY = "bot_peer_id";
    private final static String AUTH_TOKEN_KEY = "auth_token_key";
    private final static String SELECTED_CIRCLE_KEY = "selected_circle_key";
    private final static String CAHCED_CIRCLES_KEY = "cached_circles_key";

    private Long botPeerId = null;
    private String authToken = null;
    private long selectedCircleId = CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL;

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

    long getSelectedCircleId() {
        return selectedCircleId;
    }

    void setSelectedCircleId(Long selectedCircleId) {
        this.selectedCircleId = selectedCircleId != null ? selectedCircleId : CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL;
        prefs.edit().putLong(SELECTED_CIRCLE_KEY, this.selectedCircleId).apply();
    }

    List<CircleData> getCachedCircles() {
        String data = prefs.getString(CAHCED_CIRCLES_KEY, null);
        List<CircleData> circles = null;
        if (data != null && !data.isEmpty()) {
            try {
                circles = (new Gson()).fromJson(data, List.class);
            } catch (Exception e) {
                Logger.e(e);
            }
        }
        if (circles != null) {
            Logger.d("Loaded cached circles: "+circles.size());
            return circles;
        } else {
            return new ArrayList<>();
        }
    }

    void setCachedCircles(List<CircleData> cachedCircles) {
        String data = (new Gson()).toJson(cachedCircles);
        prefs.edit().putString(CAHCED_CIRCLES_KEY, data).apply();
    }

    private void reload() {
        botPeerId = prefs.contains(BOT_PEER_ID_KEY)? prefs.getLong(BOT_PEER_ID_KEY, 0) : null;
        authToken = prefs.getString(AUTH_TOKEN_KEY, null);
        selectedCircleId = prefs.getLong(SELECTED_CIRCLE_KEY, CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL);
    }

    void clear() {
        botPeerId = null;
        authToken = null;
        selectedCircleId = CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL;
        prefs.edit().clear().apply();
    }
}
