# Reactor 速查手册 (基于 TransFlow 实践)

> 本文档结合 `transflow-ai` 代码库中的实际用法，解释 Project Reactor 的核心概念和操作符。

---

## 1. 两大核心类型

| 类型 | 含义 | 类比 | 本项目中代表 |
|------|------|------|-------------|
| `Mono<T>` | 0 或 1 个元素 | `Optional<T>` | 单次处理结果，如 `process(Object data)` 的返回值 |
| `Flux<T>` | 0 到 N 个元素 | `Stream<T>` | 持续数据流，如 `dataSink.asFlux()` |

### 为什么用它们替代同步代码？

响应式编程的核心是**声明式** + **非阻塞**：你描述数据如何流动、转换、处理错误，Reactor 在订阅时编排执行，不会阻塞调用线程。

---

## 2. 创建数据源

### Mono.empty() — 空结果

```java
// ToJsonProcessor.java:23
public Mono<Void> init(...) {
    return Mono.empty();  // 没有数据要返回，只是完成初始化
}
```

用于不需要返回值的场景，如 `init()` 完成配置加载。

### Mono.just(T) / Mono.justOrEmpty(T)

```java
// TxtInputProcessor.java:45
if (data != null) {
    return Mono.just(data);        // 有数据，包装为 Mono
}

// HttpClientProcessor.java:37
return Mono.justOrEmpty(data);     // data 可能为 null，为 null 时返回 Mono.empty()
```

- `just()` — 包装确定的非 null 值
- `justOrEmpty()` — 包装可能为 null 的值，null 时等同于 `Mono.empty()`

### Mono.fromCallable(() -> { ... })

```java
// ToJsonProcessor.java:30
return Mono.fromCallable(() -> {
    if (data instanceof String s) {
        try { return JSON.parse(s); }
        catch (Exception e) { return Map.of("value", s); }
    }
    return JSON.toJSON(data);
});
```

**最常用**。将阻塞的同步代码（如 JSON 解析、Groovy 脚本执行）包装为 `Mono`，Reactor 在合适的线程上执行它。

### Mono.fromRunnable(() -> { ... })

```java
// ConsoleProcessor.java:35
return Mono.fromRunnable(() -> {
    String output = data instanceof String s ? s : JSON.toJSONString(data);
    log.info("{} {}", prefix, output);
}).then(Mono.justOrEmpty(data));
```

执行一个无返回值的操作（如打印日志），然后用 `.then()` 链式返回另一个 `Mono`。

### Flux.fromArray() / Flux.fromIterable()

```java
// TxtInputProcessor.java:31
Flux.fromArray(text.split("\\n"))
    .map(String::trim)
    .filter(s -> !s.isEmpty())
    .cast(Object.class)
    .subscribe(dataSink::tryEmitNext);
```

将数组/集合转为 `Flux`，每个元素逐一通过操作符链。

---

## 3. 转换与链式操作

### map — 同步转换

```java
// HttpClientProcessor.java:47
responseMono = client.get()
    .responseSingle((res, bytes) -> bytes.asString().map(b -> {
        try { return JSON.parse(b); }
        catch (Exception e) { return Map.of("raw", (Object) b); }
    }));
```

对流的每个元素做**同步转换**，如字符串转 JSON。

### flatMap — 异步展平

```java
// FlowEngine.java:90
sink.asFlux()
    .flatMap(data -> {
        NodeProcessor proc = processors.get(node.getId());
        return proc.process(data);  // process 返回 Mono<Object>
    })
    .subscribe(result -> { ... });
```

核心理解：`flatMap` 将每个元素映射为**一个新的 Publisher**（Mono/Flux），然后把它们**展平合并**为一个流。

对比：

```
map:      data -> Mono<X>  →  Flux<Mono<X>>  (嵌套)
flatMap:  data -> Mono<X>  →  Flux<X>        (展平)
```

### filter — 条件过滤

```java
// TxtInputProcessor.java:33
.filter(s -> !s.isEmpty())  // 过滤空行
```

### cast — 类型转换

```java
// TxtInputProcessor.java:34
.cast(Object.class)  // String -> Object
```

### doOnXxx — 副作用（不修改数据）

```java
// FlowEngine.java:95
.doOnSuccess(result -> incrementSend(node.getId()))

// FlowEngine.java:137
.doOnError(err -> log.error("Input node {} error", nodeId, err))

// TxtInputProcessor.java:35
.doOnComplete(() -> dataSink.tryEmitComplete())
```

- `doOnSuccess` — 成功时执行
- `doOnError` — 出错时执行
- `doOnComplete` — 流完成时执行
- `doOnNext` — 每个元素到达时执行

它们**不改变流中的数据**，只用于日志、计数、状态更新等副作用。

---

## 4. 错误处理

### onErrorResume — 替代错误

```java
// FlowEngine.java:96
.onErrorResume(err -> {
    log.error("Error in node {}: {}", node.getId(), err.getMessage());
    updateStatus(node.getId(), "ERROR", err.getMessage());
    return Mono.empty();  // 出错时返回空，流继续
});

// HttpClientProcessor.java:65
.onErrorResume(err -> Mono.just(Map.of("error", (Object) err.getMessage())));
```

**最常用**的错误恢复方式。当上游出错时，用另一个 `Mono` 替代，避免流终止。

---

## 5. Sinks — 程序化发射数据

`Sinks` 是**热流**（Hot Publisher）：数据由外部事件驱动推入，所有订阅者共享同一流。

### Sinks.Many — 多值发射器

```java
// FlowEngine.java:65
Sinks.many().multicast().onBackpressureBuffer(256, false)
```

| 模式 | 含义 |
|------|------|
| `multicast` | 多播：多个订阅者共享同一个流 |
| `onBackpressureBuffer(256)` | 背压时缓冲 256 个元素 |
| `false` | 不保留历史元素给后续订阅者 |

操作：

```java
dataSink.tryEmitNext(envelope);     // 发射一个元素
dataSink.tryEmitComplete();         // 标记完成
dataSink.tryEmitError(e);           // 发射错误
```

**本项目中典型用法**：

```java
// HttpServerProcessor.java:17,34,44
private Sinks.Many<Object> dataSink;

dataSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
dataSink.tryEmitNext(envelope);     // HTTP 请求到达时推入数据

// process() 中暴露为 Flux
public Mono<Object> process(Object data) {
    return Mono.from(dataSink.asFlux());  // 外部订阅，消费推入的数据
}
```

### Sinks.One — 单值发射器

```java
// HttpRequestContext.java:29
public static class PendingResponse {
    private final Sinks.One<String> sink = Sinks.one();

    public void complete(String body) {
        sink.tryEmitValue(body);   // 发射唯一值
    }

    public void error(String message) {
        sink.tryEmitError(new RuntimeException(message));
    }

    public Mono<String> asMono() {
        return sink.asMono();      // 转为 Mono<String>
    }
}
```

用于需要等待异步响应的场景（如 HTTP 请求-响应配对）。

---

## 6. 调度与线程

### subscribeOn — 指定执行线程

```java
// FlowEngine.java:117
Flux.defer(() -> processor.process(null))
    .subscribeOn(Schedulers.boundedElastic())  // 在弹性线程池执行
    .subscribe();
```

- `Schedulers.boundedElastic()` — 弹性线程池，适合 I/O 或阻塞操作，线程数自动扩缩
- `subscribeOn` 影响**整个流**的执行线程（包括数据源创建）

### Mono.defer — 惰性创建

```java
// WebConfig.java:48
.switchIfEmpty(Mono.defer(() -> {
    // 只有在 switchIfEmpty 触发时才执行
    return Mono.just(INDEX);
}));
```

`Mono.defer()` / `Flux.defer()` 确保**每次订阅时**才执行 supplier，而不是在组装流时就执行。

对比 `Flux.defer` 和直接调用：

```java
// ❌ eager：process(null) 立即执行，结果被缓存
Flux flux = processor.process(null);

// ✅ defer：每次 subscribe() 才重新调用 process(null)
Flux flux = Flux.defer(() -> processor.process(null));
```

---

## 7. 订阅与生命周期

### .subscribe() — 启动流

```java
// FlowEngineManager.java:31
engine.build(...)
    .doOnSuccess(v -> engine.start())
    .subscribe();  // 触发构建流程

// TxtInputProcessor.java:36
Flux.fromArray(text.split("\\n"))
    ...
    .subscribe(dataSink::tryEmitNext);  // 逐行推入 sink
```

没有 `subscribe()`，流不会执行。这是 Reactor **惰性**的核心：组装操作符链只是声明，订阅才真正触发执行。

### Disposable — 取消订阅

```java
// FlowEngine.java:12,32
import reactor.core.Disposable;

private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();

Disposable d = sink.asFlux().flatMap(...).subscribe(...);
subscriptions.add(d);

// 停止时取消
for (Disposable d : subscriptions) {
    if (!d.isDisposed()) d.dispose();
}
```

`subscribe()` 返回 `Disposable`，用于**取消订阅**、释放资源。

---

## 8. 组合操作

### .then() — 完成后接另一个 Mono

```java
// FlowEngine.java:72
return Flux.fromIterable(nodes)
    .concatMap(node -> { ... })
    .then();  // 等所有节点创建完成，返回 Mono<Void>

// FlowDataController.java:29
Mono.fromRunnable(() -> { ... })
    .then(Mono.just(ResponseEntity.ok(Map.of("status", "ok"))));
```

### .switchIfEmpty() — 为空时切换

```java
// WebConfig.java:48
.switchIfEmpty(Mono.defer(() -> Mono.just(INDEX)));
```

当前 Mono 为空时，切换到另一个 Mono。

---

## 9. 冷流 vs 热流

| 特性 | 冷流 (Cold) | 热流 (Hot) |
|------|------------|-----------|
| 数据源 | 每次订阅独立创建 | 共享同一个数据源 |
| 数据生产 | 订阅驱动 | 事件驱动（外部推入） |
| 代表 | `Mono.fromCallable()`、`Flux.fromArray()` | `Sinks.Many`、Kafka Receiver |
| 类比 | 播放本地视频（每次从头播） | 看直播（加入时从当前画面开始） |

**本项目中的体现**：

- `TxtInputProcessor` 是**冷流**：`Flux.fromArray(text.split("\n"))`，每次订阅都从第一行开始
- `HttpServerProcessor` 是**热流**：`dataSink.asFlux()`，HTTP 请求到达时数据被实时推入，订阅者只能收到订阅之后的数据

---

## 10. 快速决策表

| 场景 | 操作符/方法 |
|------|------------|
| 有值要返回 | `Mono.just(value)` |
| 可能为 null | `Mono.justOrEmpty(value)` |
| 无返回值 | `Mono.empty()` |
| 阻塞操作（JSON 解析、脚本） | `Mono.fromCallable(() -> { ... })` |
| 执行副作用（日志） | `Mono.fromRunnable(() -> { ... })` |
| 同步转换 | `.map(x -> ...)` |
| 异步转换（返回 Mono） | `.flatMap(x -> ...)` |
| 出错返回默认值 | `.onErrorResume(e -> Mono.just(default))` |
| 出错丢弃 | `.onErrorResume(e -> Mono.empty())` |
| 过滤 | `.filter(condition)` |
| 完成后接另一个 | `.then(Mono.just(next))` |
| 为空时切换 | `.switchIfEmpty(Mono.just(alt))` |
| 程序推数据 | `Sinks.many().multicast()` |
| 等待异步响应 | `Sinks.one()` |
| 阻塞操作放弹性线程 | `.subscribeOn(Schedulers.boundedElastic())` |

---

---

## 11. 架构实践：FlowEngine 如何用 Reactor 串联 DAG

下面以 `HTTP-SERVER -> GROOVY -> HTTP-CLIENT` 为例，说明 FlowEngine 如何将配置化的有向图编译为反应式数据管道。

### 11.1 核心数据结构

```
adjacency:    节点 ID → [下游节点 ID列表]        (DAG 拓扑)
processors:   节点 ID → NodeProcessor 实例       (业务逻辑)
sinks:        节点 ID → Sinks.Many<Object>       (节点间通信缓冲区)
statuses:     节点 ID → NodeStatus               (运行状态)
```

每个节点的 `Sinks.Many` 是它的**收件箱**——上游通过 `tryEmitNext()` 非阻塞投递，下游通过 `asFlux()` 订阅消费。

### 11.2 三个阶段

**build() —— 构建**

```java
// 1. 解析 Edge 构建邻接表
for (Edge edge : edges) {
    adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
             .add(edge.getTarget());
}

// 2. 为每个节点创建 Processor 并 init，同时创建 Sink
Flux.fromIterable(nodes)
    .concatMap(node -> {
        sinks.put(node.getId(),
            Sinks.many().multicast().onBackpressureBuffer(256, false));
        return factory.create(node)  // 调用 processor.init()
            .doOnSuccess(proc -> processors.put(node.getId(), proc));
    })
    .then();
```

**start() —— 启动**

- **INPUT 节点**：订阅 `processor.process(null)`，数据产生后 `tryEmitNext` 到下游 sinks
- **非 INPUT 节点**：订阅自身的 `sink.asFlux()`，数据到达后调用 `processor.process()`，结果再 `tryEmitNext` 到下游

**runtime —— 运行**：数据通过 `Sink → Flux → process() → Sink` 在节点间流转。

### 11.3 一条数据的完整旅程

```
外部 HTTP POST → 8888/api/data
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│  HttpServerProcessor (INPUT)                            │
│  ├─ init: 启动 netty HttpServer + 内部 dataSink        │
│  ├─ route 收到请求:                                      │
│  │   String requestId = HttpRequestContext.create()      │
│  │   Map envelope = {api, method, body, requestId}       │
│  │   dataSink.tryEmitNext(envelope)  // 推入内部流        │
│  │   return pending.asMono()  // 挂起，等待下游回填响应   │
│  └─ process(null): Mono.from(dataSink.asFlux())         │
└────────────────┬────────────────────────────────────────┘
                 │ envelope
                 ▼
┌─────────────────────────────────────────────────────────┐
│  FlowEngine.startInputNode()                            │
│  Flux.defer(() -> processor.process(null))              │
│      .subscribeOn(Schedulers.boundedElastic())          │
│      .flatMap(data -> {                                  │
│          for (targetId : targets) {                      │
│              sinks.get(targetId).tryEmitNext(data);      │
│          }                                               │
│      }).subscribe();                                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  GROOVY 节点 (MID)                                      │
│  sink.asFlux()                                          │
│      .flatMap(data -> groovyProc.process(data))         │
│      .subscribe(result -> {                              │
│          for (targetId : targets) {                      │
│              sinks.get(targetId).tryEmitNext(result);    │
│          }                                               │
│      });                                                 │
│                                                         │
│  GroovyProcessor.process():                             │
│  Mono.fromCallable(() -> {                              │
│      GroovyShell shell = new GroovyShell();             │
│      shell.setVariable("data", data);                   │
│      return JSON.toJSON(shell.evaluate(script));        │
│  })                                                     │
└────────────────┬────────────────────────────────────────┘
                 │ result (脚本返回值)
                 ▼
┌─────────────────────────────────────────────────────────┐
│  HTTP-CLIENT 节点 (OUTPUT)                              │
│  sink.asFlux()                                          │
│      .flatMap(data -> httpClientProc.process(data))     │
│      .subscribe();  // 无下游，流终止                   │
│                                                         │
│  HttpClientProcessor.process():                         │
│  HttpClient.create()                                    │
│      .baseUrl(url)                                      │
│      .post()                                            │
│      .send((req, out) -> out.sendString(Mono.just(body)))│
│      .responseSingle((res, bytes) -> bytes.asString())  │
└─────────────────────────────────────────────────────────┘
```

### 11.4 关键设计模式

**① Sink 作为节点间解耦缓冲区**

```java
// FlowEngine.java:65
sinks.put(nodeId, Sinks.many().multicast().onBackpressureBuffer(256, false));
```

- `multicast`：多播，多个订阅者共享（当前每个 sink 一个订阅者）
- `onBackpressureBuffer(256)`：背压时缓冲 256 条，防止快生产者压垮慢消费者
- 节点之间完全解耦：上游不需要知道下游是谁，只管 `tryEmitNext`

**② 异步 HTTP 请求-响应挂起**

```java
// HttpServerProcessor 内部
String requestId = HttpRequestContext.createPendingResponse();  // Sinks.One
Map envelope = buildEnvelope(..., requestId);
dataSink.tryEmitNext(envelope);     // 数据推入流
return pending.asMono();            // 挂起，等待回填

// HttpBackProcessor 中（链路末端）
HttpRequestContext.getPending(requestId).complete(responseBody);  // 回填响应
```

用 `Sinks.One` 把同步的请求-响应模型桥接到反应式流上——请求进来后**不立即响应**，数据在流中流转，最终由下游通过 `HttpRequestContext` 回填。

**③ 错误隔离**

```java
// FlowEngine.java:96
.onErrorResume(err -> {
    log.error("Error in node {}: {}", node.getId(), err.getMessage());
    updateStatus(node.getId(), "ERROR", err.getMessage());
    return Mono.empty();  // 出错返回空，当前数据被丢弃，流继续
})
```

单节点处理失败不会影响整条链路，只是丢弃当前数据并记录状态。

### 11.5 为什么是 Reactor 而不是线程池？

| 方案 | 问题 | Reactor 解决方式 |
|------|------|-----------------|
| 线程池 + 阻塞队列 | 线程数受限，I/O 阻塞浪费线程 | `Schedulers.boundedElastic()` + 非阻塞 I/O |
| 回调地狱 | 代码嵌套深，难以维护 | 声明式链式操作符 `.flatMap().map().subscribe()` |
| 手动背压 | 消费者跟不上时内存溢出 | `onBackpressureBuffer(256)` 自动缓冲 |
| 错误处理分散 | try-catch 散落在各处 | `.onErrorResume()` 统一在流上处理 |

## 参考

- [Project Reactor 官方文档](https://projectreactor.io/docs/core/release/reference/)
- [Reactor 操作符速查](https://projectreactor.io/docs/core/release/reference/#which-operator)
