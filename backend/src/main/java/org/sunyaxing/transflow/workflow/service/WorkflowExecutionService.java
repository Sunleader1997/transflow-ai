package org.sunyaxing.transflow.workflow.service;

import org.sunyaxing.transflow.workflow.model.*;
import org.sunyaxing.transflow.workflow.repository.WorkflowRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowExecutionService {

    private final WorkflowRepository repository;
    private final WorkflowTemplateService templateService;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 70;

    public WorkflowExecutionService(WorkflowRepository repository, WorkflowTemplateService templateService) {
        this.repository = repository;
        this.templateService = templateService;
    }

    public List<WorkflowExecution> findAll() {
        return repository.findAllExecutions();
    }

    public List<WorkflowExecution> findByTemplateId(String templateId) {
        return repository.findExecutionsByTemplateId(templateId);
    }

    public WorkflowExecution findById(String id) {
        return repository.findExecutionById(id)
                .orElseThrow(() -> new RuntimeException("Execution not found: " + id));
    }

    public WorkflowExecution create(String templateId) {
        WorkflowTemplate template = templateService.findById(templateId);
        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(UUID.randomUUID().toString().substring(0, 8));
        execution.setTemplateId(templateId);
        execution.setCreatedAt(LocalDateTime.now());

        List<ExecutionNode> nodes = template.getNodes().stream()
                .map(ExecutionNode::new)
                .toList();
        execution.setNodes(nodes);

        execution.setEdges(template.getEdges());

        return repository.saveExecution(execution);
    }

    public WorkflowExecution start(String id) {
        WorkflowExecution execution = findById(id);
        execution.setStatus("running");
        execution.setStartedAt(LocalDateTime.now());
        execution.setHeartbeatAt(LocalDateTime.now());
        return repository.saveExecution(execution);
    }

    public WorkflowExecution updateNodeStatus(String executionId, String nodeId, String status, String detail) {
        WorkflowExecution execution = findById(executionId);
        execution.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .ifPresent(node -> {
                    node.setStatus(status);
                    node.setDetail(detail);
                    if ("in_progress".equals(status)) {
                        node.setStartedAt(LocalDateTime.now());
                    } else if ("completed".equals(status) || "failed".equals(status)) {
                        node.setCompletedAt(LocalDateTime.now());
                    }
                });

        boolean allDone = execution.getNodes().stream()
                .allMatch(n -> "completed".equals(n.getStatus()) || "skipped".equals(n.getStatus()));
        boolean anyFailed = execution.getNodes().stream()
                .anyMatch(n -> "failed".equals(n.getStatus()));

        if (anyFailed) {
            execution.setStatus("failed");
            execution.setCompletedAt(LocalDateTime.now());
        } else if (allDone) {
            execution.setStatus("completed");
            execution.setCompletedAt(LocalDateTime.now());
        }

        return repository.saveExecution(execution);
    }

    public WorkflowExecution updateStatus(String id, String status) {
        WorkflowExecution execution = findById(id);
        execution.setStatus(status);
        if ("completed".equals(status) || "failed".equals(status) || "stopped".equals(status)) {
            execution.setCompletedAt(LocalDateTime.now());
        }
        return repository.saveExecution(execution);
    }

    public WorkflowExecution heartbeat(String id) {
        WorkflowExecution execution = findById(id);
        execution.setHeartbeatAt(LocalDateTime.now());
        return repository.saveExecution(execution);
    }

    @Scheduled(fixedRate = 10000)
    public void checkHeartbeatTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        repository.findAllExecutions().stream()
                .filter(e -> "running".equals(e.getStatus()))
                .filter(e -> e.getHeartbeatAt() != null)
                .filter(e -> Duration.between(e.getHeartbeatAt(), now).getSeconds() > HEARTBEAT_TIMEOUT_SECONDS)
                .forEach(e -> {
                    e.setStatus("stopped");
                    e.setCompletedAt(now);
                    repository.saveExecution(e);
                });
    }
}
