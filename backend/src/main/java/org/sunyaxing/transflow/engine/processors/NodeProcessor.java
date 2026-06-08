package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface NodeProcessor {

    /** Unique type identifier matching node.type */
    String getType();

    /** Configurable parameters, drives the frontend dynamic form */
    List<NodeParam> configParams();

    /** Initialize with configuration */
    Mono<Void> init(String nodeId, Map<String, Object> config);

    /** Process data, returning processed result or empty to drop */
    Mono<Object> process(Object data);

    /** Shutdown and release resources */
    void destroy();
}
