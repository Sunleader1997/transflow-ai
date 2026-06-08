package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class KafkaProducerProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerProcessor.class);

    @Override
    public String getType() { return "KAFKA-PRODUCER"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("bootstrapServers", "Bootstrap Servers", "text", "localhost:9092", "localhost:9092", null, null),
            new NodeParam("topic", "Topic", "text", "", "发送目标主题", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // Kafka producer placeholder
        // In production, create reactive Kafka sender here
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromRunnable(() -> {
            String value = data instanceof String s ? s : JSON.toJSONString(data);
            log.info("[KafkaProducer] Sending: {}", value);
        }).then(Mono.justOrEmpty(data));
    }

    @Override
    public void destroy() {}
}
