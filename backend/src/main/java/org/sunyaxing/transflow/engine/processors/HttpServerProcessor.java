package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;
import java.util.Map;

public class HttpServerProcessor implements NodeProcessor {

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

        // Use boundedElastic scheduler for blocking bindNow() operation
        return Mono.fromCallable(() -> {
            server = HttpServer.create()
                    .port(port)
                    .route(routes -> routes
                        .post(path, (req, res) ->
                            req.receive().aggregate().asString()
                                .doOnNext(body -> {
                                    try {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> obj = com.alibaba.fastjson2.JSON.parseObject(body, Map.class);
                                        dataSink.tryEmitNext(obj);
                                    } catch (Exception e) {
                                        dataSink.tryEmitNext(body);
                                    }
                                })
                                .then(Mono.defer(() -> res.sendString(Mono.just("OK")).then()))
                        )
                        .get(path, (req, res) -> {
                            Map<String, Object> params = new java.util.HashMap<>();
                            String uri = req.uri();
                            int qi = uri.indexOf('?');
                            if (qi >= 0) {
                                String query = uri.substring(qi + 1);
                                for (String pair : query.split("&")) {
                                    String[] kv = pair.split("=", 2);
                                    params.put(kv[0], kv.length > 1 ? kv[1] : "");
                                }
                            }
                            dataSink.tryEmitNext(params);
                            return res.sendString(Mono.just("OK"));
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
    public void destroy() {
        if (server != null) {
            server.dispose();
        }
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
    }
}
