package org.telegram.circles.utils;

import androidx.annotation.NonNull;

import org.telegram.circles.CirclesConstants;
import org.telegram.circles.data.CircleData;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Utils {
    public static boolean messageLooksLikeToken(String text) {
        return CirclesConstants.authTokenPattern.matcher(text).matches();
    }

    public static boolean isSystemBotMessage(String text) {
        return messageLooksLikeToken(text);
    }

    public static boolean isSystemUserMessage(String text) {
        return text.startsWith("/start") || text.startsWith("/create");
    }

    public static TLRPC.User getDialogUser(long dialogId, AccountInstance accountInstance) {
        TLRPC.User user = null;
        if (dialogId != 0) {
            int lower_id = (int) dialogId;
            int high_id = (int) (dialogId >> 32);
            if (lower_id != 0) {
                if (lower_id >= 0) {
                    user = accountInstance.getMessagesController().getUser(lower_id);
                }
            } else {
                TLRPC.EncryptedChat encryptedChat = accountInstance.getMessagesController().getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    user = accountInstance.getMessagesController().getUser(encryptedChat.user_id);
                }
            }
        }
        return user;
    }

    public static TLRPC.Chat getDialogChat(long dialogId, AccountInstance accountInstance) {
        int lower_id = (int) dialogId;
        if (lower_id < 0) {
            return accountInstance.getMessagesController().getChat(-lower_id);
        }
        return null;
    }

    public static boolean isSavedMessages(long dialogId, AccountInstance accountInstance) {
        return UserObject.isUserSelf(getDialogUser(dialogId, accountInstance));
    }

    @SuppressWarnings("UseSparseArrays")
    public static Map<Long, Set<TLRPC.Dialog>> mapDialogsToCircles(@NonNull List<CircleData> circlesList, AccountInstance accountInstance) {
        ArrayList<TLRPC.Dialog> dialogs = accountInstance.getMessagesController().getAllDialogs();
        Map<Long, Set<TLRPC.Dialog>> map = new HashMap<>();
        synchronized (circlesList) {
            for (TLRPC.Dialog dialog : dialogs) {
                if (dialog instanceof TLRPC.TL_dialogFolder && ((TLRPC.TL_dialogFolder) dialog).folder.id == 1) {
                    continue; //Archive folder, do not map
                }
                long circleId = CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL;
                if (dialog.folder_id == 1) { //archived
                    circleId = CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED;
                } else {
                    if (isSavedMessages(dialog.id, accountInstance)) {
                        putDialogToMap(map, circleId, dialog);
                    }
                    long dialogPeerId = dialogIdToPeerId(dialog.id, accountInstance);
                    for (CircleData circle : circlesList) {
                        if (circle.getAllPeerIds().contains(dialogPeerId)) {
                            circleId = circle.id;
                            putDialogToMap(map, circleId, dialog);
                        }
                    }
                }
                putDialogToMap(map, circleId, dialog);
            }
        }
        return map;
    }

    private static long dialogIdToPeerId(long dialogId, AccountInstance accountInstance) {
        if (ChatObject.isChannel(getDialogChat(dialogId, accountInstance))) {
            return -1000000000000L + dialogId;
        }
        return dialogId;
    }

    private static void putDialogToMap(Map<Long, Set<TLRPC.Dialog>> map, long circleId, TLRPC.Dialog dialog) {
        Set<TLRPC.Dialog> dialogSet = map.get(circleId);
        if (dialogSet == null) {
            dialogSet = new HashSet<>();
            map.put(circleId, dialogSet);
        }
        dialogSet.add(dialog);
    }

    private Utils() {}
}
