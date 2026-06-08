package org.sunyaxing.transflow.engine;

import org.sunyaxing.transflow.engine.processors.NodeProcessorFactory;
import org.sunyaxing.transflow.engine.processors.TxtOutProcessor;
import org.sunyaxing.transflow.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FlowEngineManager {

    private final NodeProcessorFactory factory;
    private final Map<String, FlowEngine> engines = new ConcurrentHashMap<>();

    public FlowEngineManager(NodeProcessorFactory factory) {
        this.factory = factory;
    }

    public FlowEngine getOrCreate(String taskId) {
        return engines.computeIfAbsent(taskId, id -> new FlowEngine(id, factory));
    }

    public void startTask(String taskId, Flow flow) {
        FlowEngine engine = getOrCreate(taskId);
        engine.build(flow.getNodes() != null ? flow.getNodes() : List.of(),
                     flow.getEdges() != null ? flow.getEdges() : List.of())
            .doOnSuccess(v -> engine.start())
            .subscribe();
    }

    public void stopTask(String taskId) {
        FlowEngine engine = engines.remove(taskId);
        if (engine != null) {
            engine.stop();
        }
    }

    public void emitData(String taskId, String nodeId, Object data) {
        FlowEngine engine = engines.get(taskId);
        if (engine != null) {
            engine.emit(nodeId, data);
        }
    }

    public Map<String, NodeStatus> getNodeStatuses(String taskId) {
        FlowEngine engine = engines.get(taskId);
        if (engine != null) {
            return engine.getNodeStatuses();
        }
        return Map.of();
    }

    public Map<String, FlowEngine> getEngines() { return engines; }

    public List<String> getTxtOutput(String taskId, String nodeId) {
        FlowEngine engine = engines.get(taskId);
        if (engine != null) {
            var processor = engine.getProcessor(nodeId);
            if (processor instanceof TxtOutProcessor txtOut) {
                return txtOut.getOutput();
            }
        }
        return List.of();
    }
}
