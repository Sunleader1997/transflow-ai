# TransFlow 节点处理器详细参考

每个节点的 `config` 对象中的 key 与下表的 `field` 列一一对应。

---

## INPUT 类节点

### TXT-INPUT — 文本输入

将预配置的文本按行注入流程。也可通过 API 动态注入数据。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| text | textarea | `""` | 多行文本，每行一条数据，init 时按 `\n` 分割逐行发射 |

**config 示例:**
```json
{"text": "hello\nworld\nfoo bar"}
```

**动态注入**: 即使 text 为空，任务启动后仍可通过 `POST /api/flow-data/emit` 向该节点注入数据。

---

### KAFKA-CONSUMER — Kafka 消费者

从 Kafka 主题消费消息，每条消息自动 acknowledge。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| bootstrapServers | text | `localhost:9092` | Kafka 集群地址，多个用逗号分隔 |
| topic | text | `""` | 订阅的主题名（必填） |
| groupId | text | `transflow` | 消费者组 ID |

**config 示例:**
```json
{"bootstrapServers": "kafka1:9092,kafka2:9092", "topic": "user-events", "groupId": "transflow-consumer"}
```

**输出数据结构:**
```json
{
  "topic": "user-events",
  "partition": 0,
  "offset": 12345,
  "key": "user-123",
  "value": {"action": "login", "userId": "u001"},
  "timestamp": 1717800000000
}
```

`value` 字段会自动尝试 JSON 解析，失败则保留原始字符串。

---

### HTTP-SERVER — HTTP 服务端

启动一个 Netty HTTP 服务，接收 GET/POST 请求。每个请求生成一个 `requestId`，下游通过 HTTP-BACK 节点响应回去。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| port | number | `8888` | 监听端口 |
| path | text | `/api/data` | API 路径 |

**config 示例:**
```json
{"port": 9000, "path": "/webhook/order"}
```

**输出数据结构:**
```json
{
  "api": "/webhook/order",
  "method": "POST",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "body": {"orderId": "1001", "amount": 99.9}
}
```

- POST 请求: body 从请求体解析
- GET 请求: body 从 query string 解析

**与 HTTP-BACK 配合**: HTTP-SERVER 接收请求后不会立即响应，而是挂起等待。下游处理完成后，HTTP-BACK 通过 `requestId` 找到挂起的请求并返回响应。如果流程中没有 HTTP-BACK 节点，HTTP 请求会超时。

---

### SYSLOG-INPUT — Syslog 输入

监听 UDP 端口接收 RFC 3164 格式的 Syslog 消息。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| port | number | `514` | UDP 监听端口 |

**config 示例:**
```json
{"port": 1514}
```

**输出数据结构:**
```json
{
  "message": "<14>Jun  8 12:00:00 myhost app: user login success",
  "source": "192.168.1.100:54321",
  "timestamp": 1717800000000
}
```

---

### FILE — 文件监听

读取或实时监听文件内容变化。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| path | text | `""` | 文件路径（必填） |
| mode | select | `TAIL` | `TAIL` = 增量监听，`FULL` = 一次性全量读取 |

**config 示例:**
```json
{"path": "/var/log/nginx/access.log", "mode": "TAIL"}
```

**输出数据结构:**
```json
{
  "file": "/var/log/nginx/access.log",
  "content": "192.168.1.1 - - [08/Jun/2026:12:00:00 +0800] GET /api/users ...",
  "timestamp": 1717800000000
}
```

- TAIL 模式: 使用 WatchService 监听父目录的 ENTRY_MODIFY 事件，增量读取新增内容
- FULL 模式: 启动时一次性读取全部行，每行一条数据，读完后不再产生新数据

---

### DIR — 目录监听

监听目录下的文件创建、修改、删除事件。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| path | text | `""` | 目录路径（必填） |

**config 示例:**
```json
{"path": "/data/uploads"}
```

**输出数据结构:**
```json
{
  "dir": "/data/uploads",
  "file": "report.csv",
  "eventType": "ENTRY_CREATE",
  "timestamp": 1717800000000
}
```

`eventType` 取值: `ENTRY_CREATE`、`ENTRY_MODIFY`、`ENTRY_DELETE`

---

## MID 类节点

### GROOVY — Groovy 脚本转换

使用 Groovy 脚本对数据进行任意转换。脚本在 GroovyShell 中执行。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| script | groovy | `""` | Groovy 脚本，使用 `data` 变量访问输入数据 |

**config 示例:**
```json
{"script": "def result = [\"name\": data.user, \"action\": data.type]\nreturn result"}
```

**脚本变量:**
- `data` — 上游传入数据，类型可能是 Map、List、String 或其他

**常用脚本模式:**

```groovy
// 透传
return data

// 提取字段
return data.name

// 构造新结构
return ["userName": data.user, "timestamp": System.currentTimeMillis()]

// 列表转换
return data.items.collect { [id: it.id, upper: it.name?.toUpperCase()] }

// 过滤列表
return data.list.findAll { it.amount > 100 }

// 字符串处理
return ["clean": data.text?.trim()?.replaceAll("\\s+", " ")]

// 条件逻辑
if (data.type == "vip") {
    return ["level": "gold", "data": data]
}
return ["level": "silver", "data": data]
```

返回值经 `JSON.toJSON()` 自动转换，无需手动序列化。

---

### IF-ELSE — 条件分支

根据 Groovy 布尔表达式决定数据是否放行。表达式返回 `true` 时放行，`false` 时丢弃。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| condition | groovy | `""` | Groovy 布尔表达式 |

**config 示例:**
```json
{"condition": "data.status == \"active\""}
```

**常用条件表达式:**
```groovy
data.status == "active"
data.amount > 1000
data.type in ["order", "payment"]
data.name?.startsWith("VIP")
data.count > 0 && data.enabled == true
data.tags?.contains("important")
```

**连线注意**: IF-ELSE 节点有两个输出 handle：
- `sourceHandle: "true"` — 条件为 true 时数据流向
- `sourceHandle: "false"` — 条件为 false 时数据流向

如果只连一条边且不指定 handle，数据在条件为 true 时流向该边。

---

### TO-JSON — JSON 格式化

将任意数据格式化为 JSON 对象。

无配置参数。

**行为:**
- 输入为 String: 尝试 JSON.parse 解析，失败则包装为 `{"value": "原始字符串"}`
- 输入为其他类型: 调用 `JSON.toJSON()` 转换
- 输入为 null: 返回空

---

### CONSOLE — 控制台日志

将数据输出到后端控制台日志，数据原样透传给下游。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| prefix | text | `[TransFlow]` | 日志前缀 |

**config 示例:**
```json
{"prefix": "[OrderService]"}
```

输出格式: `[OrderService] {"orderId":"1001","amount":99.9}`

---

### HTTP-CLIENT — HTTP 客户端

将数据作为 JSON 发送到指定 HTTP 接口。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| url | text | `""` | 目标 URL（必填） |
| method | select | `POST` | HTTP 方法: POST / GET / PUT |

**config 示例:**
```json
{"url": "https://api.example.com/webhook", "method": "POST"}
```

**行为:**
- 数据自动序列化为 JSON 作为请求体
- 响应体自动尝试 JSON.parse，失败则包装为 `{"raw": "原始响应"}`
- 异常时返回 `{"error": "错误信息"}`
- 数据透传给下游（下游收到的是 HTTP-CLIENT 的响应，不是原始输入）

---

## OUTPUT 类节点

### HTTP-BACK — HTTP 响应回调

通过 Groovy 脚本构造响应体，将数据返回给 HTTP-SERVER 的调用方。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| script | code | `""` | Groovy 脚本，返回值作为 HTTP 响应体 |

**config 示例:**
```json
{"script": "return [\"code\": 200, \"message\": \"success\", \"data\": data]"}
```

**脚本变量:**
- `data` — 上游传入数据
- `requestId` — HTTP 请求 ID，用于追溯

**必须与 HTTP-SERVER 配对使用。** HTTP-SERVER 接收请求后挂起，HTTP-BACK 通过 `requestId` 找到挂起的请求并完成响应。

**脚本示例:**
```groovy
// 简单透传
return data

// 标准响应格式
return ["code": 200, "message": "ok", "data": data]

// 提取并转换
return ["orderId": data.body.orderId, "processed": true, "ts": System.currentTimeMillis()]

// 带 requestId 追溯
return ["requestId": requestId, "result": data]
```

如果脚本为空，直接将数据 JSON 序列化后返回。

---

### KAFKA-PRODUCER — Kafka 生产者

将数据发送到指定 Kafka 主题。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| bootstrapServers | text | `localhost:9092` | Kafka 集群地址 |
| topic | text | `""` | 目标主题（必填） |

**config 示例:**
```json
{"bootstrapServers": "kafka1:9092,kafka2:9092", "topic": "processed-events"}
```

**行为:**
- 数据自动 JSON 序列化后作为消息 value 发送
- 使用 `acks=all` 保证消息可靠性
- 发送成功后数据原样透传给下游

---

### SYSLOG-OUTPUT — Syslog 输出

将数据以 RFC 3164 格式通过 UDP 发送到目标主机。

| field | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| host | text | `localhost` | 目标主机地址 |
| port | number | `514` | 目标端口 |

**config 示例:**
```json
{"host": "syslog.internal", "port": 514}
```

**Syslog 格式:** `<14>{timestamp} {host} transflow[{nodeId}]: {message}`
- facility=1 (user), severity=6 (info), priority=14

数据原样透传给下游。

---

### TXT-OUT — 文本输出

将最新一条数据存储在内存中，供前端实时展示。无配置参数。

**行为:**
- 仅保留最新一条数据（覆盖式）
- 前端通过 `GET /api/flow-data/output/{nodeId}` 轮询获取
- 数据原样透传给下游
