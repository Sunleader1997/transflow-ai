#!/bin/bash
# Get workflow execution details
# Usage: get_execution.sh <base_url> <execution_id>

BASE_URL="${1:-http://localhost:18900}"
EXECUTION_ID="$2"

if [ -z "$EXECUTION_ID" ]; then
    echo "Usage: get_execution.sh <base_url> <execution_id>"
    exit 1
fi

curl -s "$BASE_URL/api/workflow-executions/$EXECUTION_ID"
