package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServerProcessor extends AbstractNodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(HttpServerProcessor.class);

    private DisposableServer server;
    private Sinks.Many<Object> dataSink;
    private String defaultResponse;
    private long timeoutSeconds;

    @Override
    public String getType() { return "HTTP-SERVER"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("port", "监听端口", "number", "8888", "8888", null, null),
            new NodeParam("defaultResponse", "默认返回", "textarea", "{\"code\":200,\"message\":\"ok\"}",
                "流程走完但无 HTTP-BACK 响应时返回的数据", null, null),
            new NodeParam("timeout", "超时(秒)", "number", "30", "等待响应的超时时间，超时返回默认数据", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        int port = Integer.parseInt(config.getOrDefault("port", "8888").toString());
        defaultResponse = (String) config.getOrDefault("defaultResponse", "{\"code\":200,\"message\":\"ok\"}");
        timeoutSeconds = Long.parseLong(config.getOrDefault("timeout", "30").toString());
        dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

        return Mono.fromRunnable(() -> {
            try {
                server = HttpServer.create()
                        .port(port)
                        .route(routes -> routes
                            .get("/**", (req, res) -> handleRequest(req, res, "get"))
                            .post("/**", (req, res) -> handleRequest(req, res, "post"))
                            .put("/**", (req, res) -> handleRequest(req, res, "put"))
                            .delete("/**", (req, res) -> handleRequest(req, res, "delete"))
                        )
                        .bindNow();
                log.info("[HttpServer] started on port {}", port);
            } catch (Exception e) {
                log.error("[HttpServer] failed to bind port {}: {}", port, e.getMessage());
                throw e;
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).then();
    }

    private Mono<Void> handleRequest(HttpServerRequest req,
                                     reactor.netty.http.server.HttpServerResponse res,
                                     String method) {
        String uri = req.uri();
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;

        Mono<String> bodyMono = method.equals("get") || method.equals("delete")
                ? Mono.just("")
                : req.receive().aggregate().asString().defaultIfEmpty("");

        return bodyMono.flatMap(body -> {
            String requestId = HttpRequestContext.createPendingResponse();
            Map<String, Object> envelope = buildEnvelope(path, method, body, uri, requestId);
            log.info("[HttpServer] {} {} requestId={} envelope={}", method.toUpperCase(), path, requestId, envelope);
            Sinks.EmitResult result = dataSink.tryEmitNext(envelope);
            if (result.isFailure()) {
                log.error("[HttpServer] emit failed: {}", result);
                HttpRequestContext.removePending(requestId);
                return res.status(500).sendString(Mono.just("emit failed: " + result)).then();
            }

            HttpRequestContext.PendingResponse pending = HttpRequestContext.getPending(requestId);
            return pending.asMono()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doFinally(signal -> HttpRequestContext.removePending(requestId))
                .flatMap(responseBody -> res.sendString(Mono.just(responseBody)).then())
                .onErrorResume(err -> {
                    log.warn("[HttpServer] requestId={} timeout or error, returning default response", requestId);
                    return res.sendString(Mono.just(defaultResponse)).then();
                });
        });
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

    private Map<String, Object> buildEnvelope(String path, String method, String body, String uri, String requestId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("path", path);
        envelope.put("method", method);
        envelope.put("requestId", requestId);

        Map<String, Object> query = new HashMap<>();
        int qi = uri.indexOf('?');
        if (qi >= 0) {
            for (String pair : uri.substring(qi + 1).split("&")) {
                String[] kv = pair.split("=", 2);
                query.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        envelope.put("query", query);

        if (body != null && !body.isEmpty()) {
            try {
                envelope.put("body", JSON.parseObject(body, Map.class));
            } catch (Exception e) {
                envelope.put("body", body);
            }
        } else {
            envelope.put("body", Map.of());
        }
        return envelope;
    }
}
