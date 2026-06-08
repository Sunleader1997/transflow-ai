package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class HttpBackProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(HttpBackProcessor.class);

    private String script;

    @Override
    public String getType() { return "HTTP-BACK"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("script", "返回体脚本 (Groovy)", "code", "",
                "return [\"code\": 200, \"message\": \"success\", \"data\": data]",
                "data 为上游传入数据，返回值将作为 HTTP 响应体。使用 requestId 可追溯请求。", null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        this.script = (String) config.getOrDefault("script", "");
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromCallable(() -> {
            String requestId = extractRequestId(data);
            if (requestId == null) {
                log.warn("[HttpBack] No requestId in data, cannot send response");
                return null;
            }
            HttpRequestContext.PendingResponse pending = HttpRequestContext.getPending(requestId);
            if (pending == null) {
                log.warn("[HttpBack] Pending response not found for requestId: {}", requestId);
                return null;
            }
            try {
                String responseBody;
                if (script == null || script.isBlank()) {
                    responseBody = data instanceof String s ? s : JSON.toJSONString(data);
                } else {
                    GroovyShell shell = new GroovyShell();
                    shell.setVariable("data", data);
                    shell.setVariable("requestId", requestId);
                    Object result = shell.evaluate(script);
                    responseBody = JSON.toJSONString(result);
                }
                pending.complete(responseBody);
            } catch (Exception e) {
                log.error("[HttpBack] Groovy script error: {}", e.getMessage(), e);
                pending.error(e.getMessage());
            }
            return null;
        });
    }

    @Override
    public void destroy() {}

    @SuppressWarnings("unchecked")
    private String extractRequestId(Object data) {
        if (data instanceof Map) {
            Object rid = ((Map<String, Object>) data).get("requestId");
            return rid != null ? rid.toString() : null;
        }
        try {
            Map<String, Object> map = JSON.parseObject(data instanceof String ? (String) data : JSON.toJSONString(data), Map.class);
            Object rid = map.get("requestId");
            return rid != null ? rid.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
