#!/bin/bash
# Get workflow template details
# Usage: get_template.sh <base_url> <template_id>

BASE_URL="${1:-http://localhost:18900}"
TEMPLATE_ID="$2"

if [ -z "$TEMPLATE_ID" ]; then
    echo "Usage: get_template.sh <base_url> <template_id>"
    exit 1
fi

curl -s "$BASE_URL/api/workflow-templates/$TEMPLATE_ID"
