#!/bin/bash
# Start a workflow execution
# Usage: start_execution.sh <base_url> <execution_id>

BASE_URL="${1:-http://localhost:18900}"
EXECUTION_ID="$2"

if [ -z "$EXECUTION_ID" ]; then
    echo "Usage: start_execution.sh <base_url> <execution_id>"
    exit 1
fi

curl -s -X POST "$BASE_URL/api/workflow-executions/$EXECUTION_ID/start"
