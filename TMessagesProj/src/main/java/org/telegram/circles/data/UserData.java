package org.telegram.circles.data;

import org.telegram.tgnet.TLRPC;

import java.io.Serializable;

public class UserData implements Serializable {
    public long id;
    public String firstname;
    public String lastname;
    public String username;

    public UserData(TLRPC.User user) {
        id = user.id;
        username = user.username;
        firstname = user.first_name;
        lastname = user.last_name;
    }
}
