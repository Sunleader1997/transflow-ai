package org.sunyaxing.transflow.workflow.model;

import java.time.LocalDateTime;

public class ExecutionNode extends WorkflowNode {
    private String status; // pending, in_progress, completed, failed, skipped
    private String detail;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public ExecutionNode() {
        super();
        this.status = "pending";
    }

    public ExecutionNode(WorkflowNode node) {
        super(node.getId(), node.getTitle(), node.getDescription(), node.getPosition());
        this.status = "pending";
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
