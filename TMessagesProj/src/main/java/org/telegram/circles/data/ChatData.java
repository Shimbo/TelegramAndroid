package org.telegram.circles.data;

import org.telegram.tgnet.TLRPC;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;

public class ChatData implements Serializable {
    public long id;
    public String title;
    public ArrayList<UserData> users;

    public ChatData(TLRPC.Chat chat, Set<TLRPC.User> users) {
        id = -1000000000000L - chat.id;
        title = chat.title;

        this.users = new ArrayList<>();
        if (users != null && !users.isEmpty()) {
            for (TLRPC.User user : users) {
                this.users.add(new UserData(user));
            }
        }
    }
}
