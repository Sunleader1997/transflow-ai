package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SyslogOutputProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyslogOutputProcessor.class);
    private static final DateTimeFormatter SYSLOG_TIME_FMT = DateTimeFormatter.ofPattern("MMM dd HH:mm:ss");

    private String host;
    private int port;
    private String nodeId;
    private DatagramSocket socket;
    private InetAddress address;

    @Override
    public String getType() { return "SYSLOG-OUTPUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("host", "目标主机", "text", "localhost", "localhost", null, null),
            new NodeParam("port", "目标端口", "number", "514", "514", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        return Mono.fromRunnable(() -> {
            this.nodeId = nodeId;
            this.host = config.getOrDefault("host", "localhost").toString();
            this.port = Integer.parseInt(config.getOrDefault("port", "514").toString());
            try {
                this.address = InetAddress.getByName(this.host);
                this.socket = new DatagramSocket();
                log.info("[SyslogOutput] Initialized, target={}:{}", this.host, this.port);
            } catch (Exception e) {
                log.error("[SyslogOutput] Failed to init UDP socket for {}:{}: {}", this.host, this.port, e.getMessage(), e);
                throw new RuntimeException("Syslog UDP init failed", e);
            }
        });
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromRunnable(() -> {
            String value = data instanceof String s ? s : JSON.toJSONString(data);
            String syslogMsg = buildSyslogMessage(value);
            try {
                byte[] bytes = syslogMsg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
                socket.send(packet);
                log.debug("[SyslogOutput] Sent {} bytes to {}:{}", bytes.length, host, port);
            } catch (Exception e) {
                log.error("[SyslogOutput] Failed to send to {}:{}: {}", host, port, e.getMessage(), e);
            }
        }).then(Mono.justOrEmpty(data));
    }

    @Override
    public void destroy() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("[SyslogOutput] Socket closed for {}:{}", host, port);
        }
    }

    private String buildSyslogMessage(String message) {
        // RFC 3164: <priority>timestamp hostname app-name: msg
        // priority = facility*8 + severity, facility=1 (user), severity=6 (info) => 14
        String timestamp = LocalDateTime.now().format(SYSLOG_TIME_FMT);
        return "<14>" + timestamp + " " + host + " transflow[" + nodeId + "]: " + message;
    }
}
