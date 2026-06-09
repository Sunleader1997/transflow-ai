package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServerProcessor extends AbstractNodeProcessor {

    private DisposableServer server;
    private Sinks.Many<Object> dataSink;

    @Override
    public String getType() { return "HTTP-SERVER"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("port", "监听端口", "number", "8888", "8888", null, null),
            new NodeParam("path", "路径", "text", "/api/data", "/api/data", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        int port = Integer.parseInt(config.getOrDefault("port", "8888").toString());
        String path = (String) config.getOrDefault("path", "/api/data");
        dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

        return Mono.fromCallable(() -> {
            server = HttpServer.create()
                    .port(port)
                    .route(routes -> routes
                        .post(path, (req, res) ->
                            req.receive().aggregate().asString()
                                .flatMap(body -> {
                                    String requestId = HttpRequestContext.createPendingResponse();
                                    Map<String, Object> envelope = buildEnvelope(path, "POST", body, requestId);
                                    dataSink.tryEmitNext(envelope);
                                    HttpRequestContext.PendingResponse pending = HttpRequestContext.getPending(requestId);
                                    return pending.asMono()
                                        .doFinally(signal -> HttpRequestContext.removePending(requestId))
                                        .flatMap(responseBody -> res.sendString(Mono.just(responseBody)).then())
                                        .onErrorResume(err -> res.status(500).sendString(Mono.just(err.getMessage())).then());
                                })
                        )
                        .get(path, (req, res) -> {
                            String requestId = HttpRequestContext.createPendingResponse();
                            Map<String, Object> envelope = buildEnvelopeFromQuery(path, req.uri(), requestId);
                            dataSink.tryEmitNext(envelope);
                            HttpRequestContext.PendingResponse pending = HttpRequestContext.getPending(requestId);
                            return pending.asMono()
                                .doFinally(signal -> HttpRequestContext.removePending(requestId))
                                .flatMap(responseBody -> res.sendString(Mono.just(responseBody)).then())
                                .onErrorResume(err -> res.status(500).sendString(Mono.just(err.getMessage())).then());
                        })
                    )
                    .bindNow();
            return null;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.from(dataSink.asFlux());
    }

    @Override
    public Flux<Object> output() {
        return dataSink.asFlux();
    }

    @Override
    public void destroy() {
        if (server != null) {
            server.dispose();
        }
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
    }

    private Map<String, Object> buildEnvelope(String api, String method, String body, String requestId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("api", api);
        envelope.put("method", method.toLowerCase());
        envelope.put("requestId", requestId);
        try {
            envelope.put("body", JSON.parseObject(body, Map.class));
        } catch (Exception e) {
            envelope.put("body", body);
        }
        return envelope;
    }

    private Map<String, Object> buildEnvelopeFromQuery(String api, String uri, String requestId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("api", api);
        envelope.put("method", "get");
        envelope.put("requestId", requestId);
        Map<String, Object> params = new HashMap<>();
        int qi = uri.indexOf('?');
        if (qi >= 0) {
            String query = uri.substring(qi + 1);
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                params.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        envelope.put("body", params);
        return envelope;
    }
}
