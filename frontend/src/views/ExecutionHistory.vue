<template>
  <div class="execution-history">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="header">
      <div class="header-top">
        <button class="btn btn-back" @click="$router.push('/workflows')">&#8592; 返回</button>
        <button class="btn btn-primary" @click="createExecution">+ 新建执行</button>
      </div>
      <h1>{{ templateName }}</h1>
      <p class="subtitle">执行记录</p>
    </header>
    <div class="stats-bar" v-if="executions.length">
      <div class="stat">
        <span class="stat-value">{{ executions.length }}</span>
        <span class="stat-label">执行总数</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ executions.filter(e => e.status === 'completed').length }}</span>
        <span class="stat-label">已完成</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ executions.filter(e => e.status === 'failed').length }}</span>
        <span class="stat-label">失败</span>
      </div>
    </div>
    <div class="execution-cards" v-if="executions.length">
      <div class="execution-card" v-for="exec in executions" :key="exec.id" @click="goToDetail(exec.id)">
        <div class="card-header">
          <span class="exec-id">#{{ exec.id.slice(0, 8) }}</span>
          <span :class="['status-badge', exec.status]">{{ statusLabel(exec.status) }}</span>
        </div>
        <div class="card-body">
          <div class="card-info">
            <span class="info-label">开始时间</span>
            <span class="info-value">{{ formatTime(exec.startedAt || exec.createdAt) }}</span>
          </div>
          <div class="card-info" v-if="exec.finishedAt">
            <span class="info-label">结束时间</span>
            <span class="info-value">{{ formatTime(exec.finishedAt) }}</span>
          </div>
          <div class="card-info">
            <span class="info-label">节点数</span>
            <span class="info-value">{{ exec.nodeStatuses ? Object.keys(exec.nodeStatuses).length : 0 }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="empty" v-else-if="!loading">
      <p>暂无执行记录，点击上方按钮创建</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { workflowTemplateApi, workflowExecutionApi } from '../api/workflow.js'
import { useToast } from '../composables/useToast.js'

const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

const templateId = route.params.templateId
const templateName = ref('')
const executions = ref([])
const loading = ref(true)

const loadData = async () => {
  try {
    const [templateRes, execRes] = await Promise.all([
      workflowTemplateApi.get(templateId),
      workflowExecutionApi.list(templateId)
    ])
    templateName.value = templateRes.data.name || '未知模版'
    executions.value = (execRes.data || []).sort((a, b) => {
      const ta = new Date(a.startedAt || a.createdAt).getTime()
      const tb = new Date(b.startedAt || b.createdAt).getTime()
      return tb - ta
    })
  } catch (e) {
    showToast('加载失败', 'error')
  } finally {
    loading.value = false
  }
}

const createExecution = async () => {
  try {
    const { data } = await workflowExecutionApi.create(templateId)
    showToast('执行已创建')
    if (data?.id) {
      router.push(`/execution/${data.id}`)
    } else {
      loadData()
    }
  } catch (e) {
    showToast('创建失败', 'error')
  }
}

const goToDetail = (id) => {
  router.push(`/execution/${id}`)
}

const statusLabel = (status) => {
  const map = { pending: '待执行', running: '运行中', completed: '已完成', failed: '失败', stopped: '已停止' }
  return map[status] || status
}

const formatTime = (ts) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadData)
</script>

<style scoped>
.execution-history { max-width: 1100px; margin: 0 auto; padding: 32px 20px; }
.header { text-align: center; margin-bottom: 32px; }
.header-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header h1 { font-size: 28px; color: var(--text); margin-bottom: 8px; }
.subtitle { color: var(--text-secondary); font-size: 16px; }
.stats-bar { display: flex; gap: 24px; justify-content: center; margin-bottom: 24px; }
.stat { text-align: center; }
.stat-value { display: block; font-size: 28px; font-weight: 700; color: var(--primary); }
.stat-label { font-size: 13px; color: var(--text-secondary); }
.btn { padding: 10px 20px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; font-size: 14px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:hover { opacity: 0.9; }
.btn-back { background: transparent; border: 1px solid var(--border); }
.btn-back:hover { background: #f3f4f6; }
.execution-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.execution-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; cursor: pointer; transition: box-shadow 0.2s; }
.execution-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.exec-id { font-size: 16px; font-weight: 600; color: var(--text); }
.status-badge { padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: 500; }
.status-badge.pending { background: #f3f4f6; color: #6b7280; }
.status-badge.running { background: #dbeafe; color: #1976D2; }
.status-badge.completed { background: #d1fae5; color: #059669; }
.status-badge.failed { background: #fee2e2; color: #dc2626; }
.status-badge.stopped { background: #fef3c7; color: #d97706; }
.card-body { display: flex; flex-direction: column; gap: 8px; }
.card-info { display: flex; justify-content: space-between; align-items: center; }
.info-label { font-size: 13px; color: var(--text-secondary); }
.info-value { font-size: 13px; color: var(--text); }
.empty { text-align: center; padding: 60px; color: var(--text-secondary); }
.toast-bar { position: fixed; top: 0; left: 50%; transform: translateX(-50%); z-index: 200; padding: 10px 28px; border-radius: 0 0 8px 8px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.toast-bar.success { background: #4caf50; color: #fff; }
.toast-bar.error { background: #f44336; color: #fff; }
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
</style>
