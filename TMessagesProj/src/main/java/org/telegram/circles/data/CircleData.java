package org.telegram.circles.data;

import org.telegram.circles.CircleType;
import org.telegram.circles.CirclesConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CircleData implements Serializable {
    public long id;
    public String name;
    public Integer role;
    public String tier;
    public ArrayList<Long> peers;
    public ArrayList<Long> members;

    //local fields
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

    public boolean isLocked() {
        if (circleType != CircleType.WORKSPACE || id == CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED || id == CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL) {
            return false;
        } else {
            return role != 1;
        }
    }

    public boolean isPaid() {
        if (circleType != CircleType.WORKSPACE || id == CirclesConstants.DEFAULT_CIRCLE_ID_ARCHIVED || id == CirclesConstants.DEFAULT_CIRCLE_ID_PERSONAL) {
            return false;
        } else {
            return "paid".equalsIgnoreCase(tier);
        }
    }
}
