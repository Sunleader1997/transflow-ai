package org.sunyaxing.transflow.controller;

import org.sunyaxing.transflow.engine.FlowEngineManager;
import org.sunyaxing.transflow.engine.processors.NodeProcessor;
import org.sunyaxing.transflow.engine.processors.NodeProcessorFactory;
import org.sunyaxing.transflow.model.Flow;
import org.sunyaxing.transflow.model.Node;
import org.sunyaxing.transflow.model.NodeParam;
import org.sunyaxing.transflow.model.Task;
import org.sunyaxing.transflow.persistence.FileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/api/flow")
public class FlowController {

    private final FileRepository repository;
    private final NodeProcessorFactory factory;
    private final FlowEngineManager engineManager;

    public FlowController(FileRepository repository, NodeProcessorFactory factory, FlowEngineManager engineManager) {
        this.repository = repository;
        this.factory = factory;
        this.engineManager = engineManager;
    }

    @GetMapping("/{taskId}")
    public Mono<ResponseEntity<Map<String, Object>>> getFlow(@PathVariable String taskId) {
        return Mono.fromCallable(() ->
            repository.findById(taskId).map(task -> {
                Map<String, Object> result = new HashMap<>();
                result.put("name", task.getName());
                result.put("flow", task.getFlow());
                // Attach configParams to each node
                if (task.getFlow() != null && task.getFlow().getNodes() != null) {
                    List<Map<String, Object>> nodesWithParams = new ArrayList<>();
                    for (Node node : task.getFlow().getNodes()) {
                        Map<String, Object> nodeData = new HashMap<>();
                        nodeData.put("id", node.getId());
                        nodeData.put("type", node.getType());
                        nodeData.put("label", node.getLabel());
                        nodeData.put("position", node.getPosition());
                        nodeData.put("config", node.getConfig());
                        nodeData.put("status", node.getStatus());
                        nodeData.put("configParams", factory.getConfigParams(node.getType()));
                        nodesWithParams.add(nodeData);
                    }
                    // Replace nodes with enriched version
                    Flow enriched = new Flow();
                    enriched.setEdges(task.getFlow().getEdges());
                    // We can't easily change flow nodes type, so add a separate field
                    result.put("nodesWithParams", nodesWithParams);
                }
                return ResponseEntity.ok(result);
            }).orElse(ResponseEntity.notFound().build())
        );
    }

    @PutMapping("/{taskId}")
    public Mono<ResponseEntity<Task>> saveFlow(@PathVariable String taskId, @RequestBody Flow flow) {
        return Mono.fromCallable(() ->
            repository.findById(taskId).map(task -> {
                task.setFlow(flow);
                task.setUpdatedAt(System.currentTimeMillis());
                Task saved = repository.save(task);

                // Restart engine with updated flow
                engineManager.stopTask(taskId);
                engineManager.startTask(taskId, flow);

                return ResponseEntity.ok(saved);
            }).orElse(ResponseEntity.notFound().build())
        );
    }
}
