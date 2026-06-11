<template>
  <div class="execution-detail">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="detail-header">
      <button class="btn btn-back" @click="goBack">&#8592; 返回</button>
      <div class="header-info">
        <h2>
          执行 #{{ executionId.slice(0, 8) }}
          <span :class="['status-badge', execution?.status]">{{ statusLabel(execution?.status) }}</span>
        </h2>
        <div class="header-meta">
          <span class="meta-item" v-if="execution?.startedAt">
            开始: {{ formatTime(execution.startedAt) }}
          </span>
          <span class="meta-item" v-if="execution?.completedAt">
            结束: {{ formatTime(execution.completedAt) }}
          </span>
          <span class="meta-item heartbeat" v-if="execution?.heartbeatAt">
            心跳: {{ formatTime(execution.heartbeatAt) }}
          </span>
        </div>
      </div>
    </header>
    <div class="canvas-area">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :default-viewport="{ zoom: 1, x: 0, y: 0 }"
        :min-zoom="0.2"
        :max-zoom="4"
        :nodes-draggable="false"
        :nodes-connectable="false"
        :edges-updatable="false"
        :default-edge-options="{ animated: true, style: { stroke: '#26A69A', strokeWidth: 2 } }"
      >
        <Background :gap="20" />
        <Controls />
        <MiniMap />
        <template #node-workflow="nodeProps">
          <WorkflowNode v-bind="nodeProps" />
        </template>
      </VueFlow>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { workflowExecutionApi } from '../api/workflow.js'
import WorkflowNode from '../components/WorkflowNode.vue'
import { useToast } from '../composables/useToast.js'

const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

const executionId = route.params.executionId
const execution = ref(null)
const nodes = ref([])
const edges = ref([])

let pollTimer = null

const goBack = () => {
  if (execution.value?.templateId) {
    router.push(`/workflow/${execution.value.templateId}/history`)
  } else {
    router.push('/workflows')
  }
}

const statusLabel = (status) => {
  const map = { pending: '待执行', running: '运行中', completed: '已完成', failed: '失败', stopped: '已停止' }
  return map[status] || status || '-'
}

const formatTime = (ts) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

const loadExecution = async () => {
  try {
    const { data } = await workflowExecutionApi.get(executionId)
    execution.value = data
    nodes.value = (data.nodes || []).map(n => ({
      id: n.id,
      type: 'workflow',
      position: n.position || { x: 0, y: 0 },
      data: {
        title: n.title,
        description: n.description,
        status: n.status || 'pending',
        detail: n.detail
      }
    }))
    edges.value = (data.edges || []).map(e => ({
      id: e.id,
      source: e.source,
      target: e.target,
      animated: true,
      style: { stroke: '#26A69A', strokeWidth: 2, strokeDasharray: '5,5' }
    }))
  } catch (e) {
    showToast('加载执行详情失败', 'error')
  }
}

const pollExecution = async () => {
  try {
    const { data } = await workflowExecutionApi.get(executionId)
    execution.value = data
    for (const node of nodes.value) {
      const updated = (data.nodes || []).find(n => n.id === node.id)
      if (updated) {
        node.data.status = updated.status || 'pending'
        node.data.detail = updated.detail
      }
    }
    if (data.status === 'completed' || data.status === 'failed' || data.status === 'stopped') {
      clearInterval(pollTimer)
    }
  } catch (e) {
    // silently ignore
  }
}

onMounted(() => {
  loadExecution()
  pollTimer = setInterval(pollExecution, 2000)
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
})
</script>

<style scoped>
.execution-detail { height: 100vh; display: flex; flex-direction: column; }
.detail-header {
  display: flex; align-items: center; gap: 16px;
  padding: 12px 20px; background: #fff; border-bottom: 1px solid var(--border); z-index: 10;
}
.header-info { flex: 1; }
.header-info h2 { font-size: 18px; display: flex; align-items: center; gap: 12px; }
.header-meta { display: flex; gap: 16px; margin-top: 4px; }
.meta-item { font-size: 13px; color: var(--text-secondary); }
.meta-item.heartbeat { color: var(--primary); }
.canvas-area { flex: 1; }
.btn { padding: 10px 20px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; font-size: 14px; background: #fff; }
.btn-back { background: transparent; border: 1px solid var(--border); }
.btn-back:hover { background: #f3f4f6; }
.status-badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; }
.status-badge.pending { background: #f3f4f6; color: #6b7280; }
.status-badge.running { background: #dbeafe; color: #1976D2; }
.status-badge.completed { background: #d1fae5; color: #059669; }
.status-badge.failed { background: #fee2e2; color: #dc2626; }
.status-badge.stopped { background: #fef3c7; color: #d97706; }
.toast-bar { position: fixed; top: 0; left: 50%; transform: translateX(-50%); z-index: 200; padding: 10px 28px; border-radius: 0 0 8px 8px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.toast-bar.success { background: #4caf50; color: #fff; }
.toast-bar.error { background: #f44336; color: #fff; }
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
</style>
