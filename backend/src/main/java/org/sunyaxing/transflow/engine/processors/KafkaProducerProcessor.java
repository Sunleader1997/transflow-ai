package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.util.List;
import java.util.Map;

public class KafkaProducerProcessor extends AbstractNodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerProcessor.class);

    private KafkaSender<String, String> sender;
    private String topic;

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
        return Mono.fromRunnable(() -> {
            String bootstrapServers = config.getOrDefault("bootstrapServers", "localhost:9092").toString();
            this.topic = config.getOrDefault("topic", "").toString();

            if (topic.isBlank()) {
                throw new IllegalArgumentException("Kafka topic is required");
            }

            Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"
            );

            SenderOptions<String, String> options = SenderOptions.create(props);
            this.sender = KafkaSender.create(options);
            initInputSink();

            log.info("[KafkaProducer] Initialized, servers={}, topic={}", bootstrapServers, topic);
        });
    }

    @Override
    public Mono<Object> process(Object data) {
        String value = data instanceof String s ? s : JSON.toJSONString(data);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, value);
        return sender.send(Mono.just(SenderRecord.create(record, null)))
            .doOnNext(result -> log.debug("[KafkaProducer] Sent to {}@{}", topic, result.recordMetadata().offset()))
            .doOnError(err -> log.error("[KafkaProducer] Send error: {}", err.getMessage()))
            .single()
            .thenReturn(data);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (sender != null) {
            sender.close();
        }
        log.info("[KafkaProducer] Stopped, topic={}", topic);
    }
}
