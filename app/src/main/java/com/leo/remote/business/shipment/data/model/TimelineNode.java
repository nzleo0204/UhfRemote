package com.leo.remote.business.shipment.data.model;

public final class TimelineNode {
    public String title;
    public String desc;
    public long time;
    public boolean done;

    public TimelineNode(String title, String desc, long time, boolean done) {
        this.title = title;
        this.desc = desc;
        this.time = time;
        this.done = done;
    }
}
