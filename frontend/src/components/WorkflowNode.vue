<template>
  <div class="workflow-node" :class="[statusClass, { selected: selected }]">
    <Handle type="target" :position="Position.Left" />
    <div class="node-content">
      <span class="node-icon">{{ statusIcon }}</span>
      <div class="node-text">
        <span class="node-title">{{ data.title }}</span>
        <span class="node-desc" v-if="data.description">{{ data.description }}</span>
      </div>
    </div>
    <div class="node-detail" v-if="data.detail">{{ data.detail }}</div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps(['id', 'data', 'selected'])

const statusClass = computed(() => {
  const status = props.data?.status || 'pending'
  return `status-${status}`
})

const statusIcon = computed(() => {
  const icons = {
    'pending': '⏳',
    'in_progress': '⚡',
    'completed': '✅',
    'failed': '❌',
    'skipped': '⏭️'
  }
  return icons[props.data?.status] || '⏳'
})
</script>

<style scoped>
.workflow-node {
  background: #fff;
  border: 2px solid #d1d5db;
  border-radius: 8px;
  min-width: 200px;
  max-width: 300px;
  font-size: 13px;
  transition: all 0.3s ease;
}

.workflow-node.selected {
  border-color: #ec4899 !important;
  box-shadow: 0 0 12px #ec4899;
}

.status-pending { border-color: #9ca3af; }
.status-in_progress {
  border-color: #3b82f6;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.4);
  animation: pulse 2s infinite;
}
.status-completed { border-color: #22c55e; }
.status-failed { border-color: #ef4444; }
.status-skipped { border-color: #6b7280; }

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 8px rgba(59, 130, 246, 0.3); }
  50% { box-shadow: 0 0 20px rgba(59, 130, 246, 0.6); }
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}

.node-icon { font-size: 18px; }

.node-text {
  flex: 1;
  min-width: 0;
}

.node-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
}

.node-desc {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-detail {
  padding: 6px 12px;
  border-top: 1px solid #e5e7eb;
  font-size: 11px;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}
</style>
