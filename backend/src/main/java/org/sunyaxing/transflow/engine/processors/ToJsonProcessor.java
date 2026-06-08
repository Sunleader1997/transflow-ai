package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class ToJsonProcessor implements NodeProcessor {

    @Override
    public String getType() { return "TO-JSON"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("", "将数据格式化为 JSON 字符串", "hint", "", "", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        if (data == null) return Mono.empty();
        return Mono.fromCallable(() -> {
            if (data instanceof String s) {
                // Try to parse if it's a JSON string, otherwise wrap
                try {
                    return JSON.parse(s);
                } catch (Exception e) {
                    return Map.of("value", s);
                }
            }
            return JSON.toJSON(data);
        });
    }

    @Override
    public void destroy() {}
}
