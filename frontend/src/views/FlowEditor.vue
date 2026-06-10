<template>
  <div class="flow-editor">
    <transition name="toast">
      <div v-if="toast.show" :class="['toast-bar', toast.type]">{{ toast.message }}</div>
    </transition>
    <header class="editor-header">
      <button class="btn" @click="$router.push('/tasks')">&#8592; 返回</button>
      <h2>{{ taskName }}</h2>
      <span class="shortcut-hint">Ctrl+S 保存 | Backspace 删除节点 | 双击节点配置</span>
      <button class="btn btn-primary" @click="saveFlow">保存流程</button>
    </header>
    <div class="editor-body">
      <div class="sidebar">
        <div class="node-category" v-for="cat in categories" :key="cat.name">
          <h4>{{ cat.label }}</h4>
          <div
            class="node-item"
            v-for="nodeType in cat.types"
            :key="nodeType.type"
            draggable="true"
            @dragstart="onDragStart($event, nodeType)"
          >
            <span class="material-icons node-icon">{{ nodeType.icon }}</span>
            <div class="node-info">
              <span class="node-name">{{ nodeType.label }}</span>
              <span class="node-desc">{{ nodeType.desc }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          ref="vueFlowRef"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 0.7, x: 0, y: 0 }"
          :min-zoom="0.2"
          :max-zoom="4"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :connection-mode="'strict'"
          :default-edge-options="{ animated: true, style: { stroke: '#26A69A', strokeWidth: 2 } }"
          @nodes-change="onNodesChange"
          @node-double-click="onNodeDoubleClick"
          @pane-click="onPaneClick"
          @connect="onConnect"
        >
          <Background :gap="20" />
          <Controls />
          <MiniMap />
          <template #node-custom="nodeProps">
            <FlowNode
              v-bind="nodeProps"
              @config="openConfig(nodeProps.id)"
              @send="onSendData"
            />
          </template>
        </VueFlow>
      </div>
    </div>

    <NodeConfig
      v-if="configNode"
      :node="configNode"
      :config-params="configParams"
      @close="configNode = null"
      @save="saveNodeConfig"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { flowApi, flowDataApi } from '../api/index.js'
import FlowNode from '../components/FlowNode.vue'
import NodeConfig from '../components/NodeConfig.vue'
import { useToast } from '../composables/useToast.js'

const route = useRoute()
const taskId = route.params.taskId
const taskName = ref('')

const nodes = ref([])
const edges = ref([])
const configNode = ref(null)
const configParams = ref([])
const vueFlowRef = ref(null)

const { toast, showToast } = useToast()

let statusTimer = null
let outputTimer = null
let draggedType = null

const { removeNodes, addEdges } = useVueFlow()

const categories = [
  {
    label: 'INPUT',
    name: 'input',
    types: [
      { type: 'TXT-INPUT', label: '文本输入', desc: '文本框直接输入', icon: 'text_fields' },
      { type: 'KAFKA-CONSUMER', label: 'Kafka 消费者', desc: 'Kafka 消息消费', icon: 'cloud_download' },
      { type: 'HTTP-SERVER', label: 'HTTP 服务端', desc: 'Netty HTTP 服务端', icon: 'dns' },
      { type: 'SYSLOG-INPUT', label: 'Syslog 输入', desc: 'UDP Syslog 接收', icon: 'router' },
      { type: 'FILE', label: '文件监听', desc: '文件变更监听', icon: 'insert_drive_file' },
      { type: 'DIR', label: '目录监听', desc: '目录变更监听', icon: 'folder' }
    ]
  },
  {
    label: 'MID',
    name: 'mid',
    types: [
      { type: 'GROOVY', label: 'Groovy 脚本', desc: 'Groovy 脚本转换', icon: 'code' },
      { type: 'TO-JSON', label: '转 JSON', desc: '数据格式化为JSON', icon: 'data_object' },
      { type: 'IF-ELSE', label: '条件分支', desc: '条件分支过滤', icon: 'call_split' }
    ]
  },
  {
    label: 'OUTPUT',
    name: 'output',
    types: [
      { type: 'CONSOLE', label: '日志输出', desc: '控制台日志输出', icon: 'terminal' },
      { type: 'HTTP-CLIENT', label: 'HTTP 客户端', desc: 'HTTP 客户端调用', icon: 'send' },
      { type: 'HTTP-BACK', label: 'HTTP 回调', desc: 'HTTP 响应回调', icon: 'reply' },
      { type: 'KAFKA-PRODUCER', label: 'Kafka 生产者', desc: 'Kafka 消息生产', icon: 'cloud_upload' },
      { type: 'SYSLOG-OUTPUT', label: 'Syslog 输出', desc: 'UDP Syslog 发送', icon: 'cast' },
      { type: 'TXT-OUT', label: '文本输出', desc: '实时文本展示', icon: 'article' }
    ]
  }
]

let nodeIdCounter = 0
const nextNodeId = () => `node_${Date.now()}_${++nodeIdCounter}`

const getCategory = (type) => {
  for (const cat of categories) {
    if (cat.types.find(t => t.type === type)) return cat.name.toUpperCase()
  }
  return 'MID'
}

// Drag & drop from sidebar
const onDragStart = (event, nodeType) => {
  draggedType = nodeType
  event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType))
  event.dataTransfer.effectAllowed = 'move'
}

const onDrop = (event) => {
  const bounds = vueFlowRef.value?.$el?.getBoundingClientRect()
  if (bounds && draggedType) {
    const position = {
      x: event.clientX - bounds.left - 130,
      y: event.clientY - bounds.top - 30
    }
    addNode(draggedType, position)
  }
  draggedType = null
}

const addNode = (nodeType, position) => {
  const id = nextNodeId()
  nodes.value.push({
    id,
    type: 'custom',
    position,
    data: {
      type: nodeType.type,
      label: nodeType.label,
      nodeType: getCategory(nodeType.type),
      config: getDefaultConfig(nodeType.type),
      status: { state: 'STOPPED', recNumb: 0, sendNumb: 0 }
    }
  })
}

const getDefaultConfig = (type) => {
  const defaults = {
    'TXT-INPUT': { text: '' },
    'KAFKA-CONSUMER': { bootstrapServers: 'localhost:9092', topic: '', groupId: 'transflow' },
    'HTTP-SERVER': { port: '8888', defaultResponse: '{"code":200,"message":"ok"}', timeout: '30' },
    'SYSLOG-INPUT': { port: '514' },
    'FILE': { path: '', mode: 'TAIL' },
    'DIR': { path: '' },
    'GROOVY': { script: '// data 为输入数据\ndef result = data\nreturn result' },
    'TO-JSON': {},
    'IF-ELSE': { condition: '' },
    'CONSOLE': { prefix: '[TransFlow]' },
    'HTTP-CLIENT': { url: '', method: 'POST' },
    'HTTP-BACK': { script: '// data 为上游传入数据，requestId 为请求追溯 ID\nreturn ["code": 200, "message": "success", "data": data]' },
    'KAFKA-PRODUCER': { bootstrapServers: 'localhost:9092', topic: '' },
    'SYSLOG-OUTPUT': { host: 'localhost', port: '514' },
    'TXT-OUT': {}
  }
  return { ...(defaults[type] || {}) }
}

// Edge connection
const onConnect = (connection) => {
  // Prevent self-connection
  if (connection.source === connection.target) return
  // Prevent duplicate edges
  const exists = edges.value.some(
    e => e.source === connection.source && e.target === connection.target
  )
  if (exists) return
  addEdges([{
    ...connection,
    id: `edge_${connection.source}_${connection.target}`,
    animated: true,
    style: { stroke: '#26A69A', strokeWidth: 2, strokeDasharray: '5,5' }
  }])
}

const deleteNode = (id) => {
  removeNodes(id)
}

const onNodesChange = (changes) => {
  // VueFlow handles most changes; we handle edge cleanup
}

const onNodeDoubleClick = ({ node }) => {
  openConfig(node.id)
}

const onPaneClick = () => {
  configNode.value = null
}

const openConfig = async (nodeId) => {
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return
  try {
    const { data } = await flowApi.get(taskId)
    if (data?.nodesWithParams) {
      const enriched = data.nodesWithParams.find(n => n.id === nodeId)
      if (enriched?.configParams) {
        configParams.value = enriched.configParams
      }
    }
  } catch (e) {
    // Use local defaults
  }
  configNode.value = node
}

const onSendData = async ({ nodeId, text }) => {
  const lines = text.split('\n').filter(l => l.trim())
  let success = 0
  for (const line of lines) {
    try {
      await flowDataApi.emit(taskId, nodeId, line.trim())
      success++
    } catch (e) {
      console.error('Emit failed:', e)
    }
  }
  if (success > 0) showToast(`已发送 ${success} 条数据`)
}

const saveNodeConfig = (nodeId, config) => {
  const node = nodes.value.find(n => n.id === nodeId)
  if (node) {
    node.data.config = { ...node.data.config, ...config }
  }
  configNode.value = null
  showToast('节点配置已保存')
}

const saveFlow = async () => {
  try {
    const flow = {
      nodes: nodes.value.map(n => ({
        id: n.id,
        type: n.data.type,
        label: n.data.label,
        position: n.position,
        config: n.data.config
      })),
      edges: edges.value.map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle || '',
        targetHandle: e.targetHandle || ''
      }))
    }
    await flowApi.save(taskId, flow)
    showToast('保存成功')
  } catch (e) {
    console.error('Save failed:', e)
    showToast('保存失败', 'error')
  }
}

const loadFlow = async () => {
  try {
    const { data } = await flowApi.get(taskId)
    if (data) {
      taskName.value = data.name || ''
      if (data.flow) {
        nodes.value = (data.flow.nodes || []).map(n => ({
          id: n.id,
          type: 'custom',
          position: n.position || { x: 0, y: 0 },
          data: {
            type: n.type,
            label: n.label,
            nodeType: getCategory(n.type),
            config: n.config || getDefaultConfig(n.type),
            status: n.status || { state: 'STOPPED', recNumb: 0, sendNumb: 0 }
          }
        }))
        edges.value = (data.flow.edges || []).map(e => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle || undefined,
          targetHandle: e.targetHandle || undefined,
          animated: true,
          style: { stroke: '#26A69A', strokeWidth: 2, strokeDasharray: '5,5' }
        }))
      }
    }
  } catch (e) {
    console.log('No saved flow yet')
  }
}

const pollStatuses = async () => {
  try {
    const { data } = await flowDataApi.getNodeStatuses(taskId)
    if (data) {
      for (const node of nodes.value) {
        const status = data[node.id]
        if (status) node.data.status = { ...status }
      }
    }
  } catch (e) {
    // silently ignore
  }
}

const pollTxtOutputs = async () => {
  for (const node of nodes.value) {
    if (node.data.type === 'TXT-OUT') {
      try {
        const { data } = await flowDataApi.getOutput(node.id)
        if (data && data.length) {
          node.data.outputText = data[data.length - 1]
        }
      } catch (e) {
        // silently ignore
      }
    }
  }
}

const handleKeydown = (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    saveFlow()
  }
}

onMounted(() => {
  loadFlow()
  statusTimer = setInterval(pollStatuses, 2000)
  outputTimer = setInterval(pollTxtOutputs, 1000)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  clearInterval(statusTimer)
  clearInterval(outputTimer)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.flow-editor { height: 100vh; display: flex; flex-direction: column; }
.editor-header { display: flex; align-items: center; gap: 16px; padding: 12px 20px; background: #fff; border-bottom: 1px solid var(--border); z-index: 10; }
.editor-header h2 { font-size: 18px; flex: 1; }
.shortcut-hint { font-size: 12px; color: var(--text-secondary); }
.editor-body { flex: 1; display: flex; overflow: hidden; }
.sidebar { width: 260px; background: #fff; border-right: 1px solid var(--border); overflow-y: auto; padding: 12px; flex-shrink: 0; }
.node-category { margin-bottom: 20px; }
.node-category h4 { font-size: 12px; text-transform: uppercase; color: var(--text-secondary); margin-bottom: 8px; padding: 0 4px; }
.node-item { display: flex; align-items: center; gap: 10px; padding: 10px 8px; border-radius: 8px; cursor: grab; margin-bottom: 4px; transition: background 0.15s; border: 1px solid transparent; }
.node-item:hover { background: #f0f4ff; border-color: #c7d2fe; }
.node-item:active { cursor: grabbing; }
.node-icon { font-size: 22px; color: var(--primary); width: 28px; text-align: center; flex-shrink: 0; }
.node-info { flex: 1; min-width: 0; }
.node-name { display: block; font-size: 13px; font-weight: 600; }
.node-desc { display: block; font-size: 11px; color: var(--text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.canvas-area { flex: 1; }
.btn { padding: 10px 20px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; font-size: 14px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:hover { opacity: 0.9; }
.toast-bar { position: fixed; top: 0; left: 50%; transform: translateX(-50%); z-index: 200; padding: 10px 28px; border-radius: 0 0 8px 8px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.toast-bar.success { background: #4caf50; color: #fff; }
.toast-bar.error { background: #f44336; color: #fff; }
.toast-enter-active { transition: all 0.25s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
</style>
