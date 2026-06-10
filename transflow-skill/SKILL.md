---
name: transflow
description: TransFlow 数据流处理引擎技能 - 帮助用户设计、配置和生成数据处理流程。当用户提到数据流、流水线、数据处理、消息路由、文件监控、HTTP 服务、Kafka、Syslog、日志处理、数据转换、实时处理、流式处理、管道、ETL、数据集成、消息队列、事件驱动、异步处理、数据管道等概念时，必须使用此技能。也适用于用户描述需要处理数据、监控文件变化、接收 HTTP 请求、消费 Kafka 消息、发送 Syslog、执行 Groovy 脚本转换数据等具体需求的场景。
---

# TransFlow 数据流处理引擎技能

## 概述

TransFlow 是一个基于 Reactor 的响应式数据流处理引擎，支持多种输入源和处理节点的灵活组合。本技能帮助用户：

1. **设计数据处理流程** - 根据需求推荐合适的处理器组合
2. **配置处理器参数** - 提供详细的配置说明和最佳实践
3. **生成流程定义代码** - 创建完整的 JSON 流程配置

## 处理器分类

### 输入源（INPUT）- 6 个

输入源是流程的起点，它们产生数据并推送到下游。

| 处理器 | 功能 | 典型场景 |
|--------|------|----------|
| **HTTP-SERVER** | 启动 HTTP 服务器接收请求 | API 网关、Webhook 接收、请求处理 |
| **FILE** | 读取文件内容（支持 TAIL/FULL 模式） | 日志文件监控、配置文件读取、批量数据导入 |
| **DIR** | 监听目录文件变化事件 | 文件上传监控、自动处理新文件 |
| **KAFKA-CONSUMER** | 消费 Kafka 消息 | 消息队列处理、事件驱动架构 |
| **SYSLOG-INPUT** | 接收 Syslog 消息 | 网络设备日志、系统监控 |
| **TXT-INPUT** | 输入静态文本数据 | 测试数据、批量处理、数据注入 |

### 处理节点 - 9 个

处理节点接收上游数据，进行处理后传递给下游。

| 处理器 | 功能 | 典型场景 |
|--------|------|----------|
| **GROOVY** | 执行 Groovy 脚本转换数据 | 数据转换、业务逻辑、复杂计算 |
| **HTTP-CLIENT** | 发送 HTTP 请求 | 调用外部 API、数据同步 |
| **HTTP-BACK** | 生成 HTTP 响应 | API 响应处理、请求-响应关联 |
| **IF-ELSE** | 条件过滤/路由 | 数据筛选、分支处理 |
| **KAFKA-PRODUCER** | 发送 Kafka 消息 | 消息转发、事件发布 |
| **SYSLOG-OUTPUT** | 发送 Syslog 消息 | 日志转发、告警通知 |
| **TO-JSON** | 数据格式化为 JSON | 数据标准化、格式转换 |
| **CONSOLE** | 输出到控制台日志 | 调试、监控、审计 |
| **TXT-OUT** | 缓存最新输出文本 | 结果展示、状态监控 |

## 流程设计模式

### 1. HTTP API 网关模式

```
HTTP-SERVER → GROOVY (业务逻辑) → HTTP-BACK (响应)
```

**场景**: 接收 HTTP 请求，处理后返回响应

**配置要点**:
- HTTP-SERVER 设置端口和超时
- GROOVY 处理请求数据，构造响应
- HTTP-BACK 通过 requestId 关联请求和响应

### 2. 文件监控处理模式

```
FILE (TAIL) → TO-JSON → IF-ELSE (过滤) → KAFKA-PRODUCER
```

**场景**: 实时监控日志文件，过滤后发送到 Kafka

**配置要点**:
- FILE 使用 TAIL 模式持续监听
- TO-JSON 标准化数据格式
- IF-ELSE 按条件过滤
- KAFKA-PRODUCER 发送到指定 topic

### 3. 消息路由模式

```
KAFKA-CONSUMER → IF-ELSE (路由) → 多个处理分支
```

**场景**: 根据消息内容路由到不同处理逻辑

**配置要点**:
- KAFKA-CONSUMER 订阅源 topic
- IF-ELSE 使用 Groovy 表达式判断路由条件
- 多个下游处理器处理不同类型的消息

### 4. 数据集成模式

```
SYSLOG-INPUT → GROOVY (解析) → HTTP-CLIENT (转发)
```

**场景**: 接收 Syslog 消息，解析后转发到 HTTP API

**配置要点**:
- SYSLOG-INPUT 监听 UDP 端口
- GROOVY 解析 Syslog 格式，提取关键信息
- HTTP-CLIENT 发送到目标 API

### 5. 批量处理模式

```
TXT-INPUT → GROOVY (处理) → CONSOLE (输出)
```

**场景**: 批量处理静态数据，用于测试或一次性任务

**配置要点**:
- TXT-INPUT 输入多行数据
- GROOVY 逐行处理
- CONSOLE 查看处理结果

## 处理器配置指南

### HTTP-SERVER

**配置参数**:
- `port` (number): 监听端口，默认 8888
- `defaultResponse` (textarea): 超时默认响应
- `timeout` (number): 超时秒数，默认 30

**输出数据结构**:
```json
{
  "path": "/api/endpoint",
  "method": "POST",
  "query": {},
  "body": "...",
  "requestId": "uuid"
}
```

**最佳实践**:
- 设置合理的超时时间，避免请求长时间挂起
- 提供默认响应，确保超时后客户端能收到反馈
- 使用 requestId 关联 HTTP-BACK 进行响应

### FILE

**配置参数**:
- `path` (text): 文件路径
- `mode` (select): TAIL 或 FULL

**输出**: 每行文本作为独立数据项

**最佳实践**:
- TAIL 模式适合实时监控日志文件
- FULL 模式适合一次性读取配置文件
- 确保文件路径可访问，有读取权限

### GROOVY

**配置参数**:
- `script` (groovy): Groovy 脚本

**可用变量**:
- `data`: 输入数据（Object 类型）

**最佳实践**:
- 脚本应返回处理后的数据，返回 null 会丢弃数据
- 复杂逻辑建议封装为函数
- 注意异常处理，避免脚本错误导致流程中断

**示例脚本**:
```groovy
// 提取字段
def result = [name: data.name, value: data.value * 2]
return result

// 条件处理
if (data.status == 'error') {
    return [alert: true, message: data.error]
}
return data

// 格式转换
return "Processed: ${data.toString()}"
```

### IF-ELSE

**配置参数**:
- `condition` (groovy): 布尔表达式

**可用变量**:
- `data`: 输入数据

**最佳实践**:
- 表达式应返回 boolean 值
- 使用 Groovy 的安全导航操作符 `?.` 处理可能为 null 的字段
- 复杂条件建议使用括号明确优先级

**示例表达式**:
```groovy
data.status == 'active'
data.amount > 100 && data.type == 'order'
data.tags?.contains('important')
data.timestamp > System.currentTimeMillis() - 3600000
```

### HTTP-CLIENT

**配置参数**:
- `url` (text): 目标 URL
- `method` (select): POST/GET/PUT

**输入**: 数据作为请求体发送

**输出**: 响应 JSON 对象

**最佳实践**:
- URL 支持动态构造，在 GROOVY 中预处理
- POST 方法用于创建/更新资源
- GET 方法用于查询（数据作为 query 参数需预处理）

### KAFKA-CONSUMER / KAFKA-PRODUCER

**配置参数**:
- `bootstrapServers` (text): Kafka 地址
- `topic` (text): 主题名称
- `groupId` (text): 消费者组 ID（仅 CONSUMER）

**最佳实践**:
- 确保 Kafka 集群可达
- 使用有意义的 groupId，便于监控和管理
- 生产者可设置 key 进行消息分区

### SYSLOG-INPUT / SYSLOG-OUTPUT

**配置参数**:
- `port` (number): UDP 端口
- `host` (text): 目标主机（仅 OUTPUT）

**最佳实践**:
- Syslog 使用 RFC 3164 格式
- 默认端口 514 需要 root 权限，建议使用 1514 等高位端口
- OUTPUT 支持发送到远程 Syslog 服务器

## 流程定义格式

TransFlow 使用 JSON 格式定义流程：

```json
{
  "id": "flow-id",
  "name": "流程名称",
  "nodes": [
    {
      "id": "node-1",
      "type": "HTTP-SERVER",
      "config": {
        "port": 8080,
        "timeout": 30
      },
      "targets": ["node-2"]
    },
    {
      "id": "node-2",
      "type": "GROOVY",
      "config": {
        "script": "return data"
      },
      "targets": ["node-3"]
    },
    {
      "id": "node-3",
      "type": "HTTP-BACK",
      "config": {
        "script": "return data"
      },
      "targets": []
    }
  ]
}
```

### 节点配置说明

- `id`: 节点唯一标识符
- `type`: 处理器类型（必须是上述处理器之一）
- `config`: 处理器配置参数
- `targets`: 下游节点 ID 列表（空数组表示终点）

## 常见问题解决

### 1. HTTP 请求超时

**问题**: HTTP-SERVER 接收请求后未及时响应

**解决方案**:
- 检查 GROOVY 脚本是否有阻塞操作
- 增加 HTTP-SERVER 的 timeout 配置
- 确保 HTTP-BACK 正确关联 requestId

### 2. 文件监控无数据

**问题**: FILE TAIL 模式没有输出

**解决方案**:
- 确认文件路径正确且有读取权限
- 检查文件是否有新内容追加
- 验证文件编码是否正确

### 3. Kafka 消费失败

**问题**: KAFKA-CONSUMER 无法消费消息

**解决方案**:
- 检查 bootstrapServers 地址是否可达
- 确认 topic 存在且有数据
- 验证 groupId 是否正确

### 4. Groovy 脚本错误

**问题**: GROOVY 处理器执行失败

**解决方案**:
- 检查脚本语法是否正确
- 确认 data 变量的类型和结构
- 添加 try-catch 处理异常

## 最佳实践总结

1. **流程设计**
   - 每个流程专注于一个业务目标
   - 合理使用 IF-ELSE 进行数据分流
   - 避免过长的处理链，保持流程清晰

2. **错误处理**
   - GROOVY 脚本添加异常处理
   - HTTP-SERVER 设置默认响应
   - 使用 CONSOLE 输出调试信息

3. **性能优化**
   - 避免在 GROOVY 中执行耗时操作
   - 合理设置 Kafka 消费者并发
   - 监控节点状态和处理计数

4. **可维护性**
   - 使用有意义的节点 ID
   - 添加流程和节点说明文档
   - 定期清理无用的流程

## 获取帮助

如需进一步帮助，可以：
- 查看具体处理器的源代码实现
- 参考项目中的示例流程
- 使用 CONSOLE 节点调试数据流
