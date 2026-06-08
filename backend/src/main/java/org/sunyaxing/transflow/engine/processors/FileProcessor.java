package org.sunyaxing.transflow.engine.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(FileProcessor.class);

    private Path filePath;
    private String mode;
    private Sinks.Many<Object> dataSink;
    private WatchService watchService;
    private Thread watchThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public String getType() { return "FILE"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("path", "文件路径", "text", "", "/path/to/file", null, null),
            new NodeParam("mode", "监听模式", "select", "TAIL", "TAIL", null, List.of("TAIL", "FULL"))
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        return Mono.fromRunnable(() -> {
            String pathStr = (String) config.getOrDefault("path", "");
            this.mode = (String) config.getOrDefault("mode", "TAIL");
            if (pathStr.isBlank()) {
                throw new IllegalArgumentException("File path is required");
            }
            this.filePath = Path.of(pathStr);
            this.dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);

            if ("FULL".equals(mode)) {
                readFullFile();
            } else {
                startTailWatch(nodeId);
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
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (dataSink != null) {
            dataSink.tryEmitComplete();
        }
        log.info("[FileProcessor] Stopped, path={}", filePath);
    }

    private void readFullFile() {
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                dataSink.tryEmitNext(Map.of(
                    "file", filePath.toString(),
                    "content", line,
                    "timestamp", System.currentTimeMillis()
                ));
            }
            dataSink.tryEmitComplete();
        } catch (IOException e) {
            log.error("[FileProcessor] Failed to read file {}: {}", filePath, e.getMessage(), e);
            throw new RuntimeException("Cannot read file: " + filePath, e);
        }
    }

    private void startTailWatch(String nodeId) {
        Path dir = filePath.getParent();
        String fileName = filePath.getFileName().toString();
        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            running.set(true);

            watchThread = new Thread(() -> {
                long lastPos = getFileSize();
                log.info("[FileProcessor] Tailing {} from offset {}", filePath, lastPos);
                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        break;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (fileName.equals(changed.toString())) {
                            try {
                                long newSize = getFileSize();
                                if (newSize > lastPos) {
                                    byte[] bytes = Files.readAllBytes(filePath);
                                    String newContent = new String(bytes, (int) lastPos, (int) (newSize - lastPos));
                                    for (String line : newContent.split("\n")) {
                                        if (!line.isBlank()) {
                                            dataSink.tryEmitNext(Map.of(
                                                "file", filePath.toString(),
                                                "content", line,
                                                "timestamp", System.currentTimeMillis()
                                            ));
                                        }
                                    }
                                    lastPos = newSize;
                                }
                            } catch (IOException e) {
                                log.error("[FileProcessor] Read error: {}", e.getMessage());
                            }
                        }
                    }
                    key.reset();
                }
            }, "file-tail-" + nodeId);
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            log.error("[FileProcessor] WatchService init failed: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot watch file: " + filePath, e);
        }
    }

    private long getFileSize() {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }
}
