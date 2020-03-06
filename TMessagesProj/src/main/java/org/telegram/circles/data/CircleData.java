package org.telegram.circles.data;

import com.google.gson.annotations.SerializedName;

import org.telegram.circles.CircleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CircleData implements Serializable {
    public long id;
    public String alias;
    public String domain;
    public String name;
    public String description;
    public String locale;
    public Float timezone;
    public Boolean disabled;
    @SerializedName("requires_login")
    public Boolean requiresLogin;
    @SerializedName("non_admins_can_post")
    public Boolean nonAdminsCanPost;
    @SerializedName("approval_type")
    public Integer approvalType;
    @SerializedName("enable_digest")
    public Boolean enableDigest;
    public Boolean activated;
    @SerializedName("zapier_ur_l")
    public String zapierURL;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("updated_at")
    public String updatedAt;
    public ArrayList<Long> peers;
    public ArrayList<Long> members;
    public CircleType circleType = CircleType.WORKSPACE;
    public int counter = 0;


    public Set<Long> getAllPeerIds() {
        Set<Long> res = new HashSet<>();
        if (peers != null) {
            res.addAll(peers);
        }
        if (members != null) {
            res.addAll(members);
        }
        return res;
    }
}
