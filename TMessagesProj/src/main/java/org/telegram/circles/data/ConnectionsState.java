package org.telegram.circles.data;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;

public class ConnectionsState implements Serializable {
    @SerializedName("circle")
    public long circleId;
    public ArrayList<ChangeConnection> peers;
}
