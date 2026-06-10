package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

public class TxtInputProcessor extends AbstractNodeProcessor {

    private Sinks.Many<Object> dataSink;

    @Override
    public String getType() { return "TXT-INPUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("text", "输入文本", "textarea", "", "输入文本内容，每行一条数据", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        String text = (String) config.getOrDefault("text", "");
        initInputSink();
        dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

        if (text != null && !text.isBlank()) {
            Flux.fromArray(text.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .cast(Object.class)
                .doOnComplete(() -> dataSink.tryEmitComplete())
                .subscribe(dataSink::tryEmitNext);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        if (data != null) {
            return Mono.just(data);
        }
        return Mono.from(dataSink.asFlux());
    }

    @Override
    public Flux<Object> output() {
        return Flux.merge(dataSink.asFlux(), inputSink().asFlux());
    }

    @Override
    public void destroy() {
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
        super.destroy();
    }
}
