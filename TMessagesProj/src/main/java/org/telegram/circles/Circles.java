package org.telegram.circles;

import androidx.annotation.NonNull;

import org.telegram.circles.utils.Logger;
import org.telegram.circles.utils.Utils;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class Circles implements NotificationCenter.NotificationCenterDelegate {
    private static final Map<Integer, Circles> instances = new ConcurrentHashMap<>();
    private static final Object instanceLockObj = new Object();

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final AccountInstance accountInstance;
    private final Preferences preferences;
    private final int accountNum;
    private final Set<MessageObject> botMessages = new HashSet<>();
    private final AtomicInteger newMessagesAwaiting = new AtomicInteger(0);
    private final int classGuid = ConnectionsManager.generateClassGuid();
    private int lastLoadIndex = 0;

    private Circles (int accountNum) {
        this.accountNum = accountNum;
        accountInstance = AccountInstance.getInstance(accountNum);
        preferences = new Preferences(accountNum);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.appDidLogout);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int account, final Object... args) {
        Logger.d("Received notification: "+id+" with "+args.length+" args, account: "+account+"("+accountNum+")");
        if (account == accountNum) {
            if (id == NotificationCenter.appDidLogout) {
                logout();
            } else if (preferences.getBotPeerId() != null) {
                if (id == NotificationCenter.messagesDeleted) {
                    removeBotMessages((ArrayList<Integer>) args[0]);
                } else if (id == NotificationCenter.didReceiveNewMessages || id == NotificationCenter.messagesDidLoad) {
                    ArrayList<MessageObject> messages = null;
                    if (id == NotificationCenter.didReceiveNewMessages) {
                        messages = (ArrayList<MessageObject>) args[1];
                    }
                    if (id == NotificationCenter.messagesDidLoad) {
                        messages = (ArrayList<MessageObject>) args[2];
                    }
                    if (messages != null && !messages.isEmpty()) {
                        handleNewBotMessages(messages);
                    }
                }
                cleanupBotMessages();
            }
        }
    }

    private Single<TLObject> sendRequest(TLObject req) {
        return Single.create((SingleEmitter<TLObject> emitter) ->
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
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

    private Single<Object> lookupBotPeerId() {
        return Single.create((emitter) -> {
            TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
            req.q = CirclesConstants.BOT_HANDLE;
            req.limit = 10;
            compositeDisposable.add(sendRequest(req)
                .subscribe(response -> {
                    TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                    if (res.users != null) {
                        String botUsername = CirclesConstants.BOT_HANDLE.replaceFirst("@", "");
                        for (TLRPC.User user : res.users) {
                            if (user.bot && botUsername.equals(user.username)) {
                                preferences.setBotPeerId((long) user.id);
                                Logger.d("Bot peer id found "+ user.id);
                                emitter.onSuccess(new Object());
                                return;
                            }
                        }
                    }
                    emitter.onError(new RequestError(RequestError.ErrorCode.BOT_SEED_LOOKUP_FAILED));
                }, emitter::onError));
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private Single<Object> requestToken() {
        if (preferences.getBotPeerId() == null) {
            throw new IllegalStateException("Circles bot peer id not resolved yet");
        }
        //init messages controller
        accountInstance.getMessagesController().loadGlobalNotificationsSettings();
        accountInstance.getMessagesController().loadDialogs(0, 0, 100, true);
        accountInstance.getMessagesController().loadHintDialogs();
        accountInstance.getMessagesController().loadUserInfo(UserConfig.getInstance(accountNum).getCurrentUser(), false, classGuid);

        //load bot messages
        accountInstance.getMessagesController().loadMessages(preferences.getBotPeerId(), Integer.MAX_VALUE, 0, 0, false, 0, classGuid, 2, 0, false, false, lastLoadIndex++);

        //send start request
        accountInstance.getSendMessagesHelper()
            .sendMessage("/start api", preferences.getBotPeerId(), null, null, false, null, null, null, true, 0);

        //wait for incoming message
        return Single.create((emitter) -> {
            String latestToken = "";

            synchronized (botMessages) {
                newMessagesAwaiting.incrementAndGet();
                botMessages.wait(CirclesConstants.BOT_MESSAGE_WAIT_TIMEOUT);

                Integer latestTokenMessageId = null;
                for (MessageObject message : botMessages) {
                    if (message.deleted || message.messageText == null) continue;
                    if (message.messageOwner.from_id == preferences.getBotPeerId()) {
                        String token = message.messageText.toString();
                        if (Utils.messageLooksLikeToken(token)) {
                            if (latestTokenMessageId == null || latestTokenMessageId < message.getId()) {
                                latestTokenMessageId = message.getId();
                                latestToken = token;
                            }
                        }
                    }
                }
                newMessagesAwaiting.decrementAndGet();
            }

            if (!latestToken.isEmpty()) {
                Logger.d("Received new token: "+latestToken);
                preferences.setAuthToken(latestToken);
                emitter.onSuccess(new Object());
            } else {
                emitter.onError(new RequestError(RequestError.ErrorCode.DIDNT_RECEIVE_TOKEN));
            }

            cleanupBotMessages();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private void handleNewBotMessages(@NonNull ArrayList<MessageObject> messages) {
        synchronized (botMessages) {
            boolean hasNewMessages = false;
            for (MessageObject message : messages) {
                if (message.getDialogId() == preferences.getBotPeerId() && !message.deleted) {
                    Logger.d("Message in bot chat: " + message.messageText);
                    if (!botMessages.contains(message)) {
                        boolean duplicatedMessage = false;
                        for (Iterator<MessageObject> i = botMessages.iterator(); i.hasNext();) {
                            if (i.next().getId() == message.getId()) {
                                i.remove();
                                duplicatedMessage = true;
                            }
                        }
                        if (!duplicatedMessage) {
                            hasNewMessages = true;
                        }
                        botMessages.add(message);
                    }
                }
            }
            if (hasNewMessages) {
                botMessages.notifyAll();
            }
        }
    }

    private void removeBotMessages(ArrayList<Integer> deletedIds) {
        if (deletedIds != null && !deletedIds.isEmpty()) {
            synchronized (botMessages) {
                for (Iterator<MessageObject> i = botMessages.iterator(); i.hasNext(); ) {
                    if (deletedIds.contains(i.next().getId())) {
                        i.remove();
                    }
                }
            }
        }
    }

    private void cleanupBotMessages() {
        if (newMessagesAwaiting.get() != 0) return;

        ArrayList<Integer> messagesToDelete = new ArrayList<>();

        synchronized (botMessages) {
            if (newMessagesAwaiting.get() != 0) return;

            for (MessageObject message : botMessages) {
                if (message.deleted || message.messageText == null) continue;
                if (message.messageOwner.from_id == preferences.getBotPeerId()) {
                    if (Utils.isSystemBotMessage(message.messageText.toString())) {
                        Logger.d("Delete bot message " + message.getId() + " :: " + message.messageText);
                        messagesToDelete.add(message.getId());
                    }
                } else {
                    if (Utils.isSystemUserMessage(message.messageText.toString())) {
                        Logger.d("Delete user message " + message.getId() + " :: " + message.messageText);
                        messagesToDelete.add(message.getId());
                    }
                }
            }
        }

        if (!messagesToDelete.isEmpty()) {
            accountInstance.getMessagesController().deleteMessages(messagesToDelete, null, null, 0, 0, true, false);
        }
    }

    private void logout() {
        preferences.clear();
        synchronized (botMessages) {
            botMessages.clear();
        }
        Logger.d("Did log out");
    }


    // PUBLIC API

    public static Circles getInstance(int accountNum) {
        Circles instance = instances.get(accountNum);
        if (instance == null) {
            synchronized (instanceLockObj) {
                instance = instances.get(accountNum);
                if (instance == null) {
                    instance = new Circles(accountNum);
                    instances.put(accountNum, instance);
                }
            }
        }

        return instance;
    }

    public void onAuthSuccess(@NonNull SuccessListener listener) {
        Logger.d("Telefrost auth requested");
        if (preferences.getAuthToken() != null) {
            listener.onSuccess();
        } else {
            if (preferences.getBotPeerId() == null) {
                compositeDisposable.add(
                        lookupBotPeerId()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(success -> compositeDisposable.add(
                                        requestToken()
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(success1 -> listener.onSuccess(), listener::onError)
                                ), listener::onError));
            } else {
                compositeDisposable.add(
                        requestToken()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(success1 -> listener.onSuccess(), listener::onError)
                );
            }
        }
    }
}
