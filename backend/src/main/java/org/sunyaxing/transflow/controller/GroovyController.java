package org.sunyaxing.transflow.controller;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/groovy")
public class GroovyController {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/compile")
    public Mono<ResponseEntity<Map<String, Object>>> compileScript(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            String script = body.getOrDefault("script", "");
            Map<String, Object> result = new HashMap<>();

            if (script == null || script.isBlank()) {
                result.put("success", true);
                return ResponseEntity.ok(result);
            }

            GroovyShell shell = new GroovyShell();

            // Step 1: syntax check via parse()
            try {
                shell.parse(script);
            } catch (CompilationFailedException e) {
                result.put("success", false);
                result.put("message", "语法错误: " + e.getMessage());
                return ResponseEntity.ok(result);
            }

            // Step 2: dry-run with mock data to catch runtime errors
            // (undefined variables, missing methods, etc.)
            Future<?> future = executor.submit(() -> {
                GroovyShell runShell = new GroovyShell();
                runShell.setVariable("data", new HashMap<>());
                runShell.setVariable("requestId", "test-request-id");
                runShell.evaluate(script);
            });

            try {
                future.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                result.put("success", false);
                result.put("message", "脚本执行超时(>3秒)，可能存在死循环");
                return ResponseEntity.ok(result);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                result.put("success", false);
                result.put("message", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                return ResponseEntity.ok(result);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                result.put("success", false);
                result.put("message", "校验被中断");
                return ResponseEntity.ok(result);
            }

            result.put("success", true);
            return ResponseEntity.ok(result);
        });
    }
}
