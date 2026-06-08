package org.sunyaxing.transflow.engine.processors;

import com.alibaba.fastjson2.JSON;
import groovy.lang.GroovyShell;
import org.sunyaxing.transflow.model.NodeParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class GroovyProcessor implements NodeProcessor {

    private String script;

    @Override
    public String getType() { return "GROOVY"; }

    @Override
    public List<NodeParam> configParams() {
        return List.of(
            new NodeParam("script", "Groovy 脚本", "groovy", "",
                "def result = data\nreturn result", "使用 data 变量访问输入数据", null)
        );
    }

    @Override
    public Mono<Void> init(String nodeId, Map<String, Object> config) {
        this.script = (String) config.getOrDefault("script", "");
        return Mono.empty();
    }

    @Override
    public Mono<Object> process(Object data) {
        if (script == null || script.isBlank()) {
            return Mono.justOrEmpty(data);
        }
        return Mono.fromCallable(() -> {
            GroovyShell shell = new GroovyShell();
            shell.setVariable("data", data);
            Object result = shell.evaluate(script);
            // Convert Groovy GString/collections to standard Java/JSON types
            return JSON.toJSON(result);
        });
    }

    @Override
    public void destroy() {}
}
