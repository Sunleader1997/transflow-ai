package org.sunyaxing.transflow.workflow.controller;

import org.sunyaxing.transflow.workflow.model.WorkflowExecution;
import org.sunyaxing.transflow.workflow.service.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-executions")
public class WorkflowExecutionController {

    private final WorkflowExecutionService service;

    public WorkflowExecutionController(WorkflowExecutionService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkflowExecution> list(@RequestParam(required = false) String templateId) {
        if (templateId != null) {
            return service.findByTemplateId(templateId);
        }
        return service.findAll();
    }

    @GetMapping("/{id}")
    public WorkflowExecution get(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public WorkflowExecution create(@RequestBody Map<String, String> body) {
        String templateId = body.get("templateId");
        return service.create(templateId);
    }

    @PostMapping("/{id}/start")
    public WorkflowExecution start(@PathVariable String id) {
        return service.start(id);
    }

    @PutMapping("/{id}/status")
    public WorkflowExecution updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        return service.updateStatus(id, body.get("status"));
    }

    @PutMapping("/{id}/nodes/{nodeId}/status")
    public WorkflowExecution updateNodeStatus(
            @PathVariable String id,
            @PathVariable String nodeId,
            @RequestBody Map<String, String> body) {
        return service.updateNodeStatus(id, nodeId, body.get("status"), body.get("detail"));
    }

    @PostMapping("/{id}/heartbeat")
    public WorkflowExecution heartbeat(@PathVariable String id) {
        return service.heartbeat(id);
    }
}
