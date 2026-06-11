---
name: transflow-workflow
description: |
  Manage and interact with the TransFlow workflow execution system via shell scripts.
  Use this skill when the user wants to create workflow templates, execute workflows,
  monitor execution progress, update node statuses, or send heartbeats.
  Also use this skill when the user mentions workflow execution, template management,
  node status updates, or execution monitoring.
---

# TransFlow Workflow Skill

TransFlow Workflow is an execution engine that runs workflow templates as tracked executions.
Each template defines a DAG of nodes; each execution tracks per-node progress with heartbeats.

## First Step: Get Service Address

Before making any API calls, ask the user for the TransFlow service address. Use this question:

"请问 TransFlow 服务的地址是什么？（例如: http://localhost:18900）"

If the user provides an address, use it as the API base URL. If the user says it's running locally on the default port, use `http://localhost:18900`.

## Quick Start

```bash
# Check if service is running
./transflow-workflow/scripts/check_service.sh http://localhost:18900

# Create a workflow template
./transflow-workflow/scripts/create_template.sh http://localhost:18900 "My Workflow" "Description" \
  '[{"id":"n1","title":"Start","description":"Begin"},{"id":"n2","title":"Process","description":"Do work"}]' \
  '[{"id":"e1","source":"n1","target":"n2"}]'

# List all templates
./transflow-workflow/scripts/list_templates.sh http://localhost:18900

# Get template details
./transflow-workflow/scripts/get_template.sh http://localhost:18900 <template-id>

# Create an execution from a template
./transflow-workflow/scripts/create_execution.sh http://localhost:18900 <template-id>

# Start the execution
./transflow-workflow/scripts/start_execution.sh http://localhost:18900 <execution-id>

# Update node status
./transflow-workflow/scripts/update_node_status.sh http://localhost:18900 <execution-id> <node-id> completed "Done"

# Send heartbeat
./transflow-workflow/scripts/send_heartbeat.sh http://localhost:18900 <execution-id>

# Get execution details
./transflow-workflow/scripts/get_execution.sh http://localhost:18900 <execution-id>
```

## API Endpoints

All endpoints are under `{SERVICE_URL}/api`.

### Workflow Templates (`/api/workflow-templates`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/workflow-templates` | — | List all templates |
| GET | `/api/workflow-templates/{id}` | — | Get template by ID |
| POST | `/api/workflow-templates` | `{name, description, nodes, edges}` | Create template |
| PUT | `/api/workflow-templates/{id}` | `{name, description, nodes, edges}` | Update template |
| DELETE | `/api/workflow-templates/{id}` | — | Delete template |

### Workflow Executions (`/api/workflow-executions`)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/workflow-executions` | — | List all executions |
| GET | `/api/workflow-executions?templateId=X` | — | List executions by template |
| GET | `/api/workflow-executions/{id}` | — | Get execution by ID |
| POST | `/api/workflow-executions` | `{templateId}` | Create execution from template |
| POST | `/api/workflow-executions/{id}/start` | — | Start execution |
| PUT | `/api/workflow-executions/{id}/status` | `{status}` | Update execution status |
| PUT | `/api/workflow-executions/{id}/nodes/{nodeId}/status` | `{status, detail}` | Update node status |
| POST | `/api/workflow-executions/{id}/heartbeat` | — | Send heartbeat |

## Execution Status Values

**Execution status:**
- `pending` — Created but not started
- `running` — Currently executing
- `completed` — All nodes finished successfully
- `failed` — One or more nodes failed
- `stopped` — Manually stopped

**Node status:**
- `pending` — Not yet started
- `in_progress` — Currently executing
- `completed` — Finished successfully
- `failed` — Execution failed
- `skipped` — Skipped (e.g., conditional branch)

## Workflow Patterns

### Monitoring an Execution

1. Create execution: `create_execution.sh`
2. Start it: `start_execution.sh`
3. Poll with: `get_execution.sh` to check progress
4. Update nodes as they complete: `update_node_status.sh`
5. Send heartbeats to indicate liveness: `send_heartbeat.sh`

### Error Handling

When a node fails, update its status with a detail message:
```bash
./transflow-workflow/scripts/update_node_status.sh $BASE $EXEC_ID $NODE_ID failed "Error message here"
```

## Tips

- Always check service availability first with `check_service.sh`
- Node IDs must match those defined in the template
- Heartbeats should be sent periodically for long-running executions
- Use `get_execution.sh` to poll execution progress
