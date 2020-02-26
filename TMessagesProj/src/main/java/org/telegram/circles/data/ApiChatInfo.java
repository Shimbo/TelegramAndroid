package org.telegram.circles.data;

import java.io.Serializable;

public class ApiChatInfo implements Serializable {
    public long id;
    public String title;
    public ApiUserInfo[] users;
}
