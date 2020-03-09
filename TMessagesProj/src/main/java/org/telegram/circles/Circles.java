package org.telegram.circles;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.telegram.circles.data.ChangeConnection;
import org.telegram.circles.data.ChatData;
import org.telegram.circles.data.CircleData;
import org.telegram.circles.data.CirclesList;
import org.telegram.circles.data.RetrofitHelper;
import org.telegram.circles.data.UserData;
import org.telegram.circles.ui.WorkspaceSelector;
import org.telegram.circles.utils.Logger;
import org.telegram.circles.utils.Utils;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

    private final List<CircleData> cachedCircles = new ArrayList<>();
    private volatile long lastCacheUpdateTime = 0;
    private volatile boolean circlesRequestInProgress = false;

    private Circles (int accountNum) {
        this.accountNum = accountNum;
        accountInstance = AccountInstance.getInstance(accountNum);
        preferences = new Preferences(accountNum);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.appDidLogout);
        synchronized (cachedCircles) {
            cachedCircles.clear();
            cachedCircles.addAll(preferences.getCachedCircles());
        }
        setSelectedCircle(null);
        Logger.d("Initialized for account "+accountNum);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int account, final Object... args) {
        Logger.d("Received notification: "+id+" with "+args.length+" args, account: "+account+"("+accountNum+")");
        if (account == accountNum) {
            if (NotificationCenter.dialogsNeedReload == id) {
                updateDialogCircles();
            } else if (NotificationCenter.didReceiveNewMessages == id || NotificationCenter.messagesDidLoad == id) {
                ArrayList<MessageObject> messages = null;
                if (id == NotificationCenter.didReceiveNewMessages) {
                    updateDialogCircles();
                    messages = (ArrayList<MessageObject>) args[1];
                }
                if (id == NotificationCenter.messagesDidLoad) {
                    messages = (ArrayList<MessageObject>) args[2];
                }
                handleNewBotMessages(messages);
            } else if (NotificationCenter.messagesDeleted == id) {
                removeBotMessages((ArrayList<Integer>) args[0]);
            } else if (NotificationCenter.appDidLogout == id) {
                logout();
                return;
            }
            cleanupBotMessages();
        }
    }

    private void cacheCircles(CirclesList circlesList, Map<Long, Set<TLRPC.Dialog>> dialogsMap) {
        synchronized (cachedCircles) {
            cachedCircles.clear();

            CircleData personal = new CircleData();
            personal.id = CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL;
            personal.circleType = CircleType.PERSONAL;
            personal.counter = countUnread(dialogsMap.get(CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL));
            cachedCircles.add(personal);

            if (circlesList != null && circlesList.circles != null) {
                for (CircleData circle : circlesList.circles) {
                    circle.counter = countUnread(dialogsMap.get(circle.id));
                    cachedCircles.add(circle);
                }
            }

            Set<TLRPC.Dialog> archivedDialogs = dialogsMap.get(CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED);
            if (archivedDialogs != null && archivedDialogs.size() > 0) {
                CircleData archive = new CircleData();
                archive.id = CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED;
                archive.circleType = CircleType.ARCHIVE;
                archive.counter = countUnread(archivedDialogs);
                cachedCircles.add(archive);
            }

            lastCacheUpdateTime = SystemClock.uptimeMillis();
            preferences.setCachedCircles(cachedCircles);
        }
    }

    private int countUnread(Set<TLRPC.Dialog> dialogs) {
        int count = 0;
        if (dialogs != null || dialogs.size() > 0) {
            for (TLRPC.Dialog dialog : dialogs) {
                count += dialog.unread_count;
                if (dialog instanceof TLRPC.TL_dialogFolder && ((TLRPC.TL_dialogFolder) dialog).folder.id != 0) {
                    count += dialog.unread_mentions_count;
                }
            }
        }
        return count;
    }

    private void selectCurrentCircle(Map<Long, Set<TLRPC.Dialog>> dialogsMap) {
        long currentCircleId = preferences.getSelectedCircleId();

        if (currentCircleId == CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED) return;

        Set<TLRPC.Dialog> currentCircleDialogs = dialogsMap.get(currentCircleId);
        boolean modifiedDialogs = false;
        for (long circleId : dialogsMap.keySet()) {
            Set<TLRPC.Dialog> dialogs = dialogsMap.get(circleId);
            if (dialogs != null) {
                for (TLRPC.Dialog dialog : dialogs) {
                    if (circleId != currentCircleId && circleId != CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED && (currentCircleDialogs == null || !currentCircleDialogs.contains(dialog))) {
                        if (dialog.folder_id != 3) {
                            dialog.folder_id = 3;
                            modifiedDialogs = true;
                        }
                    } else if (circleId == currentCircleId) {
                        if (dialog.folder_id != 0) {
                            dialog.folder_id = 0;
                            modifiedDialogs = true;
                        }
                    }
                }
            }
        }
        if (modifiedDialogs) {
            accountInstance.getMessagesController().sortDialogs(null);
            accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
    }

    private void updateDialogCircles() {
        if (preferences.getAuthToken() == null || circlesRequestInProgress) return;

        if (lastCacheUpdateTime < SystemClock.uptimeMillis() - CirclesConstants.CIRCLES_CACHE_UPDATE_INTERVAL) {
            loadCircles(null);
        } else {
            setSelectedCircle(null);
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
                                //store bot user
                                accountInstance.getMessagesController().putUser(user, false);
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

        //send start request
        accountInstance.getSendMessagesHelper()
            .sendMessage("/start api", preferences.getBotPeerId(), null, null, false, null, null, null, true, 0);
        Logger.d("Sent bot /start api message to "+preferences.getBotPeerId());

        //wait for incoming message
        return Single.create((emitter) -> {
            String latestToken = "";

            synchronized (botMessages) {
                newMessagesAwaiting.incrementAndGet();
                Logger.d("Waiting for bot response");
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
                //load all bot message to cleanup later
                accountInstance.getMessagesController().loadMessages(preferences.getBotPeerId(), Integer.MAX_VALUE, 0, 0, false, 0, classGuid, 2, 0, false, false, lastLoadIndex++);
                emitter.onSuccess(new Object());
            } else {
                emitter.onError(new RequestError(RequestError.ErrorCode.DIDNT_RECEIVE_TOKEN));
            }

            cleanupBotMessages();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private Single<Object> createNewCircle(final @NonNull String circleName) {
        if (preferences.getBotPeerId() == null) {
            throw new IllegalStateException("Circles bot peer id not resolved yet");
        }

        //wait for incoming message
        return Single.create((emitter) -> {
            //send start request
            accountInstance.getSendMessagesHelper()
                    .sendMessage("/create "+circleName, preferences.getBotPeerId(), null, null, false, null, null, null, true, 0);
            Logger.d("Sent bot /create "+circleName+" message to "+preferences.getBotPeerId());

            int initialCountFromBot = 0;
            int newCountFromBot = 0;

            synchronized (botMessages) {
                newMessagesAwaiting.incrementAndGet();
                for (MessageObject message : botMessages) {
                    if (message.deleted || message.messageText == null) continue;
                    if (message.messageOwner.from_id == preferences.getBotPeerId()) {
                        initialCountFromBot++;
                    }
                }
                Logger.d("Waiting for bot response");
                botMessages.wait(CirclesConstants.BOT_MESSAGE_WAIT_TIMEOUT);


                for (MessageObject message : botMessages) {
                    if (message.deleted || message.messageText == null) continue;
                    if (message.messageOwner.from_id == preferences.getBotPeerId()) {
                        newCountFromBot++;
                    }
                }
                newMessagesAwaiting.decrementAndGet();
            }

            if (initialCountFromBot != newCountFromBot) {
                Logger.d("New workspace created");
                //load all bot message to cleanup later
                accountInstance.getMessagesController().loadMessages(preferences.getBotPeerId(), Integer.MAX_VALUE, 0, 0, false, 0, classGuid, 2, 0, false, false, lastLoadIndex++);
                emitter.onSuccess(new Object());
            } else {
                emitter.onError(new RequestError(RequestError.ErrorCode.ERROR_ON_CIRCLE_CREATION));
            }

            cleanupBotMessages();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private Single<Object> changeConnection(final @NonNull CircleData toCircle, final @NonNull Set<TLRPC.Dialog> dialogsToMove) {
        return Single.create((emitter) -> {
            final AtomicInteger requestsRunning = new AtomicInteger(0);
            final Set<Throwable> exceptions = new HashSet<>();

            for (TLRPC.Dialog dialog : dialogsToMove) {
                final ChangeConnection connection = new ChangeConnection();
                connection.toCircleId = toCircle.id;
                connection.fromCircleId = 0L;

                Map<Long, Set<TLRPC.Dialog>> dialogsMap = Utils.mapDialogsToCircles(cachedCircles, accountInstance);
                for (Long circleId : dialogsMap.keySet()) {
                    Set<TLRPC.Dialog> circleDialogs = dialogsMap.get(circleId);
                    if (circleDialogs != null) {
                        for (TLRPC.Dialog d : circleDialogs) {
                            if (d.id == dialog.id) {
                                if (connection.fromCircleId == 0L || circleId.equals(connection.fromCircleId)) {
                                    connection.fromCircleId = circleId;
                                } else {
                                    connection.fromCircleId = null;
                                }
                                break;
                            }
                        }
                    }
                }

                if (connection.toCircleId.equals(connection.fromCircleId)) {
                    continue;
                }

                TLRPC.User user = Utils.getDialogUser(dialog.id, accountInstance);
                if (user != null) {
                    connection.user = new UserData(user);
                } else {
                    TLRPC.Chat chat = Utils.getDialogChat(dialog.id, accountInstance);
                    TLRPC.ChatFull fullChat = null;
                    if (chat != null) {
                        fullChat = accountInstance.getMessagesController().getChatFull(chat.id);
                    }
                    if (ChatObject.isChannel(chat) && (fullChat == null || fullChat.participants == null || fullChat.participants.participants == null || fullChat.participants.participants.isEmpty())) {
                        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
                        req.channel = accountInstance.getMessagesController().getInputChannel(chat.id);
                        req.filter = new TLRPC.TL_channelParticipantsRecent();
                        req.offset = 0;
                        req.limit = 32;
                        final TLRPC.Dialog finalDialog = dialog;
                        compositeDisposable.add(sendRequest(req)
                                .subscribe(response -> {
                                    if (!(response instanceof TLRPC.TL_channels_channelParticipants)) {
                                        requestsRunning.decrementAndGet();
                                        synchronized (requestsRunning) {
                                            requestsRunning.notifyAll();
                                        }
                                    } else {
                                        connection.chat = getChatFromDialog(finalDialog, null, ((TLRPC.TL_channels_channelParticipants) response).users);
                                        sendConnectionData(requestsRunning, exceptions, connection);
                                    }
                                }, error -> {
                                    requestsRunning.decrementAndGet();
                                    synchronized (requestsRunning) {
                                        exceptions.add(error);
                                        requestsRunning.notifyAll();
                                    }
                                }));
                        continue;
                    } else {
                        connection.chat = getChatFromDialog(dialog, fullChat, null);
                    }
                }

                if (connection.user == null && connection.chat == null) {
                    continue;
                }

                requestsRunning.incrementAndGet();
                sendConnectionData(requestsRunning, exceptions, connection);
            }

            synchronized (requestsRunning) {
                while (requestsRunning.get() > 0 && !Thread.currentThread().isInterrupted()) {
                    try {
                        requestsRunning.wait();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            if (exceptions.isEmpty()) {
                loadCircles(null);
                emitter.onSuccess(new Object());
            } else {
                Throwable error = null;
                for (Throwable e : exceptions) {
                    if (error == null || e.equals(error) || (e.getMessage() != null && e.getMessage().equals(error.getMessage()))) {
                        error = e;
                    } else {
                        error = new RequestError(RequestError.ErrorCode.ERROR_ON_CIRCLE_MOVE);
                        break;
                    }
                }
                emitter.onError(error);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io());
    }

    private void sendConnectionData(final AtomicInteger requestsRunning, final Set<Throwable> exceptions, final ChangeConnection connection) {
        compositeDisposable.add(RetrofitHelper.service().changeConnection(preferences.getAuthToken(), connection)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .subscribe(res -> {
                    requestsRunning.decrementAndGet();
                    synchronized (requestsRunning) {
                        requestsRunning.notifyAll();
                    }
                }, error -> {
                    requestsRunning.decrementAndGet();
                    synchronized (requestsRunning) {
                        exceptions.add(error);
                        requestsRunning.notifyAll();
                    }
                }));
    }

    private ChatData getChatFromDialog(TLRPC.Dialog dialog, TLRPC.ChatFull chatFull, ArrayList<TLRPC.User> chatParticipants) {
        TLRPC.Chat chat = Utils.getDialogChat(dialog.id, accountInstance);
        if (chat != null) {
            Set<TLRPC.User> users = new HashSet<>();
            if (chatParticipants == null || chatParticipants.isEmpty()) {
                if (chatFull == null) {
                    chatFull = accountInstance.getMessagesController().getChatFull(chat.id);
                }
                if (chatFull != null && chatFull.participants != null && chatFull.participants.participants != null) {
                    for (TLRPC.ChatParticipant part : chatFull.participants.participants) {
                        TLRPC.User user = accountInstance.getMessagesController().getUser(part.user_id);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                }
            } else {
                users.addAll(chatParticipants);
            }
            return new ChatData(chat, users);
        }
        return null;
    }

    private void handleNewBotMessages(ArrayList<MessageObject> messages) {
        if (preferences.getBotPeerId() == null || messages == null || messages.isEmpty()) return;

        synchronized (botMessages) {
            boolean hasNewMessages = false;
            for (MessageObject message : messages) {
                if (message.getDialogId() == preferences.getBotPeerId() && !message.deleted) {
                    Logger.d("Message in bot chat: " + message.messageText);
                    if (!botMessages.contains(message)) {
                        boolean duplicatedMessage = false;
                        for (Iterator<MessageObject> i = botMessages.iterator(); i.hasNext(); ) {
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
        if (preferences.getBotPeerId() == null) return;

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
        if (preferences.getBotPeerId() == null || newMessagesAwaiting.get() != 0) return;

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

    private void cleanupOnAuthFailure() {
        accountInstance.getConnectionsManager().cleanup(false);
        accountInstance.getUserConfig().clearConfig();
        accountInstance.getMessagesStorage().cleanup(false);
        accountInstance.getMessagesController().cleanup();
        accountInstance.getContactsController().deleteUnknownAppAccounts();
    }

    private String getCurrentCircleTitle() {
        if (preferences.getSelectedCircleId() == CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL) {
            return ApplicationLoader.applicationContext.getString(R.string.circles_personal);
        } else if (preferences.getSelectedCircleId() == CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED) {
            return ApplicationLoader.applicationContext.getString(R.string.circles_archive);
        }

        String circleName = null;
        synchronized (cachedCircles) {
            for (CircleData c : cachedCircles) {
                if (c.id == preferences.getSelectedCircleId()) {
                    circleName = c.name;
                    break;
                }
            }
        }

        return (circleName != null && !circleName.isEmpty()) ? circleName :
                ApplicationLoader.applicationContext.getString(BuildConfig.DEBUG ? R.string.AppNameBeta : R.string.AppName);
    }



    // PUBLIC API

    public static Circles getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

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
            Logger.d("Telefrost already authorized");
            listener.onSuccess();
        } else {
            compositeDisposable.add(
                lookupBotPeerId()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(botLookupSuccess -> compositeDisposable.add(
                                requestToken()
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(authSuccess -> listener.onSuccess(), error -> {
                                            listener.onError(error);
                                            cleanupOnAuthFailure();
                                        })
                        ), error -> {
                            listener.onError(error);
                            cleanupOnAuthFailure();
                        }));
        }
    }

    public void updateDialogActionBarTitle(ActionBar actionBar) {
        if (actionBar != null) {
            actionBar.setTitle(getCurrentCircleTitle());
        }
    }

    public ArrayList<TLRPC.Dialog> filterOutArchived(int folderId, ArrayList<TLRPC.Dialog> dialogs) {
        if (dialogs == null || folderId != 0) {
            return dialogs;
        }
        ArrayList<TLRPC.Dialog> res = new ArrayList<>();
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog instanceof TLRPC.TL_dialogFolder && ((TLRPC.TL_dialogFolder) dialog).folder.id == 1) {
                continue;
            }
            res.add(dialog);
        }
        return res;
    }

    public List<CircleData> getCachedCircles() {
        return cachedCircles;
    }

    public void showWorkspaceSelector(BaseFragment baseFragment) {
        baseFragment.showDialog(new WorkspaceSelector(accountNum, baseFragment));
    }

    public void showWorkspaceSelector(BaseFragment baseFragment, Collection<Long> dialogsToMove) {
        baseFragment.showDialog(new WorkspaceSelector(accountNum, baseFragment, dialogsToMove));
    }

    public void loadCircles(SuccessListener listener) {
        circlesRequestInProgress = true;
        compositeDisposable.add(RetrofitHelper.service().getCircles(preferences.getAuthToken())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(circlesList -> {
                    List<CircleData> circles = new ArrayList<>();
                    if (circlesList != null && circlesList.circles != null) {
                        circles.addAll(Arrays.asList(circlesList.circles));
                    }
                    Map<Long, Set<TLRPC.Dialog>> dialogsMap = Utils.mapDialogsToCircles(circles, accountInstance);
                    cacheCircles(circlesList, dialogsMap);
                    selectCurrentCircle(dialogsMap);
                    circlesRequestInProgress = false;
                    if (listener != null) {
                        listener.onSuccess();
                    }
                }, error -> {
                    circlesRequestInProgress = false;
                    if (listener != null) {
                        listener.onError(error);
                    }
                }));
    }

    public void setSelectedCircle(CircleData circle) {
        if (circle != null) {
            if (circle.circleType == CircleType.ARCHIVE) {
                return;
            }
            preferences.setSelectedCircleId(circle.id);
        }
        selectCurrentCircle(Utils.mapDialogsToCircles(cachedCircles, accountInstance));
    }

    public void createWorkspace(String circleName, @NonNull SuccessListener listener) {
        if (circleName == null || circleName.isEmpty()) {
            listener.onError(new RequestError(RequestError.ErrorCode.CIRCLE_NAME_IS_EMPTY));
            return;
        }

        compositeDisposable.add(createNewCircle(circleName)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> listener.onSuccess(), listener::onError));
    }

    public boolean canMoveDialog(TLRPC.Dialog dialog) {
        return dialog != null && dialog.folder_id != 1 && dialog.id != preferences.getBotPeerId() && !(dialog instanceof TLRPC.TL_dialogFolder && ((TLRPC.TL_dialogFolder) dialog).folder.id == 1) && !Utils.isSavedMessages(dialog.id, accountInstance);
    }

    public void moveDialogs(@NonNull CircleData toCircle, @NonNull Collection<Long> dialogIds, @NonNull SuccessListener listener) {
        Logger.d("Moving "+dialogIds.size()+" dialogs");
        Set<TLRPC.Dialog> dialogsToMove = new HashSet<>();
        for (TLRPC.Dialog dialog : accountInstance.getMessagesController().getAllDialogs()) {
            if (dialogIds.contains(dialog.id) && canMoveDialog(dialog)) {
                dialogsToMove.add(dialog);
            }
        }

        if (dialogsToMove.isEmpty()) {
            listener.onSuccess();
        } else {
            compositeDisposable.add(changeConnection(toCircle, dialogsToMove)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(success -> listener.onSuccess(), listener::onError));
        }
    }
}
