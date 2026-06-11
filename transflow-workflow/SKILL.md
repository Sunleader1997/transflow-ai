---
name: transflow-workflow
description: |
  Create and execute task plans using TransFlow Workflow system.
  Use this skill when you need to break down complex tasks into visual workflow steps,
  track execution progress, and update task statuses in real-time.
  Each workflow node represents one task step. Update node status as you work.
---

# TransFlow Workflow — AI Task Planning & Execution

Use TransFlow Workflow to create visual task plans and track execution progress.
Each node = one task. Update status as you execute.

## Core Workflow

```
1. Create template (task plan)
2. Create execution from template
3. Start execution
4. For each node:
   a. Update status → in_progress
   b. Do the work
   c. Update status → completed (or failed)
5. Send heartbeats between tasks
```

## First Step: Get Service Address

Before making any API calls, ask the user for the TransFlow service address:

"请问 TransFlow 服务的地址是什么？（例如: http://localhost:18900）"

Default: `http://localhost:18900`

## Quick Start

```bash
BASE="http://localhost:18900"
SCRIPTS="./transflow-workflow/scripts"

# 1. Check service
$SCRIPTS/check_service.sh $BASE

# 2. Create task plan template
$SCRIPTS/create_template.sh $BASE "任务计划名称" "计划描述" \
  '[{"id":"n1","title":"任务1标题","description":"任务1详细描述","position":{"x":50,"y":100}},
    {"id":"n2","title":"任务2标题","description":"任务2详细描述","position":{"x":400,"y":100}}]' \
  '[{"id":"e1","source":"n1","target":"n2"}]'

# 3. Create execution (copies template)
EXEC_ID=$($SCRIPTS/create_execution.sh $BASE <template-id> | jq -r '.id')

# 4. Start execution
$SCRIPTS/start_execution.sh $BASE $EXEC_ID

# 5. Execute tasks (update status as you work)
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n1 in_progress "正在执行..."
# ... do the work ...
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n1 completed "完成"

# 6. Send heartbeat between tasks
$SCRIPTS/send_heartbeat.sh $BASE $EXEC_ID

# 7. Continue with next task
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n2 in_progress "正在执行..."
# ...
```

## Task Plan Design Rules

### Node = One Task

Each node must represent **one atomic task** that can be:
- Started and completed independently
- Described with clear success criteria
- Executed in sequence or parallel (based on edges)

### Node Title

- Use action verbs: "创建...", "实现...", "测试...", "部署..."
- Keep under 20 characters
- Examples: "创建数据库模型", "实现API接口", "编写单元测试"

### Node Description

Describe **what to do** and **how to verify success**:

```
Good: "创建 User 模型，包含 name/email 字段。验证: 模型可序列化为 JSON"
Bad: "处理数据"
```

### Edge = Dependency

- Edge from A → B means "B depends on A"
- Only add edges for real dependencies
- Independent tasks can run in parallel (no edge between them)

### Layout

- Horizontal spacing: 350px
- Vertical spacing: 200px (for parallel branches)
- Start at: `{"x": 50, "y": 100}`

**Sequential tasks:**
```json
[
  {"id":"n1","title":"步骤1","description":"...","position":{"x":50,"y":100}},
  {"id":"n2","title":"步骤2","description":"...","position":{"x":400,"y":100}},
  {"id":"n3","title":"步骤3","description":"...","position":{"x":750,"y":100}}
]
```

**Parallel tasks:**
```json
[
  {"id":"n1","title":"准备","description":"...","position":{"x":50,"y":100}},
  {"id":"n2","title":"任务A","description":"...","position":{"x":400,"y":0}},
  {"id":"n3","title":"任务B","description":"...","position":{"x":400,"y":200}},
  {"id":"n4","title":"合并","description":"...","position":{"x":750,"y":100}}
]
```

## Status Update Protocol

### Before Starting a Task

```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID <node-id> in_progress "<what you're doing>"
```

Example:
```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n1 in_progress "创建 User 模型文件..."
```

### After Completing a Task

```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID <node-id> completed "<what was done>"
```

Example:
```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n1 completed "创建了 User.java，包含 name/email 字段"
```

### If a Task Fails

```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID <node-id> failed "<error description>"
```

Example:
```bash
$SCRIPTS/update_node_status.sh $BASE $EXEC_ID n1 failed "编译错误: 找不到 symbol User"
```

### Between Tasks

Send heartbeat to indicate you're still alive:

```bash
$SCRIPTS/send_heartbeat.sh $BASE $EXEC_ID
```

## Detail Field Best Practices

The `detail` field (4th argument) should be:

- **in_progress**: What you're currently doing (brief)
  - ✅ "创建 User.java 模型文件"
  - ✅ "正在调用 API..."
  - ❌ "处理中"

- **completed**: What was accomplished (specific)
  - ✅ "创建了 3 个文件: User.java, UserService.java, UserController.java"
  - ✅ "测试通过: 15/15"
  - ❌ "完成"

- **failed**: What went wrong (with context)
  - ✅ "编译错误: UserService.java:42 - 找不到 symbol 'findById'"
  - ✅ "测试失败: testUserCreate - 预期 201 但得到 500"
  - ❌ "错误"

## Heartbeat Strategy

- Send heartbeat **between tasks** (not during)
- Send heartbeat if a task takes more than 30 seconds
- Heartbeat timeout is 60 seconds — don't wait longer than 45 seconds

## Example: Code Review Task Plan

```bash
BASE="http://localhost:18900"
SCRIPTS="./transflow-workflow/scripts"

# Create template for code review workflow
$SCRIPTS/create_template.sh $BASE "代码审查: PR #123" "审查用户认证模块的代码变更" \
  '[
    {"id":"n1","title":"阅读PR描述","description":"了解变更目的和范围。验证: 能概述PR的主要改动","position":{"x":50,"y":100}},
    {"id":"n2","title":"检查代码结构","description":"审查文件组织、命名规范、模块划分。验证: 无明显结构问题","position":{"x":400,"y":100}},
    {"id":"n3","title":"审查逻辑实现","description":"检查业务逻辑正确性、边界条件、错误处理。验证: 逻辑无明显缺陷","position":{"x":750,"y":100}},
    {"id":"n4","title":"检查测试覆盖","description":"验证测试用例是否覆盖关键路径。验证: 测试覆盖率 > 80%","position":{"x":1100,"y":100}},
    {"id":"n5","title":"编写审查意见","description":"汇总发现的问题和建议。验证: 输出结构化的审查报告","position":{"x":1450,"y":100}}
  ]' \
  '[
    {"id":"e1","source":"n1","target":"n2"},
    {"id":"e2","source":"n2","target":"n3"},
    {"id":"e3","source":"n3","target":"n4"},
    {"id":"e4","source":"n4","target":"n5"}
  ]'
```

## API Reference

### Templates

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/workflow-templates` | List all templates |
| GET | `/api/workflow-templates/{id}` | Get template |
| POST | `/api/workflow-templates` | Create template |
| PUT | `/api/workflow-templates/{id}` | Update template |
| DELETE | `/api/workflow-templates/{id}` | Delete template |

### Executions

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/workflow-executions` | List all executions |
| GET | `/api/workflow-executions?templateId=X` | List by template |
| GET | `/api/workflow-executions/{id}` | Get execution |
| POST | `/api/workflow-executions` | Create from template |
| POST | `/api/workflow-executions/{id}/start` | Start execution |
| PUT | `/api/workflow-executions/{id}/status` | Update execution status |
| PUT | `/api/workflow-executions/{id}/nodes/{nodeId}/status` | Update node status |
| POST | `/api/workflow-executions/{id}/heartbeat` | Send heartbeat |

### Node Status Values

| Status | Meaning | When to use |
|--------|---------|-------------|
| `pending` | Not started | Default state |
| `in_progress` | Executing | When starting a task |
| `completed` | Done | When task succeeds |
| `failed` | Error | When task fails |
| `skipped` | Skipped | For conditional branches |

### Execution Status

| Status | Meaning |
|--------|---------|
| `pending` | Created, not started |
| `running` | In progress |
| `completed` | All nodes done |
| `failed` | One or more nodes failed |
| `stopped` | Manually stopped or heartbeat timeout |
