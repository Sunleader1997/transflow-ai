package org.sunyaxing.transflow.model;

import java.util.List;
import java.util.Map;

public class Task {
    private String id;
    private String name;
    private String description;
    private Flow flow;
    private long createdAt;
    private long updatedAt;

    public Task() {}

    public Task(String id, String name, String description, Flow flow) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.flow = flow;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Flow getFlow() { return flow; }
    public void setFlow(Flow flow) { this.flow = flow; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
