package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class SyslogOutputProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyslogOutputProcessor.class);

    @Override
    public String getType() { return "SYSLOG-OUTPUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("host", "目标主机", "text", "localhost", "localhost", null, null),
            new NodeParam("port", "目标端口", "number", "514", "514", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // Syslog UDP sender placeholder
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromRunnable(() -> {
            String value = data instanceof String s ? s : JSON.toJSONString(data);
            log.info("[Syslog] Send to {}:{}: {}", "localhost", "514", value);
        }).then(Mono.justOrEmpty(data));
    }

    @Override
    public void destroy() {}
}
