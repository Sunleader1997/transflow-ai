<template>
  <div class="workflow-list">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="header">
      <h1>工作流管理</h1>
      <p class="subtitle">创建和管理可复用的工作流模版</p>
    </header>
    <div class="stats-bar" v-if="templates.length">
      <div class="stat">
        <span class="stat-value">{{ templates.length }}</span>
        <span class="stat-label">模版总数</span>
      </div>
    </div>
    <div class="actions">
      <button class="btn btn-primary" @click="showCreate = true">+ 创建模版</button>
    </div>
    <div class="template-cards" v-if="templates.length">
      <div class="template-card" v-for="template in templates" :key="template.id">
        <div class="card-header">
          <h3 @click="$router.push(`/workflow/${template.id}`)">{{ template.name }}</h3>
          <div class="card-actions" @click.stop>
            <button class="btn-icon" @click="editTemplate(template)" title="编辑">&#9998;</button>
            <button class="btn-icon btn-danger" @click="deleteTemplate(template.id)" title="删除">&#10005;</button>
          </div>
        </div>
        <p class="card-desc">{{ template.description || '暂无描述' }}</p>
        <div class="card-footer">
          <span class="badge">节点: {{ template.nodes?.length || 0 }}</span>
          <button class="btn btn-small" @click="$router.push(`/workflow/${template.id}/history`)">执行记录</button>
          <span class="time">{{ formatTime(template.updatedAt) }}</span>
        </div>
      </div>
    </div>
    <div class="empty" v-else>
      <p>暂无工作流模版，点击上方按钮创建</p>
    </div>

    <div class="modal-overlay" v-if="showCreate || showEdit" @click.self="closeModal">
      <div class="modal">
        <h2>{{ showEdit ? '编辑模版' : '创建模版' }}</h2>
        <form @submit.prevent="saveTemplate">
          <div class="form-group">
            <label>模版名称</label>
            <input v-model="form.name" required placeholder="输入模版名称" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="form.description" placeholder="输入模版描述" rows="3"></textarea>
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
import { ref, onMounted } from 'vue'
import { workflowTemplateApi } from '../api/workflow.js'
import { useToast } from '../composables/useToast.js'

const templates = ref([])
const { toast, showToast } = useToast()
const showCreate = ref(false)
const showEdit = ref(false)
const editingId = ref(null)
const form = ref({ name: '', description: '' })

const loadTemplates = async () => {
  const { data } = await workflowTemplateApi.list()
  templates.value = data || []
}

const saveTemplate = async () => {
  try {
    if (showEdit.value) {
      await workflowTemplateApi.update(editingId.value, form.value)
      showToast('模版已更新')
    } else {
      await workflowTemplateApi.create(form.value)
      showToast('模版已创建')
    }
    closeModal()
    loadTemplates()
  } catch (e) {
    showToast('操作失败', 'error')
  }
}

const editTemplate = (template) => {
  editingId.value = template.id
  form.value = { name: template.name, description: template.description }
  showEdit.value = true
}

const deleteTemplate = async (id) => {
  if (confirm('确定删除此模版？')) {
    try {
      await workflowTemplateApi.delete(id)
      showToast('模版已删除')
      loadTemplates()
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

onMounted(loadTemplates)
</script>

<style scoped>
.workflow-list { max-width: 1100px; margin: 0 auto; padding: 32px 20px; }
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
.btn-small { padding: 6px 12px; font-size: 12px; }
.btn-icon { border: none; background: none; cursor: pointer; font-size: 16px; padding: 4px 8px; border-radius: 4px; }
.btn-icon:hover { background: #eee; }
.btn-danger { color: #dc2626; }
.template-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.template-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; transition: box-shadow 0.2s; }
.template-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-header h3 { font-size: 18px; cursor: pointer; }
.card-header h3:hover { color: var(--primary); }
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
