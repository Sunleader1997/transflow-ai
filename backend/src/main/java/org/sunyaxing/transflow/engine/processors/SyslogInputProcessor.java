package org.sunyaxing.transflow.engine.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyslogInputProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyslogInputProcessor.class);
    private static final int BUFFER_SIZE = 4096;

    private int port;
    private DatagramSocket socket;
    private Sinks.Many<Object> dataSink;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public String getType() { return "SYSLOG-INPUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("port", "监听端口", "number", "514", "514", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        return Mono.fromRunnable(() -> {
            this.port = Integer.parseInt(config.getOrDefault("port", "514").toString());
            this.dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

            try {
                this.socket = new DatagramSocket(port);
                this.running.set(true);

                this.receiverThread = new Thread(() -> {
                    byte[] buf = new byte[BUFFER_SIZE];
                    log.info("[SyslogInput] Listening on UDP port {}", port);
                    while (running.get() && !socket.isClosed()) {
                        try {
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            socket.receive(packet);
                            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                            String sender = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                            log.debug("[SyslogInput] Received from {}: {}", sender, message);

                            Map<String, Object> syslogEvent = Map.of(
                                "message", message.trim(),
                                "source", sender,
                                "timestamp", System.currentTimeMillis()
                            );
                            dataSink.tryEmitNext(syslogEvent);
                        } catch (Exception e) {
                            if (running.get()) {
                                log.error("[SyslogInput] Error receiving packet: {}", e.getMessage(), e);
                            }
                        }
                    }
                }, "syslog-input-" + nodeId);
                this.receiverThread.setDaemon(true);
                this.receiverThread.start();
            } catch (Exception e) {
                log.error("[SyslogInput] Failed to bind UDP port {}: {}", port, e.getMessage(), e);
                throw new RuntimeException("Syslog UDP bind failed on port " + port, e);
            }
        });
    }

    @Override
    public Mono<Object> process(Object data) {
        if (data != null) {
            return Mono.justOrEmpty(data);
        }
        return Mono.from(dataSink.asFlux());
    }

    @Override
    public void destroy() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
        log.info("[SyslogInput] Stopped, port {}", port);
    }
}
