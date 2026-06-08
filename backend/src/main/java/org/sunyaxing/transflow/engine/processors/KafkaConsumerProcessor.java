package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class KafkaConsumerProcessor implements NodeProcessor {

    @Override
    public String getType() { return "KAFKA-CONSUMER"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("bootstrapServers", "Bootstrap Servers", "text", "localhost:9092", "localhost:9092", null, null),
            new NodeParam("topic", "Topic", "text", "", "订阅主题", null, null),
            new NodeParam("groupId", "Group ID", "text", "transflow", "消费者组ID", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        // Kafka consumer requires external broker; placeholder implementation
        // In production, create reactive Kafka receiver here
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.justOrEmpty(data);
    }

    @Override
    public void destroy() {}
}
