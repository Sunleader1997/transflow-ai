#!/bin/bash
# Check if TransFlow service is running
# Usage: check_service.sh <base_url>

BASE_URL="${1:-http://localhost:18900}"

if curl -s -f -o /dev/null -w "%{http_code}" "$BASE_URL/api/workflow-templates" 2>/dev/null | grep -q "200"; then
    echo "OK: TransFlow service is running at $BASE_URL"
    exit 0
else
    echo "ERROR: TransFlow service is not reachable at $BASE_URL"
    exit 1
fi
