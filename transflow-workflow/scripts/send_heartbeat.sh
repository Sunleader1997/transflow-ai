#!/bin/bash
# Send heartbeat for a workflow execution
# Usage: send_heartbeat.sh <base_url> <execution_id>

BASE_URL="${1:-http://localhost:18900}"
EXECUTION_ID="$2"

if [ -z "$EXECUTION_ID" ]; then
    echo "Usage: send_heartbeat.sh <base_url> <execution_id>"
    exit 1
fi

curl -s -X POST "$BASE_URL/api/workflow-executions/$EXECUTION_ID/heartbeat"
