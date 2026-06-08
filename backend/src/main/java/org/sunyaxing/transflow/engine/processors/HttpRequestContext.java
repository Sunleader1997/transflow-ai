package org.sunyaxing.transflow.engine.processors;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HttpRequestContext {

    private static final Map<String, PendingResponse> pending = new ConcurrentHashMap<>();

    public static String createPendingResponse() {
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new PendingResponse());
        return requestId;
    }

    public static PendingResponse getPending(String requestId) {
        return pending.get(requestId);
    }

    public static PendingResponse removePending(String requestId) {
        return pending.remove(requestId);
    }

    public static class PendingResponse {
        private final Sinks.One<String> sink = Sinks.one();
        private volatile boolean completed = false;

        public void complete(String body) {
            if (!completed) {
                completed = true;
                sink.tryEmitValue(body);
            }
        }

        public void error(String message) {
            if (!completed) {
                completed = true;
                sink.tryEmitError(new RuntimeException(message));
            }
        }

        public Mono<String> asMono() {
            return sink.asMono();
        }
    }
}
