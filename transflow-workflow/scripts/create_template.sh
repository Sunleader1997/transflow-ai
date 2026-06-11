#!/bin/bash
# Create a workflow template
# Usage: create_template.sh <base_url> <name> <description> <nodes_json> <edges_json>

BASE_URL="${1:-http://localhost:18900}"
NAME="$2"
DESCRIPTION="$3"
NODES="$4"
EDGES="$5"

if [ -z "$NAME" ]; then
    echo "Usage: create_template.sh <base_url> <name> <description> <nodes_json> <edges_json>"
    exit 1
fi

curl -s -X POST "$BASE_URL/api/workflow-templates" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$NAME\",\"description\":\"$DESCRIPTION\",\"nodes\":$NODES,\"edges\":$EDGES}"
