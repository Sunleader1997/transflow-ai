package org.sunyaxing.transflow.workflow.controller;

import org.sunyaxing.transflow.workflow.model.WorkflowTemplate;
import org.sunyaxing.transflow.workflow.service.WorkflowTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateService service;

    public WorkflowTemplateController(WorkflowTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkflowTemplate> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public WorkflowTemplate get(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public WorkflowTemplate create(@RequestBody WorkflowTemplate template) {
        return service.create(template);
    }

    @PutMapping("/{id}")
    public WorkflowTemplate update(@PathVariable String id, @RequestBody WorkflowTemplate template) {
        return service.update(id, template);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
