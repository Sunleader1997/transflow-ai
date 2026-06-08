package org.sunyaxing.transflow.model;

import java.time.Instant;

public class NodeStatus {
    private String state;      // RUNNING, ERROR, STOPPED
    private String message;
    private long timestamp;
    private long recNumb;
    private long sendNumb;

    public NodeStatus() {
        this.state = "STOPPED";
        this.timestamp = Instant.now().toEpochMilli();
        this.recNumb = 0;
        this.sendNumb = 0;
    }

    public NodeStatus(String state) {
        this.state = state;
        this.timestamp = Instant.now().toEpochMilli();
        this.recNumb = 0;
        this.sendNumb = 0;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; this.timestamp = Instant.now().toEpochMilli(); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getRecNumb() { return recNumb; }
    public void setRecNumb(long recNumb) { this.recNumb = recNumb; }
    public long getSendNumb() { return sendNumb; }
    public void setSendNumb(long sendNumb) { this.sendNumb = sendNumb; }
}
