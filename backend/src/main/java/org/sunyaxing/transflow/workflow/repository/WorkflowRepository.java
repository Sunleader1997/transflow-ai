package org.sunyaxing.transflow.workflow.repository;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.sunyaxing.transflow.workflow.model.WorkflowExecution;
import org.sunyaxing.transflow.workflow.model.WorkflowTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class WorkflowRepository {

    private static final String DB_DIR = "db";
    private static final String TEMPLATES_FILE = DB_DIR + "/workflow-templates.json";
    private static final String EXECUTIONS_FILE = DB_DIR + "/workflow-executions.json";

    public WorkflowRepository() {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
            if (!Files.exists(Paths.get(TEMPLATES_FILE))) {
                Files.writeString(Paths.get(TEMPLATES_FILE), "[]");
            }
            if (!Files.exists(Paths.get(EXECUTIONS_FILE))) {
                Files.writeString(Paths.get(EXECUTIONS_FILE), "[]");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize workflow repository", e);
        }
    }

    // Template methods
    public List<WorkflowTemplate> findAllTemplates() {
        try {
            String content = Files.readString(Paths.get(TEMPLATES_FILE));
            return JSON.parseArray(content, WorkflowTemplate.class);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public Optional<WorkflowTemplate> findTemplateById(String id) {
        return findAllTemplates().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();
    }

    public WorkflowTemplate saveTemplate(WorkflowTemplate template) {
        List<WorkflowTemplate> templates = findAllTemplates();
        Optional<WorkflowTemplate> existing = templates.stream()
                .filter(t -> t.getId().equals(template.getId()))
                .findFirst();
        if (existing.isPresent()) {
            templates.replaceAll(t -> t.getId().equals(template.getId()) ? template : t);
        } else {
            templates.add(template);
        }
        writeAllTemplates(templates);
        return template;
    }

    public void deleteTemplateById(String id) {
        List<WorkflowTemplate> templates = findAllTemplates();
        templates.removeIf(t -> t.getId().equals(id));
        writeAllTemplates(templates);
    }

    private void writeAllTemplates(List<WorkflowTemplate> templates) {
        try {
            String json = JSON.toJSONString(templates, JSONWriter.Feature.PrettyFormat);
            Files.writeString(Paths.get(TEMPLATES_FILE), json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write workflow templates", e);
        }
    }

    // Execution methods
    public List<WorkflowExecution> findAllExecutions() {
        try {
            String content = Files.readString(Paths.get(EXECUTIONS_FILE));
            return JSON.parseArray(content, WorkflowExecution.class);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public List<WorkflowExecution> findExecutionsByTemplateId(String templateId) {
        return findAllExecutions().stream()
                .filter(e -> e.getTemplateId().equals(templateId))
                .toList();
    }

    public Optional<WorkflowExecution> findExecutionById(String id) {
        return findAllExecutions().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    public WorkflowExecution saveExecution(WorkflowExecution execution) {
        List<WorkflowExecution> executions = findAllExecutions();
        Optional<WorkflowExecution> existing = executions.stream()
                .filter(e -> e.getId().equals(execution.getId()))
                .findFirst();
        if (existing.isPresent()) {
            executions.replaceAll(e -> e.getId().equals(execution.getId()) ? execution : e);
        } else {
            executions.add(execution);
        }
        writeAllExecutions(executions);
        return execution;
    }

    public void deleteExecutionById(String id) {
        List<WorkflowExecution> executions = findAllExecutions();
        executions.removeIf(e -> e.getId().equals(id));
        writeAllExecutions(executions);
    }

    private void writeAllExecutions(List<WorkflowExecution> executions) {
        try {
            String json = JSON.toJSONString(executions, JSONWriter.Feature.PrettyFormat);
            Files.writeString(Paths.get(EXECUTIONS_FILE), json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write workflow executions", e);
        }
    }
}
