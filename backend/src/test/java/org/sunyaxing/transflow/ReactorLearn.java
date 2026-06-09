package org.sunyaxing.transflow;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

/**
 * Reactor 学习演示：模拟 FlowEngine 的 DAG 数据流
 *
 * 场景：HTTP-SERVER(INPUT) -> TRANSFORMER(MID) -> CONSOLE(OUTPUT)
 *
 * 涵盖的 Reactor 核心概念：
 *   - Sinks.Many / Sinks.One   (热流，程序主动推数据)
 *   - Flux.subscribe()         (惰性执行，订阅才触发)
 *   - flatMap                  (异步展平)
 *   - Mono.fromCallable        (包装阻塞代码)
 *   - Schedulers.boundedElastic(弹性线程池)
 *   - doOnError / onErrorResume(错误恢复)
 *   - Disposable               (取消订阅)
 */
public class ReactorLearn {

    public static void main(String[] args) throws InterruptedException {
        // ============================================================
        // 1. 创建节点（对应 FlowEngine 中的 processors）
        // ============================================================
        DemoServer server = new DemoServer("http-server");
        Transformer transformer = new Transformer("groovy-transform");
        ConsoleConsumer consumer = new ConsoleConsumer("console-out");

        // ============================================================
        // 2. 建立订阅链（对应 FlowEngine 中的 adjacency + 订阅绑定）
        // ============================================================
        // transformer 订阅 server 的输出
        transformer.subscribe(server);
        // consumer 订阅 transformer 的输出
        consumer.subscribe(transformer);

        // ============================================================
        // 3. 模拟外部事件：HTTP 请求到达
        // ============================================================
        System.out.println("\n========== 第1条请求 ==========");
        server.push(Map.of("user", "alice", "action", "login", "ip", "192.168.1.1"));

        Thread.sleep(200);

        System.out.println("\n========== 第2条请求 ==========");
        server.push(Map.of("user", "bob", "action", "buy", "item", "book"));

        Thread.sleep(200);

        System.out.println("\n========== 第3条请求（触发错误） ==========");
        server.push(Map.of("triggerError", true));  // transformer 会故意抛异常

        // 等待异步处理完成
        Thread.sleep(800);

        // ============================================================
        // 4. 优雅停止（对应 FlowEngine.stop()）
        // ============================================================
        System.out.println("\n========== 停止 ==========");
        server.destroy();
        transformer.destroy();
        consumer.destroy();

        System.out.println("\n=== 演示结束 ===");
    }

    // ================================================================
    // DemoNode 接口：模拟 FlowEngine 中的 NodeProcessor
    // ================================================================
    interface DemoNode {
        /** 暴露本节点的输出流，供下游订阅 */
        Flux<Object> output();

        /** 处理单条数据，返回 Mono（异步） */
        Mono<Object> process(Object data);

        /** 订阅上游节点的输出 */
        void subscribe(DemoNode upstream);

        /** 停止并释放资源 */
        void destroy();
    }

    // ================================================================
    // INPUT 节点：模拟 HTTP-SERVER
    //
    // 核心模式：内部持有 Sinks.Many，外部事件（HTTP 请求）驱动 tryEmitNext
    //          process(null) 暴露为 Mono.from(dataSink.asFlux())
    // ================================================================
    static class DemoServer implements DemoNode {
        private final String id;
        private final Sinks.Many<Object> dataSink;

        public DemoServer(String id) {
            this.id = id;
            // multicast: 多播，多个订阅者共享
            // onBackpressureBuffer(256): 背压时缓冲 256 条
            this.dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
        }

        /** 模拟 HTTP 请求到达 */
        public void push(Object data) {
            System.out.printf("[%s] 收到 HTTP 请求: %s%n", id, data);
            // tryEmitNext: 非阻塞地将数据推入 Sink
            // 返回 EmitResult，可据此判断投递是否成功（FULL、FAILED 等）
            Sinks.EmitResult result = dataSink.tryEmitNext(data);
            if (result.isSuccess()) {
                System.out.printf("[%s] 数据已推入 Sink%n", id);
            } else {
                System.err.printf("[%s] 推入失败: %s%n", id, result);
            }
        }

        @Override
        public Flux<Object> output() {
            // 将 Sinks 转为 Flux，供下游订阅
            return dataSink.asFlux();
        }

        @Override
        public Mono<Object> process(Object data) {
            // INPUT 节点的 process 通常返回内部 dataSink 的流
            return Mono.from(dataSink.asFlux());
        }

        @Override
        public void subscribe(DemoNode upstream) {
            // INPUT 没有上游，无需订阅
        }

        @Override
        public void destroy() {
            // tryEmitComplete: 标记流正常结束，下游订阅者会收到 onComplete
            dataSink.tryEmitComplete();
            System.out.printf("[%s] 已关闭%n", id);
        }
    }

    // ================================================================
    // MID 节点：模拟 GROOVY / TO-JSON 等转换节点
    //
    // 核心模式：
    //   1. 订阅上游 output()
    //   2. 通过 flatMap 调用 process(data) 做异步转换
    //   3. 结果 tryEmitNext 到自己的 outputSink
    //   4. 下游再订阅本节点的 output()
    // ================================================================
    static class Transformer implements DemoNode {
        private final String id;
        private final Sinks.Many<Object> outputSink;
        private Disposable subscription;

        public Transformer(String id) {
            this.id = id;
            this.outputSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
        }

        @Override
        public void subscribe(DemoNode upstream) {
            // 订阅上游 Flux，这是整个链路启动的关键！
            // 没有 subscribe()，流不会执行（Reactor 的惰性特性）
            subscription = upstream.output()
                .doOnNext(data -> System.out.printf("[%s] 收到上游数据%n", id))
                .flatMap(data -> {
                    // flatMap: 将每个元素映射为 Mono/Flux，然后展平合并
                    // 这里 process() 返回 Mono，flatMap 将其展平
                    return process(data)
                        .doOnSuccess(r -> System.out.printf("[%s] 处理完成%n", id))
                        // 错误恢复：本节点出错不影响整条链路
                        .onErrorResume(err -> {
                            System.err.printf("[%s] 处理失败: %s%n", id, err.getMessage());
                            return Mono.empty();  // 返回空，丢弃这条数据
                        });
                })
                .subscribe(result -> {
                    // 处理结果推入自己的输出 Sink，供下游消费
                    outputSink.tryEmitNext(result);
                });
        }

        @Override
        public Mono<Object> process(Object data) {
            // Mono.fromCallable: 将阻塞/同步代码包装为 Mono
            // Schedulers.boundedElastic(): 在弹性线程池执行（适合 I/O 或脚本）
            return Mono.fromCallable(() -> {
                System.out.printf("[%s] 开始转换...%n", id);

                // 模拟错误场景
                if (data instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) data).get("triggerError"))) {
                    throw new RuntimeException("模拟转换异常");
                }

                // 模拟耗时处理（如 Groovy 脚本执行）
                Thread.sleep(100);

                Map<String, Object> result = new HashMap<>();
                if (data instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        result.put((String) e.getKey(), e.getValue());
                    }
                }
                result.put("_node", id);
                result.put("_transformed", true);
                result.put("_timestamp", System.currentTimeMillis());

                return (Object) result;
            }).subscribeOn(Schedulers.boundedElastic());
        }

        @Override
        public Flux<Object> output() {
            return outputSink.asFlux();
        }

        @Override
        public void destroy() {
            // dispose: 取消上游订阅，停止接收数据
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            outputSink.tryEmitComplete();
            System.out.printf("[%s] 已关闭%n", id);
        }
    }

    // ================================================================
    // OUTPUT 节点：模拟 CONSOLE / HTTP-CLIENT
    //
    // 核心模式：订阅上游，消费最终数据，无下游
    // ================================================================
    static class ConsoleConsumer implements DemoNode {
        private final String id;
        private Disposable subscription;

        public ConsoleConsumer(String id) {
            this.id = id;
        }

        @Override
        public void subscribe(DemoNode upstream) {
            subscription = upstream.output()
                .subscribe(
                    data -> System.out.printf("[%s] ★ 最终输出: %s%n", id, data),
                    err  -> System.err.printf("[%s] 消费错误: %s%n", id, err.getMessage()),
                    ()   -> System.out.printf("[%s] 上游已结束%n", id)
                );
        }

        @Override
        public Mono<Object> process(Object data) {
            return Mono.just(data);
        }

        @Override
        public Flux<Object> output() {
            // OUTPUT 节点没有下游，返回空流
            return Flux.empty();
        }

        @Override
        public void destroy() {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            System.out.printf("[%s] 已关闭%n", id);
        }
    }
}