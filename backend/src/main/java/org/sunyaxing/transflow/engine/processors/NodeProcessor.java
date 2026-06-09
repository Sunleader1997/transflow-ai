package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeParam;
import org.sunyaxing.transflow.model.NodeStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

public interface NodeProcessor {

    /** Unique type identifier matching node.type */
    String getType();

    /** Configurable parameters, drives the frontend dynamic form */
    List<NodeParam> configParams();

    /** Initialize with configuration */
    Mono<Void> init(String nodeId, Map<String, Object> config);

    /** Process data, returning processed result or empty to drop */
    Mono<Object> process(Object data);

    /** Shutdown and release resources */
    void destroy();

    // ---- 节点自身资源（由 AbstractNodeProcessor 提供默认实现）----

    /** 本节点的输入 sink（收件箱），INPUT 类返回 null */
    Sinks.Many<Object> inputSink();

    /** 本节点的下游目标节点 ID 列表 */
    List<String> targets();

    /** 设置下游目标（由 FlowEngine 在 build/start 时调用） */
    void setTargets(List<String> targets);

    /** 本节点的运行状态 */
    NodeStatus status();

    /** 更新状态 */
    void updateStatus(String state, String message);

    /** 接收计数 +1 */
    void incrementRec();

    /** 发送计数 +1 */
    void incrementSend();

    /**
     * 本节点的输出流，供 FlowEngine 订阅并向下游分发。
     * 默认实现：订阅输入 sink，经过 process() 处理后输出。
     * INPUT 类处理器需要覆写此方法，返回内部数据源。
     */
    default Flux<Object> output() {
        return inputSink().asFlux()
            .flatMap(data -> process(data)
                .onErrorResume(err -> Mono.empty()));
    }
}
