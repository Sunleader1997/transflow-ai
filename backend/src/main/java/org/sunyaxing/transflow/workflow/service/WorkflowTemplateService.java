package org.sunyaxing.transflow.workflow.service;

import org.sunyaxing.transflow.workflow.model.WorkflowTemplate;
import org.sunyaxing.transflow.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowTemplateService {

    private final WorkflowRepository repository;

    public WorkflowTemplateService(WorkflowRepository repository) {
        this.repository = repository;
    }

    public List<WorkflowTemplate> findAll() {
        return repository.findAllTemplates();
    }

    public WorkflowTemplate findById(String id) {
        return repository.findTemplateById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
    }

    public WorkflowTemplate create(WorkflowTemplate template) {
        template.setId(UUID.randomUUID().toString().substring(0, 8));
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return repository.saveTemplate(template);
    }

    public WorkflowTemplate update(String id, WorkflowTemplate template) {
        WorkflowTemplate existing = findById(id);
        existing.setName(template.getName());
        existing.setDescription(template.getDescription());
        existing.setNodes(template.getNodes());
        existing.setEdges(template.getEdges());
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.saveTemplate(existing);
    }

    public void delete(String id) {
        repository.deleteTemplateById(id);
    }
}
