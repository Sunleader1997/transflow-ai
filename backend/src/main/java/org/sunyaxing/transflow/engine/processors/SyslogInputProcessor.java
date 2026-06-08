package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class SyslogInputProcessor implements NodeProcessor {

    @Override
    public String getType() { return "SYSLOG-INPUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("port", "监听端口", "number", "514", "514", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // Syslog UDP receiver placeholder
        // In production, create DatagramChannel for UDP syslog messages
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.justOrEmpty(data);
    }

    @Override
    public void destroy() {}
}
