# TRANSFLOW 灵流

灵活编排的数据分发系统 — 通过可视化拖拽编排，将多种数据源经处理节点流转至目标端。

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端 | Java + Spring Boot (WebFlux) + Reactor | 17 / 3.4.3 |
| 消息 | Kafka (reactor-kafka) | - |
| 网络 | Netty (reactor-netty) | - |
| 脚本 | Groovy | 4.0.18 |
| 工具 | Hutool, Fastjson2 | - |
| 持久化 | JSON 文件 (`db/*.json`)，无数据库 | - |
| 前端 | Vue 3 + VueFlow + CodeMirror 6 | 3.x / 1.48.2 |

---

## 核心功能

### 任务管理

- 卡片式任务列表，支持创建、编辑、删除
- 任务创建后自动运行，服务重启时自动恢复任务
- 更新任务自动重启流程，删除任务自动销毁资源

### 流程编排

拖拽式画布编辑器，节点通过连线组成数据处理链路：

**节点 (Node)** — 左侧图标 (Material Icons)，右侧名称 + 描述，选中时紫红色霓虹灯效果

| 类别 | 类型 | 说明 |
|------|------|------|
| INPUT | `TXT-INPUT` | 文本框输入，内嵌输入框 + 发送按钮，Ctrl+Enter 快捷发送 |
| | `KAFKA-CONSUMER` | Kafka 消费者，基于 reactor-kafka 响应式消费 |
| | `HTTP-SERVER` | Netty HTTP 服务端，支持 GET / POST，输出协议结构化数据 `{"api":"","method":"get","body":{},"requestId":""}` |
| | `SYSLOG-INPUT` | UDP Syslog 接收，RFC 3164 格式 |
| | `FILE` | 文件监听，支持 FULL（全量读取）和 TAIL（增量追尾）模式 |
| | `DIR` | 目录变更监听，基于 WatchService 捕获创建/修改/删除事件 |
| MID | `GROOVY` | Groovy 脚本转换，通过 `data` 变量访问输入数据，CodeMirror 代码编辑器（亮色主题 + 关键字自动补全） |
| | `TO-JSON` | 数据格式化为 JSON |
| | `IF-ELSE` | 条件分支过滤，Groovy 布尔表达式 |
| OUTPUT | `CONSOLE` | 日志输出 |
| | `HTTP-CLIENT` | HTTP 客户端调用，支持 GET / POST / PUT |
| | `HTTP-BACK` | HTTP 响应返回，通过 Groovy 脚本封装返回体（CodeMirror 代码编辑器），将数据返回给 HTTP-SERVER 调用方 |
| | `KAFKA-PRODUCER` | Kafka 生产者，基于 reactor-kafka 响应式发送 |
| | `SYSLOG-OUTPUT` | UDP Syslog 发送，RFC 3164 格式 |
| | `TXT-OUT` | 实时文本展示，仅保留最新一条数据，支持一键复制 |

**连线 (Edge)** — 流动虚线表示数据流向，禁止自连和重复连线，点击可删除

**节点配置** — 后端 `configParams()` 驱动，前端动态渲染表单（`text` / `number` / `select` / `hint` / `code` / `groovy` / `json` / `boolean` / `password` / `list`），新增节点类型无需改前端

### 数据流处理

基于 Project Reactor 的响应式数据管道，使用 `Sinks.Many` 实现背压感知的流式处理，支持任意类型数据（二进制 / 文本 / JSON）的链式处理。

### HTTP 请求-响应闭环

`HTTP-SERVER` 接收请求后输出协议结构化数据，请求上下文通过 `HttpRequestContext` 注册表关联。下游节点（如 `HTTP-BACK`）通过 `requestId` 完成响应闭环，将处理结果返回给调用方：

```
HTTP-SERVER → [GROOVY / IF-ELSE / ...] → HTTP-BACK
  收到请求        数据处理/路由            封装响应返回客户端
```

### 节点状态

- 每个 INPUT / OUTPUT 节点实时显示状态：� 运行中 / � 错误 / ⚪ 已停止
- 状态通过 `/api/flow-data/node-statuses` 每 2 秒自动刷新
- HTTP Server 使用 reactor-netty 非阻塞 I/O

---

## 系统架构

### 项目结构

```
transflow-ai/
├── start.sh                    # 开发环境一键启动脚本
├── install.sh                  # 生产环境一键安装脚本
├── backend/                    # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/org/sunyaxing/transflow/
│       ├── TransflowApplication.java        # 入口，Spring Boot 启动
│       ├── config/
│       │   ├── WebConfig.java               # WebFlux CORS 配置
│       │   ├── ProcessorConfig.java         # 处理器注册（15 种节点类型）
│       │   └── StartupRunner.java           # 启动时自动恢复所有任务
│       ├── model/
│       │   ├── Task.java                    # 任务模型
│       │   ├── Flow.java                    # 流程模型（nodes + edges）
│       │   ├── Node.java / Edge.java        # 节点 / 连线
│       │   ├── Position.java                # 节点位置
│       │   ├── NodeType.java                # INPUT / MID / OUTPUT 枚举
│       │   ├── NodeParam.java               # 配置参数定义
│       │   └── NodeStatus.java              # 节点运行时状态
│       ├── persistence/
│       │   └── FileRepository.java          # JSON 文件持久化
│       ├── controller/
│       │   ├── TaskController.java          # 任务 CRUD，创建/更新时自动启停
│       │   ├── FlowController.java          # 流程存取 + configParams
│       │   └── FlowDataController.java      # 数据发射 + 输出查询 + 节点状态
│       └── engine/
│           ├── FlowEngine.java              # Reactor 数据流引擎，Sinks 发射 + 链式路由
│           ├── FlowEngineManager.java       # 引擎生命周期管理
│           └── processors/
│               ├── NodeProcessor.java       # 处理器接口（configParams + process + destroy）
│               ├── NodeProcessorFactory.java # 处理器工厂，类型注册 + 实例创建
│               ├── HttpRequestContext.java   # HTTP 请求-响应上下文注册表
│               ├── TxtInputProcessor.java   # TXT-INPUT
│               ├── KafkaConsumerProcessor.java # KAFKA-CONSUMER（reactor-kafka）
│               ├── HttpServerProcessor.java  # HTTP-SERVER（协议结构化输出 + requestId）
│               ├── SyslogInputProcessor.java # SYSLOG-INPUT（UDP 接收）
│               ├── FileProcessor.java       # FILE（FULL / TAIL 模式）
│               ├── DirProcessor.java        # DIR（WatchService 目录监听）
│               ├── GroovyProcessor.java     # GROOVY（脚本转换，代码编辑器）
│               ├── ToJsonProcessor.java     # TO-JSON
│               ├── IfElseProcessor.java     # IF-ELSE（条件过滤，代码编辑器）
│               ├── ConsoleProcessor.java    # CONSOLE
│               ├── HttpClientProcessor.java  # HTTP-CLIENT（GET / POST / PUT）
│               ├── HttpBackProcessor.java   # HTTP-BACK（Groovy 封装响应返回）
│               ├── KafkaProducerProcessor.java # KAFKA-PRODUCER（reactor-kafka）
│               ├── SyslogOutputProcessor.java # SYSLOG-OUTPUT（UDP 发送）
│               └── TxtOutProcessor.java     # TXT-OUT（仅保留最新一条）
├── frontend/                   # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── main.js / App.vue
│       ├── router/index.js
│       ├── api/index.js                    # Axios API 封装
│       ├── views/
│       │   ├── TaskList.vue                # 任务列表页，卡片式布局，统计栏
│       │   └── FlowEditor.vue              # 流程编排编辑器，拖拽节点、连线
│       ├── components/
│       │   ├── FlowNode.vue                # 自定义节点，内嵌输入框 / 实时输出 / 状态指示器
│       │   ├── NodeConfig.vue              # 节点配置面板，双击弹出，动态表单
│       │   └── GroovyCodeEditor.vue        # CodeMirror 6 代码编辑器，亮色主题，自动补全
│       └── assets/
│           └── style.css                   # 全局主题 + VueFlow 边动画 + 霓虹灯选中效果
```

### 处理器架构（工厂模式）

处理器是普通类（无 `@Component`），由 `NodeProcessorFactory` 统一管理。每次任务启动时，工厂为每个节点创建**独立实例**，确保不同任务的同类型节点互不干扰：

```
任务A: HTTP-SERVER(port=8888) → 实例A
任务B: HTTP-SERVER(port=9999) → 实例B
```

### 节点参数定义

每个处理器通过 `configParams()` 返回 `List<NodeParam>`，描述可配置参数：

```java
@Override
public List<NodeParam> configParams() {
    return List.of(
        new NodeParam("port", "监听端口", "number", "8888", "8888", null, null),
        new NodeParam("path", "路径", "text", "/api/data", "/api/data", null, null)
    );
}
```

| 字段 | 说明 |
|------|------|
| `field` | 参数名（config Map 的 key） |
| `label` | 前端显示标签 |
| `type` | 控件类型（`text` / `number` / `select` / `hint` / `code` / `groovy` / `json` / `boolean` / `password` / `list`） |
| `defaultValue` | 默认值 |
| `placeholder` | 占位提示 |
| `hint` | 底部说明文字 |
| `options` | select 控件选项列表 |

其中 `code` 和 `groovy` 类型在前端渲染为 CodeMirror 6 代码编辑器（亮色主题、行号、代码折叠、Groovy/JS 语法高亮、`data` / `requestId` 变量自动补全 + Groovy 关键字补全），`json` 类型渲染为 JSON 编辑器。

---

## 前端

| 文件 | 职责 |
|------|------|
| `views/TaskList.vue` | 任务列表页，浅色主题，统计栏 |
| `views/FlowEditor.vue` | 流程编排编辑器，拖拽节点、连线 |
| `components/FlowNode.vue` | 自定义节点，TXT-INPUT 内嵌输入框 + 发送按钮，TXT-OUT 显示最新输出 |
| `components/NodeConfig.vue` | 节点配置面板，双击弹出，动态表单，`code` 类型渲染为代码编辑器 |
| `components/GroovyCodeEditor.vue` | CodeMirror 6 代码编辑器，亮色主题，`data` / `requestId` 自动补全 |
| `api/index.js` | Axios API 封装 |
| `assets/style.css` | 全局主题 + VueFlow 边动画 |

### 交互

- 拖拽节点到画布
- 双击节点打开配置
- 选中节点 Backspace 删除（连带清理连线）
- 点击连线删除
- Ctrl+S 保存流程，顶部通知条提示保存成功 / 失败
- TXT-INPUT 节点内嵌输入框，点击发送或 Ctrl+Enter 注入数据
- TXT-OUT 实时轮询最新一条数据 + 一键复制

---

## 快速启动（开发）

```bash
# 一键启动前后端
./start.sh

# 或分别启动
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

| 服务 | 地址 |
|------|------|
| 后端 | http://localhost:8080 |
| 前端 | http://localhost:5173 |

## 一键安装（生产）

将前端打包进后端 JAR，安装为独立服务：

```bash
# 构建并安装到 /opt/transflow-ai/
sudo ./install.sh

# 启停管理
/opt/transflow-ai/start.sh start    # 启动
/opt/transflow-ai/start.sh stop     # 停止
/opt/transflow-ai/start.sh restart  # 重启
/opt/transflow-ai/start.sh status   # 状态

# 卸载
/opt/transflow-ai/uninstall.sh
```

安装目录结构：

```
/opt/transflow-ai/
├── transflow-1.0.0.jar             # 应用 JAR（内嵌前端静态资源）
├── start.sh                         # 启停管理脚本（start/stop/restart/status）
├── uninstall.sh                     # 卸载脚本
├── transflow.pid                    # 运行时 PID 文件
├── config/
│   └── application.properties       # 外部配置（端口、日志、数据目录等）
├── logs/
│   ├── transflow.log                # 应用日志
│   └── startup.log                  # 启动日志
└── db/                              # JSON 文件持久化目录
```

安装后通过 http://localhost:8080 直接访问，前后端同端口，无需额外代理。

## 环境依赖

- Java 17 (`JAVA_HOME`)
- Maven 3.9+（`MAVEN_HOME`，自定义仓库 `MAVEN_REPOSITORY`）
- Node.js 18+
