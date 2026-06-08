package org.sunyaxing.transflow.config;

import jakarta.annotation.PostConstruct;
import org.sunyaxing.transflow.engine.processors.*;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessorConfig {

    private final NodeProcessorFactory factory;

    public ProcessorConfig(NodeProcessorFactory factory) {
        this.factory = factory;
    }

    @PostConstruct
    public void registerProcessors() {
        // INPUT
        factory.register("TXT-INPUT", TxtInputProcessor::new);
        factory.register("KAFKA-CONSUMER", KafkaConsumerProcessor::new);
        factory.register("HTTP-SERVER", HttpServerProcessor::new);
        factory.register("SYSLOG-INPUT", SyslogInputProcessor::new);
        factory.register("FILE", FileProcessor::new);
        factory.register("DIR", DirProcessor::new);

        // MID
        factory.register("GROOVY", GroovyProcessor::new);
        factory.register("TO-JSON", ToJsonProcessor::new);
        factory.register("IF-ELSE", IfElseProcessor::new);

        // OUTPUT
        factory.register("CONSOLE", ConsoleProcessor::new);
        factory.register("HTTP-CLIENT", HttpClientProcessor::new);
        factory.register("HTTP-BACK", HttpBackProcessor::new);
        factory.register("KAFKA-PRODUCER", KafkaProducerProcessor::new);
        factory.register("SYSLOG-OUTPUT", SyslogOutputProcessor::new);
        factory.register("TXT-OUT", TxtOutProcessor::new);
    }
}
