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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

    private final String taskId;
    private final NodeProcessorFactory factory;
    private final Map<String, NodeProcessor> processors = new HashMap<>();
    private final Map<String, Node> nodeMap = new HashMap<>();
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

        // 先收集 edges → targets 映射（sourceId → [targetId, ...]）
        Map<String, List<String>> edgeMap = new HashMap<>();
        for (Edge edge : edges) {
            edgeMap.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
                    .add(edge.getTarget());
        }

        // 创建处理器，将 targets 绑定到处理器自身
        Map<String, List<String>> finalEdgeMap = edgeMap;
        return Flux.fromIterable(nodes)
            .concatMap(node -> {
                nodeMap.put(node.getId(), node);
                return factory.create(node)
                    .doOnSuccess(processor -> {
                        processor.setTargets(finalEdgeMap.getOrDefault(node.getId(), List.of()));
                        processors.put(node.getId(), processor);
                    });
            })
            .then();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        log.info("FlowEngine started for task {}", taskId);

        // 第一步：为所有节点绑定输出流订阅（output → 推入下游 inputSink）并标记状态
        for (Node node : nodeMap.values()) {
            bindNode(node);
        }

        // 第二步：启动 INPUT 节点的数据生产
        for (Node node : nodeMap.values()) {
            if (node.getNodeType() == NodeType.INPUT) {
                startInputProducer(node.getId());
            }
        }
    }

    /**
     * 统一订阅模式：订阅节点的 output()，推入下游节点的 inputSink
     */
    private void bindNode(Node node) {
        NodeProcessor processor = processors.get(node.getId());
        if (processor == null) return;

        // 订阅所有节点的 output()，包括没有下游的 OUTPUT 节点
        // 不订阅 → inputSink 无消费者 → 上游背压阻塞 → 整条链路卡死
        Disposable d = processor.output()
            .subscribe(result -> {
                for (String targetId : processor.targets()) {
                    NodeProcessor targetProc = processors.get(targetId);
                    if (targetProc != null && targetProc.inputSink() != null) {
                        targetProc.inputSink().tryEmitNext(result);
                    }
                }
            },
            err -> {
                log.error("Node {} error: {}", node.getId(), err.getMessage());
                processor.updateStatus("ERROR", err.getMessage());
            });
        subscriptions.add(d);
    }

    /**
     * INPUT 节点数据生产：订阅 output() 流，推入下游节点的 inputSink
     */
    private void startInputProducer(String nodeId) {
        NodeProcessor processor = processors.get(nodeId);

        Disposable d = processor.output()
            .doOnNext(data -> processor.incrementRec())
            .subscribe(data -> {
                for (String targetId : processor.targets()) {
                    NodeProcessor targetProc = processors.get(targetId);
                    if (targetProc != null && targetProc.inputSink() != null) {
                        targetProc.inputSink().tryEmitNext(data);
                    }
                }
                processor.incrementSend();
            },
            err -> {
                log.error("Input node {} error: {}", nodeId, err.getMessage());
                processor.updateStatus("ERROR", err.getMessage());
            });
        subscriptions.add(d);
    }

    public void emit(String nodeId, Object data) {
        NodeProcessor processor = processors.get(nodeId);
        if (processor != null && processor.inputSink() != null) {
            processor.inputSink().tryEmitNext(data);
        }
    }

    public Map<String, NodeStatus> getNodeStatuses() {
        Map<String, NodeStatus> result = new HashMap<>();
        processors.forEach((id, proc) -> result.put(id, proc.status()));
        return result;
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
        log.info("FlowEngine stopped for task {}", taskId);
    }

    public NodeProcessor getProcessor(String nodeId) {
        return processors.get(nodeId);
    }

    public String getTaskId() { return taskId; }
}
