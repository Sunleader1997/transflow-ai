<template>
  <div class="task-list">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="header">
      <h1>TRANSFLOW 灵流</h1>
      <p class="subtitle">灵活编排的数据分发系统</p>
    </header>
    <div class="stats-bar" v-if="tasks.length">
      <div class="stat">
        <span class="stat-value">{{ tasks.length }}</span>
        <span class="stat-label">任务总数</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ runningCount }}</span>
        <span class="stat-label">运行中</span>
      </div>
    </div>
    <div class="actions">
      <button class="btn btn-primary" @click="showCreate = true">+ 创建任务</button>
    </div>
    <div class="task-cards" v-if="tasks.length">
      <div class="task-card" v-for="task in tasks" :key="task.id" @click="$router.push(`/flow/${task.id}`)">
        <div class="card-header">
          <h3>{{ task.name }}</h3>
          <div class="card-actions" @click.stop>
            <button class="btn-icon" @click="editTask(task)" title="编辑">&#9998;</button>
            <button class="btn-icon btn-danger" @click="deleteTask(task.id)" title="删除">&#10005;</button>
          </div>
        </div>
        <p class="card-desc">{{ task.description || '暂无描述' }}</p>
        <div class="card-footer">
          <span class="badge">节点: {{ task.flow?.nodes?.length || 0 }}</span>
          <span class="time">{{ formatTime(task.updatedAt) }}</span>
        </div>
      </div>
    </div>
    <div class="empty" v-else>
      <p>暂无任务，点击上方按钮创建</p>
    </div>

    <div class="modal-overlay" v-if="showCreate || showEdit" @click.self="closeModal">
      <div class="modal">
        <h2>{{ showEdit ? '编辑任务' : '创建任务' }}</h2>
        <form @submit.prevent="saveTask">
          <div class="form-group">
            <label>任务名称</label>
            <input v-model="form.name" required placeholder="输入任务名称" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="form.description" placeholder="输入任务描述" rows="3"></textarea>
          </div>
          <div class="form-actions">
            <button type="button" class="btn" @click="closeModal">取消</button>
            <button type="submit" class="btn btn-primary">{{ showEdit ? '保存' : '创建' }}</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { taskApi } from '../api/index.js'
import { useToast } from '../composables/useToast.js'

const tasks = ref([])
const { toast, showToast } = useToast()
const showCreate = ref(false)
const showEdit = ref(false)
const editingId = ref(null)
const form = ref({ name: '', description: '' })

const runningCount = computed(() => tasks.value.filter(t => t.running).length)

const loadTasks = async () => {
  const { data } = await taskApi.list()
  tasks.value = data || []
}

const saveTask = async () => {
  try {
    if (showEdit.value) {
      await taskApi.update(editingId.value, form.value)
      showToast('任务已更新')
    } else {
      await taskApi.create(form.value)
      showToast('任务已创建')
    }
    closeModal()
    loadTasks()
  } catch (e) {
    showToast('操作失败', 'error')
  }
}

const editTask = (task) => {
  editingId.value = task.id
  form.value = { name: task.name, description: task.description }
  showEdit.value = true
}

const deleteTask = async (id) => {
  if (confirm('确定删除此任务？')) {
    try {
      await taskApi.delete(id)
      showToast('任务已删除')
      loadTasks()
    } catch (e) {
      showToast('删除失败', 'error')
    }
  }
}

const closeModal = () => {
  showCreate.value = false
  showEdit.value = false
  form.value = { name: '', description: '' }
}

const formatTime = (ts) => {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadTasks)
</script>

<style scoped>
.task-list { max-width: 1100px; margin: 0 auto; padding: 32px 20px; }
.header { text-align: center; margin-bottom: 32px; }
.header h1 { font-size: 32px; color: var(--text); margin-bottom: 8px; }
.subtitle { color: var(--text-secondary); font-size: 16px; }
.stats-bar { display: flex; gap: 24px; justify-content: center; margin-bottom: 24px; }
.stat { text-align: center; }
.stat-value { display: block; font-size: 28px; font-weight: 700; color: var(--primary); }
.stat-label { font-size: 13px; color: var(--text-secondary); }
.actions { text-align: center; margin-bottom: 24px; }
.btn { padding: 10px 20px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; font-size: 14px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:hover { opacity: 0.9; }
.btn-icon { border: none; background: none; cursor: pointer; font-size: 16px; padding: 4px 8px; border-radius: 4px; }
.btn-icon:hover { background: #eee; }
.btn-danger { color: #dc2626; }
.task-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.task-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; cursor: pointer; transition: box-shadow 0.2s; }
.task-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-header h3 { font-size: 18px; }
.card-desc { color: var(--text-secondary); font-size: 14px; margin-bottom: 12px; }
.card-footer { display: flex; justify-content: space-between; align-items: center; }
.badge { background: #e0e7ff; color: var(--primary); padding: 2px 10px; border-radius: 12px; font-size: 12px; }
.time { font-size: 12px; color: var(--text-secondary); }
.empty { text-align: center; padding: 60px; color: var(--text-secondary); }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 12px; padding: 28px; width: 420px; max-width: 90vw; box-shadow: 0 8px 32px rgba(0,0,0,0.15); }
.modal h2 { margin-bottom: 20px; font-size: 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 14px; color: var(--text-secondary); }
.form-group input, .form-group textarea { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; }
.form-group input:focus, .form-group textarea:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(25,118,210,0.1); }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
.toast-bar { position: fixed; top: 0; left: 50%; transform: translateX(-50%); z-index: 200; padding: 10px 28px; border-radius: 0 0 8px 8px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.toast-bar.success { background: #4caf50; color: #fff; }
.toast-bar.error { background: #f44336; color: #fff; }
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
</style>
