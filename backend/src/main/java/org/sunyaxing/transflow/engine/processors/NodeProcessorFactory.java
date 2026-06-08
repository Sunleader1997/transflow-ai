package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.Node;
import org.sunyaxing.transflow.model.NodeParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class NodeProcessorFactory {

    private final Map<String, Supplier<NodeProcessor>> registry = new HashMap<>();

    public void register(String type, Supplier<NodeProcessor> supplier) {
        registry.put(type, supplier);
    }

    public List<NodeParam> getConfigParams(String type) {
        Supplier<NodeProcessor> supplier = registry.get(type);
        if (supplier != null) {
            return supplier.get().configParams();
        }
        return List.of();
    }

    public Mono<NodeProcessor> create(Node node) {
        Supplier<NodeProcessor> supplier = registry.get(node.getType());
        if (supplier == null) {
            return Mono.error(new IllegalArgumentException("Unknown node type: " + node.getType()));
        }
        NodeProcessor processor = supplier.get();
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        return processor.init(node.getId(), config)
            .thenReturn(processor)
            .subscribeOn(Schedulers.boundedElastic());
    }
}
