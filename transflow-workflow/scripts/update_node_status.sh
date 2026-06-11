#!/bin/bash
# Update node status in an execution
# Usage: update_node_status.sh <base_url> <execution_id> <node_id> <status> [detail]

BASE_URL="${1:-http://localhost:18900}"
EXECUTION_ID="$2"
NODE_ID="$3"
STATUS="$4"
DETAIL="$5"

if [ -z "$STATUS" ]; then
    echo "Usage: update_node_status.sh <base_url> <execution_id> <node_id> <status> [detail]"
    echo "Status values: pending, in_progress, completed, failed, skipped"
    exit 1
fi

curl -s -X PUT "$BASE_URL/api/workflow-executions/$EXECUTION_ID/nodes/$NODE_ID/status" \
    -H "Content-Type: application/json" \
    -d "{\"status\":\"$STATUS\",\"detail\":\"$DETAIL\"}"
