---
name: transflow-api
description: |
  通过 TransFlow REST API 创建和管理数据流转任务。当用户提到创建数据处理流程、数据管道、ETL 任务、消息转发、日志采集、HTTP 网关、Kafka 消费/生产、Syslog 收发、文件监听、Groovy 脚本转换、条件路由等场景时使用此 Skill。即使用户只是说"帮我建个任务"或"我想把 Kafka 数据转发到 HTTP"，只要涉及 TransFlow 的节点编排，都应触发此 Skill。
---

# TransFlow API Skill

通过 TransFlow REST API 以编程方式创建、修改和管理数据流转任务。TransFlow 是一个可视化数据编排系统，节点通过连线组成处理链路，数据从 INPUT 节点流入，经过 MID 节点转换/过滤，最终从 OUTPUT 节点输出。

## 核心概念

- **Task（任务）**: 一个独立的数据处理单元，包含 name、description 和 flow
- **Flow（流程）**: 由 nodes（节点）和 edges（连线）组成的有向图
- **Node（节点）**: 数据处理单元，每个节点有唯一 id、类型（type）、配置（config）和位置（position）
- **Edge（连线）**: 节点间的数据流向，由 source → target 定义
- **节点创建后任务自动启动**，更新 flow 会自动重启引擎

## API 端点

基础路径: `/api`，所有端点返回 JSON。

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/tasks` | 创建任务（自动启动） |
| `GET` | `/api/tasks` | 列出所有任务 |
| `GET` | `/api/tasks/{id}` | 获取单个任务 |
| `PUT` | `/api/tasks/{id}` | 更新任务（自动重启流程） |
| `DELETE` | `/api/tasks/{id}` | 删除任务（自动停止） |

### 流程管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/flow/{taskId}` | 获取流程（含各节点 configParams） |
| `PUT` | `/api/flow/{taskId}` | 保存流程（自动重启引擎） |

### 数据操作

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/flow-data/emit` | 向指定节点注入数据 |
| `GET` | `/api/flow-data/output/{nodeId}` | 获取 TXT-OUT 节点输出 |
| `GET` | `/api/flow-data/node-statuses/{taskId}` | 获取节点运行状态 |

## 创建任务的标准流程

### 方式一：先创建任务，再保存流程

```bash
# 1. 创建任务
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"name": "任务名称", "description": "任务描述"}'
# 返回: {"id": "a1b2c3d4", "name": "...", ...}

# 2. 保存流程（用返回的 id）
curl -X PUT http://localhost:8080/api/flow/a1b2c3d4 \
  -H "Content-Type: application/json" \
  -d '{"nodes": [...], "edges": [...]}'
```

### 方式二：创建任务时直接附带流程

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "name": "任务名称",
    "description": "任务描述",
    "flow": {
      "nodes": [...],
      "edges": [...]
    }
  }'
```

## Flow 数据结构

```json
{
  "nodes": [
    {
      "id": "node_1",
      "type": "HTTP-SERVER",
      "label": "HTTP 接收",
      "position": {"x": 100, "y": 200},
      "config": {"port": 8888, "path": "/api/data"}
    },
    {
      "id": "node_2",
      "type": "GROOVY",
      "label": "数据转换",
      "position": {"x": 400, "y": 200},
      "config": {"script": "def result = data\nreturn result"}
    },
    {
      "id": "node_3",
      "type": "HTTP-BACK",
      "label": "响应回调",
      "position": {"x": 700, "y": 200},
      "config": {"script": "return [\"code\": 200, \"data\": data]"}
    }
  ],
  "edges": [
    {"id": "edge_1", "source": "node_1", "target": "node_2"},
    {"id": "edge_2", "source": "node_2", "target": "node_3"}
  ]
}
```

**节点 id 规则**: 使用 `node_` 前缀 + 递增数字或时间戳，确保全局唯一。
**连线 id 规则**: 使用 `edge_` 前缀 + `source_target`。

## 节点类型完整参考

读取 `references/processors.md` 获取每个节点的详细配置参数、脚本示例和使用说明。

快速索引：

| 类别 | 类型 | 用途 | config 关键字段 |
|------|------|------|----------------|
| INPUT | `TXT-INPUT` | 文本按行输入 | `text` |
| INPUT | `KAFKA-CONSUMER` | Kafka 消费 | `bootstrapServers`, `topic`, `groupId` |
| INPUT | `HTTP-SERVER` | HTTP 服务端 | `port`, `path` |
| INPUT | `SYSLOG-INPUT` | UDP Syslog 接收 | `port` |
| INPUT | `FILE` | 文件监听 | `path`, `mode`(TAIL/FULL) |
| INPUT | `DIR` | 目录监听 | `path` |
| MID | `GROOVY` | Groovy 脚本转换 | `script` |
| MID | `IF-ELSE` | 条件过滤 | `condition` |
| MID | `TO-JSON` | JSON 格式化 | 无 |
| MID | `CONSOLE` | 日志输出 | `prefix` |
| MID | `HTTP-CLIENT` | HTTP 客户端 | `url`, `method` |
| OUTPUT | `HTTP-BACK` | HTTP 响应回调 | `script` |
| OUTPUT | `KAFKA-PRODUCER` | Kafka 生产 | `bootstrapServers`, `topic` |
| OUTPUT | `SYSLOG-OUTPUT` | UDP Syslog 发送 | `host`, `port` |
| OUTPUT | `TXT-OUT` | 文本输出展示 | 无 |

## HTTP 请求-响应闭环

HTTP-SERVER 接收请求后，通过 `requestId` 关联 PendingResponse。下游节点处理完数据后，HTTP-BACK 节点通过 `requestId` 将响应返回给调用方。这是 TransFlow 的核心模式：

```
HTTP-SERVER → [GROOVY / IF-ELSE / ...] → HTTP-BACK
  收到请求        数据处理/路由            封装响应返回
```

HTTP-SERVER 输出的数据结构：`{"api": "/path", "method": "POST", "requestId": "uuid", "body": {...}}`

HTTP-BACK 的脚本中可使用 `data`（上游数据）和 `requestId`（请求 ID）两个变量。

## 注入数据

向运行中的任务注入数据（触发 TXT-INPUT 等节点）：

```bash
curl -X POST http://localhost:8080/api/flow-data/emit \
  -H "Content-Type: application/json" \
  -d '{"taskId": "a1b2c3d4", "nodeId": "node_1", "data": "hello world"}'
```

`data` 字段支持任意类型：字符串、数字、JSON 对象、数组。

## 常见流程模板

### 模板 1：HTTP 网关（接收请求 → 处理 → 返回响应）

```
HTTP-SERVER(:8888, /api/data) → GROOVY(数据转换) → HTTP-BACK(封装响应)
```

### 模板 2：Kafka 消费 → 处理 → HTTP 转发

```
KAFKA-CONSUMER(topic: events) → GROOVY(提取字段) → HTTP-CLIENT(POST 到目标)
```

### 模板 3：文件监听 → 条件过滤 → Kafka 生产

```
FILE(/var/log/app.log, TAIL) → IF-ELSE(data.contains("ERROR")) → KAFKA-PRODUCER(topic: errors)
```

### 模板 4：Syslog 采集 → 转 JSON → 文本输出

```
SYSLOG-INPUT(:514) → TO-JSON → TXT-OUT
```

### 模板 5：HTTP 接收 → 条件路由 → 多路输出

```
HTTP-SERVER → IF-ELSE(data.priority == "high")
  ├─ true  → HTTP-CLIENT(告警接口)
  └─ false → KAFKA-PRODUCER(普通日志主题)
```

注意：IF-ELSE 为 true 时数据从第一个输出口流出，false 时从第二个输出口流出。连线时需注意 handle：
- 条件为 true 的连线：`sourceHandle: "true"`
- 条件为 false 的连线：`sourceHandle: "false"`

## Groovy 脚本编写指南

GROOVY 和 HTTP-BACK 节点的脚本在 GroovyShell 中执行，可使用以下变量：

- `data` — 上游传入的数据，类型取决于上游节点（Map / List / String / 其他）
- `requestId` — 仅 HTTP-BACK 可用，HTTP 请求的唯一标识

### 常用脚本模式

**提取字段:**
```groovy
return data.name
```

**转换结构:**
```groovy
return ["userName": data.user, "action": data.type, "ts": System.currentTimeMillis()]
```

**条件计算:**
```groovy
if (data.amount > 1000) {
    return ["level": "high", "data": data]
}
return ["level": "low", "data": data]
```

**列表处理:**
```groovy
return data.items.collect { ["id": it.id, "name": it.name.toUpperCase()] }
```

**字符串操作:**
```groovy
def msg = data.message?.trim()?.replaceAll("\\s+", " ")
return ["cleanMessage": msg]
```

### HTTP-BACK 响应脚本

HTTP-BACK 脚本的返回值会被 JSON 序列化后作为 HTTP 响应体返回：

```groovy
// 标准成功响应
return ["code": 200, "message": "success", "data": data]

// 带 requestId 追溯
return ["code": 200, "requestId": requestId, "result": data.processed]

// 错误响应
return ["code": 500, "message": "processing failed"]
```

### IF-ELSE 条件表达式

IF-ELSE 的 condition 字段是一个返回布尔值的 Groovy 表达式：

```groovy
data.status == "active"
data.amount > 100
data.type in ["order", "payment"]
data.name?.startsWith("VIP")
data.count > 0 && data.enabled == true
```

## 最佳实践

1. **节点 id 要有意义**: 用 `http_server`、`groovy_convert`、`kafka_out` 等可读名称，而非纯数字
2. **label 要描述用途**: 用"订单数据转换"而非"Groovy节点"
3. **position 合理布局**: INPUT 在左（x=100），MID 居中（x=400），OUTPUT 在右（x=700），y 按垂直间距 150-200 排列
4. **一个任务一个完整链路**: 不要在同一个任务中放置无关的并行链路
5. **GROOVY 脚本保持简洁**: 复杂逻辑拆分为多个 GROOVY 节点串联
6. **HTTP-BACK 必须与 HTTP-SERVER 配对使用**: 通过 requestId 关联，否则响应无法返回
