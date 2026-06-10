---
name: transflow-api
description: |
  Manage and interact with the TransFlow data pipeline engine via its REST API.
  Use this skill whenever the user mentions TransFlow, data flow, pipeline, task management, flow configuration,
  node setup, Groovy scripting, or any operation related to a TransFlow service.
  Also use this skill when the user wants to create tasks, configure processing pipelines,
  test data flows, check node statuses, or inspect outputs — even if they don't explicitly mention "TransFlow".
---

# TransFlow API Skill

TransFlow is a data-flow processing engine that lets users build processing pipelines (called **Tasks**) by connecting **Nodes** with **Edges**.
Nodes come in three categories: INPUT (data sources), MID (transformations), and OUTPUT (data sinks).

## First Step: Get Service Address

Before making any API calls, ask the user for the TransFlow service address. Use this question:

"请问 TransFlow 服务的地址是什么？（例如: http://localhost:18900）"

If the user provides an address, use it as the API base URL. If the user says it's running locally on the default port, use `http://localhost:18900`.

## API Base

All endpoints are under `{SERVICE_URL}/api`. Use `curl` or any HTTP client to call them, replacing `{SERVICE_URL}` with the address the user provided.

## Endpoints

### Task Management (`/api/tasks`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/tasks` | — | List all tasks |
| GET | `/api/tasks/{id}` | — | Get a task by ID |
| POST | `/api/tasks` | `{name, description, flow?}` | Create a new task (auto-starts engine) |
| PUT | `/api/tasks/{id}` | `{name, description, flow?}` | Update a task (restarts engine) |
| DELETE | `/api/tasks/{id}` | — | Delete a task (stops engine) |

### Flow Configuration (`/api/flow`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/flow/{taskId}` | — | Get flow config for a task (includes node configParams) |
| PUT | `/api/flow/{taskId}` | `{nodes, edges}` | Save/update flow config (restarts engine) |

### Groovy Validation (`/api/groovy`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/groovy/compile` | `{script}` | Validate Groovy script syntax and dry-run |

### Flow Data (`/api/flow-data`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/flow-data/emit` | `{taskId, nodeId, data}` | Inject data into a flow node |
| GET | `/api/flow-data/output/{nodeId}` | — | Get latest output from a node |
| GET | `/api/flow-data/node-statuses/{taskId}` | — | Get status map of all nodes in a task |

## Node Model

A Node has this structure:

```json
{
  "id": "unique-node-id",
  "type": "NODE-TYPE-NAME",
  "label": "Display Label",
  "position": {"x": 100, "y": 200},
  "config": {"field1": "value1", "field2": "value2"},
  "status": {"state": "running|stopped|error", "message": ""}
}
```

An Edge connects two nodes:

```json
{
  "source": "node-a-id",
  "target": "node-b-id"
}
```

## Node Types

TransFlow supports these node types. Each has specific config fields. Read `references/node-types.md` for full details.

**INPUT nodes** (data sources):
- `TXT-INPUT` — Static text input (lines become data items)
- `KAFKA-CONSUMER` — Subscribe to Kafka topic
- `HTTP-SERVER` — HTTP server that receives requests
- `SYSLOG-INPUT` — UDP syslog listener
- `FILE` — Watch a file for changes (TAIL or FULL mode)
- `DIR` — Watch a directory for new files

**MID nodes** (transformations):
- `GROOVY` — Run Groovy script to transform data (variable `data` holds input)
- `TO-JSON` — Convert data to JSON string
- `IF-ELSE` — Filter data with Groovy boolean expression

**OUTPUT nodes** (data sinks):
- `CONSOLE` — Log to console
- `HTTP-CLIENT` — Send HTTP request
- `HTTP-BACK` — Return HTTP response (paired with HTTP-SERVER)
- `KAFKA-PRODUCER` — Publish to Kafka topic
- `SYSLOG-OUTPUT` — Send UDP syslog
- `TXT-OUT` — Display output text (viewable via API)

## Workflow Patterns

### Creating a Complete Task

1. **Create the task**: POST `/api/tasks` with `{name, description}`
2. **Configure the flow**: PUT `/api/flow/{taskId}` with `{nodes, edges}`
3. **Test data injection**: POST `/api/flow-data/emit` to send test data
4. **Check outputs**: GET `/api/flow-data/output/{nodeId}` to verify results
5. **Monitor status**: GET `/api/flow-data/node-statuses/{taskId}`

### Building a Flow

A valid flow needs:
- At least one INPUT node as the starting point
- Zero or more MID nodes for transformations
- At least one OUTPUT node as the endpoint
- Edges connecting nodes in a directed graph (INPUT -> MID -> OUTPUT)

Node IDs must be unique within a flow. Use descriptive IDs like `txt-in-1`, `groovy-filter`, `console-out`.

### Groovy Script Tips

- In `GROOVY` nodes: use variable `data` to access incoming data, `return` the transformed result
- In `IF-ELSE` nodes: write a boolean expression using `data` (e.g., `data.status == 1`)
- In `HTTP-BACK` nodes: return a Map that becomes the JSON response body
- Always validate scripts with `/api/groovy/compile` before saving the flow

## Common Operations

When the user asks to:

- **"List tasks"** → GET `/api/tasks`
- **"Create a task"** → POST `/api/tasks` with name/description
- **"Show task flow"** → GET `/api/flow/{taskId}`
- **"Build a pipeline"** → Create task + PUT flow with nodes and edges
- **"Test with data"** → POST `/api/flow-data/emit`
- **"Check output"** → GET `/api/flow-data/output/{nodeId}`
- **"Check status"** → GET `/api/flow-data/node-statuses/{taskId}`
- **"Validate Groovy"** → POST `/api/groovy/compile`

Always present API results to the user in a clear, readable format. When building flows, suggest reasonable node configurations based on the user's goal.
