package org.sunyaxing.transflow.workflow.model;

import java.util.Map;

public class WorkflowNode {
    private String id;
    private String title;
    private String description;
    private Map<String, Object> position;

    public WorkflowNode() {}

    public WorkflowNode(String id, String title, String description, Map<String, Object> position) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.position = position;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getPosition() { return position; }
    public void setPosition(Map<String, Object> position) { this.position = position; }
}
