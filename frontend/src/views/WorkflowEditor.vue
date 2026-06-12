<template>
  <div class="workflow-editor">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="editor-header">
      <button class="btn" @click="$router.push('/workflows')">&#8592; 返回</button>
      <h2>{{ templateName }}</h2>
      <button class="btn" @click="$router.push(`/workflow/${templateId}/history`)">执行记录</button>
      <button class="btn btn-primary" @click="saveTemplate">保存</button>
    </header>
    <div class="editor-body">
      <div class="sidebar">
        <h4>节点</h4>
        <div
          class="node-item"
          draggable="true"
          @dragstart="onDragStart"
        >
          <span class="node-icon">&#128196;</span>
          <div class="node-info">
            <span class="node-name">新节点</span>
            <span class="node-desc">拖拽到画布添加</span>
          </div>
        </div>
      </div>
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          ref="vueFlowRef"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 1, x: 0, y: 0 }"
          :min-zoom="0.2"
          :max-zoom="4"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :default-edge-options="{ animated: true, style: { stroke: '#26A69A', strokeWidth: 2 } }"
          @connect="onConnect"
          @node-double-click="onNodeDoubleClick"
          @pane-click="onPaneClick"
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

    <div class="modal-overlay" v-if="editNode" @click.self="editNode = null">
      <div class="modal modal-fullscreen">
        <div class="modal-header">
          <h2>编辑节点</h2>
          <button class="close-btn" @click="editNode = null">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group title-group">
            <label>标题</label>
            <input v-model="editForm.title" required placeholder="节点标题" />
          </div>
          <div class="form-group editor-group">
            <label>描述</label>
            <MarkdownEditor v-model="editForm.description" placeholder="节点描述，支持 Markdown 语法" />
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn" @click="editNode = null">取消</button>
          <button type="button" class="btn btn-primary" @click="saveNodeEdit">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { workflowTemplateApi } from '../api/workflow.js'
import WorkflowNode from '../components/WorkflowNode.vue'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import { useToast } from '../composables/useToast.js'

const route = useRoute()
const templateId = route.params.templateId

const templateName = ref('')
const nodes = ref([])
const edges = ref([])
const vueFlowRef = ref(null)
const editNode = ref(null)
const editForm = ref({ title: '', description: '' })

const { toast, showToast } = useToast()

const { addEdges } = useVueFlow()

let nodeIdCounter = 0
const nextNodeId = () => `wf_node_${Date.now()}_${++nodeIdCounter}`

const onDragStart = (event) => {
  event.dataTransfer.setData('application/vueflow', 'workflow-node')
  event.dataTransfer.effectAllowed = 'move'
}

const onDrop = (event) => {
  const bounds = vueFlowRef.value?.$el?.getBoundingClientRect()
  if (!bounds) return
  const position = {
    x: event.clientX - bounds.left - 100,
    y: event.clientY - bounds.top - 30
  }
  const id = nextNodeId()
  nodes.value.push({
    id,
    type: 'workflow',
    position,
    data: { title: '新节点', description: '', status: 'pending' }
  })
}

const onConnect = (connection) => {
  if (connection.source === connection.target) return
  const exists = edges.value.some(
    e => e.source === connection.source && e.target === connection.target
  )
  if (exists) return
  addEdges([{
    ...connection,
    id: `edge_${connection.source}_${connection.target}`,
    animated: true,
    style: { stroke: '#26A69A', strokeWidth: 2 }
  }])
}

const onNodeDoubleClick = ({ node }) => {
  editNode.value = node
  editForm.value = { title: node.data.title || '', description: node.data.description || '' }
}

const onPaneClick = () => {
  editNode.value = null
}

const saveNodeEdit = () => {
  if (!editNode.value) return
  editNode.value.data.title = editForm.value.title
  editNode.value.data.description = editForm.value.description
  editNode.value = null
  showToast('节点已更新')
}

const loadTemplate = async () => {
  try {
    const { data } = await workflowTemplateApi.get(templateId)
    if (data) {
      templateName.value = data.name || ''
      nodes.value = (data.nodes || []).map(n => ({
        id: n.id,
        type: 'workflow',
        position: n.position || { x: 0, y: 0 },
        data: {
          title: n.title || '',
          description: n.description || '',
          status: 'pending'
        }
      }))
      edges.value = (data.edges || []).map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        animated: true,
        style: { stroke: '#26A69A', strokeWidth: 2 }
      }))
    }
  } catch (e) {
    showToast('加载模版失败', 'error')
  }
}

const saveTemplate = async () => {
  try {
    const payload = {
      nodes: nodes.value.map(n => ({
        id: n.id,
        title: n.data.title,
        description: n.data.description,
        position: n.position
      })),
      edges: edges.value.map(e => ({
        id: e.id,
        source: e.source,
        target: e.target
      }))
    }
    await workflowTemplateApi.update(templateId, payload)
    showToast('保存成功')
  } catch (e) {
    showToast('保存失败', 'error')
  }
}

onMounted(loadTemplate)
</script>

<style scoped>
.workflow-editor { height: 100vh; display: flex; flex-direction: column; }
.editor-header { display: flex; align-items: center; gap: 16px; padding: 12px 20px; background: #fff; border-bottom: 1px solid var(--border); z-index: 10; }
.editor-header h2 { font-size: 18px; flex: 1; }
.editor-body { flex: 1; display: flex; overflow: hidden; }
.sidebar { width: 220px; background: #fff; border-right: 1px solid var(--border); overflow-y: auto; padding: 12px; flex-shrink: 0; }
.sidebar h4 { font-size: 12px; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 8px; padding: 0 4px; }
.node-item { display: flex; align-items: center; gap: 10px; padding: 10px 8px; border-radius: 8px; cursor: grab; margin-bottom: 4px; transition: background 0.15s; border: 1px solid transparent; }
.node-item:hover { background: #f0f4ff; border-color: #c7d2fe; }
.node-item:active { cursor: grabbing; }
.node-icon { font-size: 22px; width: 28px; text-align: center; flex-shrink: 0; }
.node-info { flex: 1; min-width: 0; }
.node-name { display: block; font-size: 13px; font-weight: 600; }
.node-desc { display: block; font-size: 11px; color: var(--text-secondary); }
.canvas-area { flex: 1; }
.btn { padding: 10px 20px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; font-size: 14px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:hover { opacity: 0.9; }
.modal-overlay { position: fixed; top: 28px; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 12px; padding: 28px; width: 420px; max-width: 90vw; box-shadow: 0 8px 32px rgba(0,0,0,0.15); }
.modal-fullscreen { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; border-radius: 0; padding: 0; display: flex; flex-direction: column; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.modal-header h2 { font-size: 18px; margin: 0; }
.modal-body { flex: 1; min-height: 0; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; overflow: hidden; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 24px; border-top: 1px solid var(--border); flex-shrink: 0; }
.title-group { flex-shrink: 0; }
.title-group input { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; box-sizing: border-box; }
.title-group input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(25,118,210,0.1); }
.editor-group { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.editor-group label { display: block; margin-bottom: 6px; font-size: 14px; color: var(--text-secondary); }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 14px; color: var(--text-secondary); }
.form-group input, .form-group textarea { width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; }
.form-group input:focus, .form-group textarea:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(25,118,210,0.1); }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
.close-btn { border: none; background: none; font-size: 24px; cursor: pointer; color: var(--text-secondary); line-height: 1; }
.toast-bar { position: fixed; top: 0; left: 50%; transform: translateX(-50%); z-index: 200; padding: 10px 28px; border-radius: 0 0 8px 8px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.toast-bar.success { background: #4caf50; color: #fff; }
.toast-bar.error { background: #f44336; color: #fff; }
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
</style>
