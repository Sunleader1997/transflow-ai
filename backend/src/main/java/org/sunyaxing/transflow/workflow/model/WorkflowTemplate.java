package org.sunyaxing.transflow.workflow.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkflowTemplate {
    private String id;
    private String name;
    private String description;
    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowTemplate() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<WorkflowNode> getNodes() { return nodes; }
    public void setNodes(List<WorkflowNode> nodes) { this.nodes = nodes; }
    public List<WorkflowEdge> getEdges() { return edges; }
    public void setEdges(List<WorkflowEdge> edges) { this.edges = edges; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
