package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.util.List;
import java.util.Map;

public class KafkaConsumerProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerProcessor.class);

    private KafkaReceiver<String, String> receiver;
    private Sinks.Many<Object> dataSink;

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
        return Mono.fromRunnable(() -> {
            String bootstrapServers = config.getOrDefault("bootstrapServers", "localhost:9092").toString();
            String topic = config.getOrDefault("topic", "").toString();
            String groupId = config.getOrDefault("groupId", "transflow").toString();

            if (topic.isBlank()) {
                throw new IllegalArgumentException("Kafka topic is required");
            }

            Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"
            );

            ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(props)
                .subscription(List.of(topic));

            this.receiver = KafkaReceiver.create(options);
            this.dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

            log.info("[KafkaConsumer] Initialized, servers={}, topic={}, group={}", bootstrapServers, topic, groupId);
        });
    }

    @Override
    public Mono<Object> process(Object data) {
        if (data != null) {
            return Mono.justOrEmpty(data);
        }
        return Mono.from(receiver.receive()
            .doOnNext(record -> {
                record.receiverOffset().acknowledge();
            })
            .map(record -> {
                Map<String, Object> msg = new java.util.HashMap<>();
                msg.put("topic", record.topic());
                msg.put("partition", record.partition());
                msg.put("offset", record.offset());
                msg.put("key", record.key());
                try {
                    msg.put("value", JSON.parse(record.value()));
                } catch (Exception e) {
                    msg.put("value", record.value());
                }
                msg.put("timestamp", record.timestamp());
                return (Object) msg;
            })
            .doOnError(err -> log.error("[KafkaConsumer] Error: {}", err.getMessage())));
    }

    @Override
    public void destroy() {
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
        log.info("[KafkaConsumer] Stopped");
    }
}
