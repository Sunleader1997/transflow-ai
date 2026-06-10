package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class ConsoleProcessor extends AbstractNodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConsoleProcessor.class);
    private String prefix;

    @Override
    public String getType() { return "CONSOLE"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("prefix", "日志前缀", "text", "[TransFlow]", "[TransFlow]", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        this.prefix = (String) config.getOrDefault("prefix", "[TransFlow]");
        initInputSink();
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromRunnable(() -> {
            String output = data instanceof String s ? s : JSON.toJSONString(data);
            log.info("{} {}", prefix, output);
        }).then(Mono.justOrEmpty(data));
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
