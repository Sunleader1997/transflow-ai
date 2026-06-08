package org.sunyaxing.transflow.controller;

import org.sunyaxing.transflow.engine.FlowEngineManager;
import org.sunyaxing.transflow.model.NodeStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flow-data")
public class FlowDataController {

    private final FlowEngineManager engineManager;

    public FlowDataController(FlowEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    @PostMapping("/emit")
    public Mono<ResponseEntity<Map<String, String>>> emit(@RequestBody Map<String, Object> body) {
        return Mono.fromRunnable(() -> {
            String taskId = (String) body.get("taskId");
            String nodeId = (String) body.get("nodeId");
            Object data = body.get("data");
            engineManager.emitData(taskId, nodeId, data);
        }).then(Mono.just(ResponseEntity.ok(Map.of("status", "ok"))));
    }

    @GetMapping("/output/{nodeId}")
    public Mono<ResponseEntity<List<String>>> getOutput(@PathVariable String nodeId) {
        return Mono.fromCallable(() -> {
            // Find taskId from engines - simplified: iterate all
            for (var entry : engineManager.getEngines().entrySet()) {
                List<String> output = engineManager.getTxtOutput(entry.getKey(), nodeId);
                if (!output.isEmpty()) {
                    return ResponseEntity.ok(output);
                }
            }
            return ResponseEntity.ok(List.of());
        });
    }

    @GetMapping("/node-statuses/{taskId}")
    public Mono<ResponseEntity<Map<String, NodeStatus>>> getNodeStatuses(@PathVariable String taskId) {
        return Mono.fromCallable(() ->
            ResponseEntity.ok(engineManager.getNodeStatuses(taskId))
        );
    }
}
