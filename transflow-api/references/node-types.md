# TransFlow Node Types Reference

## INPUT Nodes

### TXT-INPUT
Static text input. Each non-empty line becomes a data item.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `text` | textarea | `""` | Input text content, one line per data item |

### KAFKA-CONSUMER
Subscribe to a Kafka topic.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `bootstrapServers` | text | `localhost:9092` | Kafka bootstrap servers |
| `topic` | text | `""` | Topic to subscribe (required) |
| `groupId` | text | `transflow` | Consumer group ID |

### HTTP-SERVER
HTTP server that receives incoming requests.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `port` | number | `8888` | Listening port |
| `defaultResponse` | textarea | `{"code":200,"message":"ok"}` | Default response when no HTTP-BACK node handles the request |
| `timeout` | number | `30` | Timeout in seconds for waiting response |

### SYSLOG-INPUT
UDP syslog listener.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `port` | number | `514` | UDP listening port |

### FILE
Watch a file for changes.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `path` | text | `""` | File path to watch (required) |
| `mode` | select | `TAIL` | `TAIL` (follow new lines) or `FULL` (read entire file) |

### DIR
Watch a directory for new files.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `path` | text | `""` | Directory path to watch (required) |

---

## MID Nodes

### GROOVY
Transform data with Groovy script.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `script` | groovy | `def result = data\nreturn result` | Groovy script. Use `data` variable for input. Return transformed result. |

**Script variables available:**
- `data` — incoming data (String or parsed object)
- `requestId` — request ID (for HTTP-SERVER originated flows)

### TO-JSON
Convert data to JSON string. No config needed.

### IF-ELSE
Filter data with Groovy boolean expression.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `condition` | groovy | `""` | Groovy boolean expression. `data` holds input. Returns `true` to pass through, `false` to drop. |

---

## OUTPUT Nodes

### CONSOLE
Log data to console.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `prefix` | text | `[TransFlow]` | Log prefix |

### HTTP-CLIENT
Send HTTP request.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `url` | text | `""` | Target URL (required) |
| `method` | select | `POST` | HTTP method: POST, GET, PUT |

### HTTP-BACK
Return HTTP response (must be paired with HTTP-SERVER input).

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `script` | code | `return ["code": 200, "message": "success", "data": data]` | Groovy script returning a Map as JSON response body. `data` is upstream data, `requestId` traces the request. |

### KAFKA-PRODUCER
Publish data to Kafka topic.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `bootstrapServers` | text | `localhost:9092` | Kafka bootstrap servers |
| `topic` | text | `""` | Target topic (required) |

### SYSLOG-OUTPUT
Send data via UDP syslog.

| Config Field | Type | Default | Description |
|-------------|------|---------|-------------|
| `host` | text | `localhost` | Target host |
| `port` | number | `514` | Target UDP port |

### TXT-OUT
Display output text. View results via `/api/flow-data/output/{nodeId}`. No config needed.
