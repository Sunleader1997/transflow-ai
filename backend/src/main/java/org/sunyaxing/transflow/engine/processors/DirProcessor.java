package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class DirProcessor implements NodeProcessor {

    @Override
    public String getType() { return "DIR"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("path", "目录路径", "text", "", "/path/to/dir", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // Directory watching placeholder
        // In production, use WatchService to monitor directory changes
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.justOrEmpty(data);
    }

    @Override
    public void destroy() {}
}
