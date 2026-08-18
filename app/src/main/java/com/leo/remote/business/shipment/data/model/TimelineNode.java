package com.leo.remote.business.shipment.data.model;

/**
 * 表示发运流程时间线上的一个节点。
 */
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
