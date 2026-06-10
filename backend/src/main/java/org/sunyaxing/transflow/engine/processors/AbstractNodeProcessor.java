package org.sunyaxing.transflow.engine.processors;

import org.sunyaxing.transflow.model.NodeStatus;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 处理器基类：管理 inputSink、targets、NodeStatus 等节点自身资源。
 * 生命周期随 init() 创建、destroy() 销毁。
 */
public abstract class AbstractNodeProcessor implements NodeProcessor {

    private Sinks.Many<Object> inputSink;
    private final List<String> targets = new CopyOnWriteArrayList<>();
    private final NodeStatus status = new NodeStatus();

    protected void initInputSink() {
        this.inputSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
    }

    protected void destroyInputSink() {
        if (inputSink != null) inputSink.tryEmitComplete();
    }

    @Override
    public void destroy() {
        updateStatus("STOPPED", null);
        destroyInputSink();
    }

    @Override
    public Sinks.Many<Object> inputSink() { return inputSink; }

    @Override
    public List<String> targets() { return targets; }

    @Override
    public void setTargets(List<String> targets) {
        this.targets.clear();
        this.targets.addAll(targets);
    }

    @Override
    public NodeStatus status() { return status; }

    @Override
    public void updateStatus(String state, String message) {
        status.setState(state);
        if (message != null) status.setMessage(message);
    }

    @Override
    public void incrementRec() {
        status.setRecNumb(status.getRecNumb() + 1);
    }

    @Override
    public void incrementSend() {
        status.setSendNumb(status.getSendNumb() + 1);
    }
}
