#!/bin/bash
# List all workflow templates
# Usage: list_templates.sh <base_url>

BASE_URL="${1:-http://localhost:18900}"

curl -s "$BASE_URL/api/workflow-templates"
