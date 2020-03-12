package org.telegram.circles.utils;

import androidx.annotation.NonNull;

import org.telegram.circles.CirclesConstants;
import org.telegram.circles.RequestError;
import org.telegram.circles.data.CircleData;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public final class Utils {
    public static boolean messageLooksLikeToken(String text) {
        return CirclesConstants.authTokenPattern.matcher(text).matches();
    }

    public static boolean isSystemBotMessage(String text) {
        return messageLooksLikeToken(text);
    }

    public static boolean isSystemUserMessage(String text) {
        return false; //text.startsWith("/start") || text.startsWith("/create");
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

    public static Single<TLObject> sendRequest(TLObject req, AccountInstance accountInstance) {
        return Single.create((SingleEmitter<TLObject> emitter) ->
                accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                            if (error == null) {
                                if (response != null) {
                                    emitter.onSuccess(response);
                                } else {
                                    emitter.onError(new RequestError(RequestError.ErrorCode.EMPTY_RESPONSE));
                                }
                            } else {
                                emitter.onError(new RequestError(error));
                            }
                        }
                )
        )
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io());
    }

    public static Single<Set<TLRPC.User>> loadAllChatUsers(@NonNull TLRPC.Chat chat, AccountInstance accountInstance, CompositeDisposable compositeDisposable) {
        return Single.create((SingleEmitter<Set<TLRPC.User>> emitter) -> {
            if (ChatObject.isMegagroup(chat)) {
                loadAllChannelUsers(chat, accountInstance, compositeDisposable, 0, new HashSet<>(), emitter);
            } else {
                TLRPC.ChatFull fullChat = accountInstance.getMessagesController().getChatFull(chat.id);
                if (chat instanceof TLRPC.TL_chat && (fullChat == null || fullChat.participants == null || fullChat.participants.participants == null || fullChat.participants.participants.isEmpty())) {
                    TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
                    req.chat_id = chat.id;
                    compositeDisposable.add(sendRequest(req, accountInstance)
                            .subscribe(response -> {
                                Set<TLRPC.User> users = new HashSet<>();
                                if (response instanceof TLRPC.TL_messages_chatFull && ((TLRPC.TL_messages_chatFull) response).users != null) {
                                    users.addAll(((TLRPC.TL_messages_chatFull) response).users);
                                }
                                emitter.onSuccess(users);
                            }, error -> {
                                Logger.e("Error loading full chat for "+chat.title, error);
                                emitter.onError(error);
                            }));
                } else {
                    Set<TLRPC.User> users = new HashSet<>();
                    if (fullChat != null && fullChat.participants != null && fullChat.participants.participants != null && !fullChat.participants.participants.isEmpty()) {
                        for (TLRPC.ChatParticipant part : fullChat.participants.participants) {
                            TLRPC.User user = accountInstance.getMessagesController().getUser(part.user_id);
                            if (user != null) {
                                users.add(user);
                            }
                        }
                    }
                    emitter.onSuccess(users);
                }
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private static void loadAllChannelUsers(@NonNull final TLRPC.Chat chat, final AccountInstance accountInstance, final CompositeDisposable compositeDisposable, final int offset, Set<TLRPC.User> users, SingleEmitter<Set<TLRPC.User>> emitter) {
        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = accountInstance.getMessagesController().getInputChannel(chat.id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = offset;
        req.limit = CirclesConstants.LOAD_MAX_USERS_AT_A_TIME;
        compositeDisposable.add(sendRequest(req, accountInstance)
            .subscribe(response -> {
                if (response instanceof TLRPC.TL_channels_channelParticipants) {
                    Collection<TLRPC.User> participants = ((TLRPC.TL_channels_channelParticipants) response).users;
                    if (participants != null) {
                        users.addAll(participants);
                        if (participants.size() >= CirclesConstants.LOAD_MAX_USERS_AT_A_TIME) {
                            loadAllChannelUsers(chat, accountInstance, compositeDisposable, offset+participants.size(), users, emitter);
                            return;
                        }
                    }
                }
                emitter.onSuccess(users);
            }, error -> {
                Logger.e("Error loading chat users "+chat.title+" at offset "+offset, error);
                emitter.onError(error);
            }));
    }


    private Utils() {}
}
