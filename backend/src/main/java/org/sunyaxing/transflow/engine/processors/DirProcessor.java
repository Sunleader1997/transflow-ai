package org.sunyaxing.transflow.engine.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirProcessor extends AbstractNodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(DirProcessor.class);

    private Path dirPath;
    private WatchService watchService;
    private Thread watchThread;
    private Sinks.Many<Object> dataSink;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public String getType() { return "DIR"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("path", "目录路径", "text", "", "/path/to/dir", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        return Mono.fromRunnable(() -> {
            String pathStr = (String) config.getOrDefault("path", "");
            if (pathStr.isBlank()) {
                throw new IllegalArgumentException("Directory path is required");
            }
            this.dirPath = Path.of(pathStr);
            if (!Files.isDirectory(dirPath)) {
                throw new IllegalArgumentException("Not a directory: " + dirPath);
            }
            this.dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
                dirPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            } catch (IOException e) {
                throw new RuntimeException("Cannot watch directory: " + dirPath, e);
            }
            running.set(true);

            watchThread = new Thread(() -> {
                log.info("[DirProcessor] Watching directory {}", dirPath);
                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        break;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        String eventType = event.kind().name();
                        dataSink.tryEmitNext(Map.of(
                            "dir", dirPath.toString(),
                            "file", changed.toString(),
                            "eventType", eventType,
                            "timestamp", System.currentTimeMillis()
                        ));
                    }
                    key.reset();
                }
            }, "dir-watch-" + nodeId);
            watchThread.setDaemon(true);
            watchThread.start();
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
    public Flux<Object> output() {
        return dataSink.asFlux();
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
        log.info("[DirProcessor] Stopped, dir={}", dirPath);
    }
}
