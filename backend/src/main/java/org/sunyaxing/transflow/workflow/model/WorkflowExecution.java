package org.sunyaxing.transflow.workflow.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkflowExecution {
    private String id;
    private String templateId;
    private String status; // pending, running, completed, failed, stopped
    private List<ExecutionNode> nodes;
    private List<WorkflowEdge> edges;
    private LocalDateTime heartbeatAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public WorkflowExecution() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.status = "pending";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ExecutionNode> getNodes() { return nodes; }
    public void setNodes(List<ExecutionNode> nodes) { this.nodes = nodes; }
    public List<WorkflowEdge> getEdges() { return edges; }
    public void setEdges(List<WorkflowEdge> edges) { this.edges = edges; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
