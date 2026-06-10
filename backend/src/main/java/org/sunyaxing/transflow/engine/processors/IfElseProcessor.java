package org.sunyaxing.transflow.engine.processors;

import groovy.lang.GroovyShell;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class IfElseProcessor extends AbstractNodeProcessor {

    private String condition;

    @Override
    public String getType() { return "IF-ELSE"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("condition", "条件表达式", "groovy", "",
                "data.status == 1", "Groovy 布尔表达式，返回 true 放行，false 丢弃", null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        this.condition = (String) config.getOrDefault("condition", "");
        initInputSink();
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        if (condition == null || condition.isBlank()) {
            return Mono.justOrEmpty(data);
        }
        return Mono.fromCallable(() -> {
            GroovyShell shell = new GroovyShell();
            shell.setVariable("data", data);
            Object result = shell.evaluate(condition);
            if (result instanceof Boolean b && b) {
                return data;
            }
            return null;
        });
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
