package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class FileProcessor implements NodeProcessor {

    @Override
    public String getType() { return "FILE"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("path", "文件路径", "text", "", "/path/to/file", null, null),
            new NodeParam("mode", "监听模式", "select", "TAIL", "TAIL", null, List.of("TAIL", "FULL"))
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // File watching placeholder
        // In production, use WatchService to monitor file changes
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.justOrEmpty(data);
    }

    @Override
    public void destroy() {}
}
