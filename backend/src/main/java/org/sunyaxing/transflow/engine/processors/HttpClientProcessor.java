package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;

public class HttpClientProcessor implements NodeProcessor {

    private String url;
    private String method;

    @Override
    public String getType() { return "HTTP-CLIENT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("url", "URL", "text", "", "https://example.com/api", null, null),
            new NodeParam("method", "Method", "select", "POST", "POST", null, List.of("POST", "GET", "PUT"))
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        this.url = (String) config.getOrDefault("url", "");
        this.method = ((String) config.getOrDefault("method", "POST")).toUpperCase();
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        if (url == null || url.isBlank()) {
            return Mono.justOrEmpty(data);
        }
        String body = data instanceof String s ? s : JSON.toJSONString(data);
        return HttpClient.create()
                .headers(h -> h.set("Content-Type", "application/json"))
                .baseUrl(url)
                .post()
                .send((req, out) -> out.sendString(Mono.just(body)))
                .responseSingle((res, bytes) ->
                    bytes.asString().map(b -> {
                        try {
                            return JSON.parse(b);
                        } catch (Exception e) {
                            return Map.of("raw", (Object) b);
                        }
                    })
                )
                .onErrorResume(err ->
                    Mono.just(Map.of("error", (Object) err.getMessage()))
                );
    }

    @Override
    public void destroy() {}
}
