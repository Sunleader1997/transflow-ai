package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class TxtOutProcessor extends AbstractNodeProcessor {

    private volatile String latest;

    @Override
    public String getType() { return "TXT-OUT"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("", "实时展示处理后的文本数据，支持一键复制", "hint", "", "", null, null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        latest = null;
        initInputSink();
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        return Mono.fromCallable(() -> {
            latest = data instanceof String s ? s : JSON.toJSONString(data);
            return data;
        });
    }

    public List<String> getOutput() {
        return latest != null ? List.of(latest) : List.of();
    }

    @Override
    public void destroy() {
        latest = null;
        destroyInputSink();
    }
}
