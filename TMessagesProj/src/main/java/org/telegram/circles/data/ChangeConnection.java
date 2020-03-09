package org.telegram.circles.data;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ChangeConnection implements Serializable {
    @SerializedName("circle")
    public Long toCircleId;
    @SerializedName("from_circle")
    public Long fromCircleId;
    public ChatData chat;
    public UserData user;
}
