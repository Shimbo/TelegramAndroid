package org.telegram.circles.data;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class CircleData implements Serializable {
    public int id;
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
    public int[] peers;
    public int[] members;
}
