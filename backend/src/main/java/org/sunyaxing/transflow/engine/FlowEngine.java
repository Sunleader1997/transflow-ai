package org.sunyaxing.transflow.engine;

import org.sunyaxing.transflow.engine.processors.NodeProcessor;
import org.sunyaxing.transflow.engine.processors.NodeProcessorFactory;
import org.sunyaxing.transflow.model.Edge;
import org.sunyaxing.transflow.model.Node;
import org.sunyaxing.transflow.model.NodeStatus;
import org.sunyaxing.transflow.model.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

    private final String taskId;
    private final NodeProcessorFactory factory;
    private final Map<String, NodeProcessor> processors = new HashMap<>();
    private final Map<String, Node> nodeMap = new HashMap<>();
    private final Map<String, List<String>> adjacency = new HashMap<>();
    private final Map<String, Sinks.Many<Object>> sinks = new ConcurrentHashMap<>();
    private final Map<String, NodeStatus> statuses = new ConcurrentHashMap<>();
    private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public FlowEngine(String taskId, NodeProcessorFactory factory) {
        this.taskId = taskId;
        this.factory = factory;
    }

    public Mono<Void> build(List<Node> nodes, List<Edge> edges) {
        // Stop any running subscriptions first
        cancelSubscriptions();

        // Destroy existing processors
        processors.values().forEach(NodeProcessor::destroy);
        processors.clear();
        nodeMap.clear();
        adjacency.clear();

        // Complete old sinks and create fresh ones
        sinks.values().forEach(s -> s.tryEmitComplete());
        sinks.clear();

        // Build adjacency first (synchronous)
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
                    .add(edge.getTarget());
        }

        // Create processors sequentially to avoid concurrent HashMap writes
        return Flux.fromIterable(nodes)
            .concatMap(node -> {
                nodeMap.put(node.getId(), node);
                sinks.put(node.getId(),
                    Sinks.many().multicast().onBackpressureBuffer(256, false));
                NodeStatus status = node.getStatus() != null ? node.getStatus() : new NodeStatus();
                statuses.put(node.getId(), status);

                return factory.create(node)
                    .doOnSuccess(processor -> processors.put(node.getId(), processor));
            })
            .then();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        log.info("FlowEngine started for task {}", taskId);

        for (Node node : nodeMap.values()) {
            if (node.getNodeType() == NodeType.INPUT) {
                NodeProcessor processor = processors.get(node.getId());
                startInputNode(node.getId(), processor);
            }

            List<String> targets = adjacency.getOrDefault(node.getId(), List.of());
            Sinks.Many<Object> sink = sinks.get(node.getId());
            if (sink != null && !targets.isEmpty()) {
                Disposable d = sink.asFlux()
                    .flatMap(data -> {
                        NodeProcessor proc = processors.get(node.getId());
                        if (proc == null) return Mono.empty();
                        incrementRec(node.getId());
                        return proc.process(data)
                            .doOnSuccess(result -> incrementSend(node.getId()))
                            .onErrorResume(err -> {
                                log.error("Error in node {}: {}", node.getId(), err.getMessage());
                                updateStatus(node.getId(), "ERROR", err.getMessage());
                                return Mono.empty();
                            });
                    })
                    .subscribe(result -> {
                        for (String targetId : targets) {
                            Sinks.Many<Object> targetSink = sinks.get(targetId);
                            if (targetSink != null) {
                                targetSink.tryEmitNext(result);
                            }
                        }
                    });
                subscriptions.add(d);
            }
        }
    }

    private void startInputNode(String nodeId, NodeProcessor processor) {
        updateStatus(nodeId, "RUNNING", null);
        Disposable d = Flux.defer(() -> processor.process(null))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(data -> {
                incrementRec(nodeId);
                List<String> targets = adjacency.getOrDefault(nodeId, List.of());
                for (String targetId : targets) {
                    Sinks.Many<Object> targetSink = sinks.get(targetId);
                    if (targetSink != null) {
                        targetSink.tryEmitNext(data);
                    }
                    NodeProcessor targetProc = processors.get(targetId);
                    if (targetProc != null) {
                        targetProc.process(data)
                            .doOnSuccess(r -> incrementSend(targetId))
                            .subscribe();
                    }
                }
                incrementSend(nodeId);
                return Mono.empty();
            })
            .doOnError(err -> {
                log.error("Input node {} error: {}", nodeId, err.getMessage());
                updateStatus(nodeId, "ERROR", err.getMessage());
            })
            .subscribe();
        subscriptions.add(d);
    }

    public void emit(String nodeId, Object data) {
        Sinks.Many<Object> sink = sinks.get(nodeId);
        if (sink != null) {
            sink.tryEmitNext(data);
        }
    }

    public Map<String, NodeStatus> getNodeStatuses() {
        return new HashMap<>(statuses);
    }

    public NodeStatus getNodeStatus(String nodeId) {
        return statuses.getOrDefault(nodeId, new NodeStatus());
    }

    private void updateStatus(String nodeId, String state, String message) {
        NodeStatus status = statuses.computeIfAbsent(nodeId, k -> new NodeStatus());
        status.setState(state);
        if (message != null) status.setMessage(message);
    }

    private void incrementRec(String nodeId) {
        NodeStatus status = statuses.computeIfAbsent(nodeId, k -> new NodeStatus());
        status.setRecNumb(status.getRecNumb() + 1);
    }

    private void incrementSend(String nodeId) {
        NodeStatus status = statuses.computeIfAbsent(nodeId, k -> new NodeStatus());
        status.setSendNumb(status.getSendNumb() + 1);
    }

    private void cancelSubscriptions() {
        for (Disposable d : subscriptions) {
            if (!d.isDisposed()) {
                d.dispose();
            }
        }
        subscriptions.clear();
    }

    public synchronized void stop() {
        running = false;
        cancelSubscriptions();
        processors.values().forEach(NodeProcessor::destroy);
        processors.clear();
        sinks.values().forEach(s -> s.tryEmitComplete());
        sinks.clear();
        log.info("FlowEngine stopped for task {}", taskId);
    }

    public NodeProcessor getProcessor(String nodeId) {
        return processors.get(nodeId);
    }

    public String getTaskId() { return taskId; }
}
