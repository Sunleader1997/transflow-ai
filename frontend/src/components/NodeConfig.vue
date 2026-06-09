<template>
  <div class="config-overlay">
    <div class="config-panel">
      <div class="config-header">
        <h3>节点配置 — {{ node.data.label }}</h3>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>
      <div class="config-body">
        <div class="form-group" v-for="param in params" :key="param.field">
          <label>{{ param.label }}</label>
          <input
            v-if="param.type === 'text'"
            v-model="localConfig[param.field]"
            :placeholder="param.placeholder"
          />
          <textarea
            v-else-if="param.type === 'textarea'"
            v-model="localConfig[param.field]"
            :placeholder="param.placeholder"
            rows="4"
          ></textarea>
          <input
            v-else-if="param.type === 'number'"
            type="number"
            v-model.number="localConfig[param.field]"
            :placeholder="param.placeholder"
          />
          <select v-else-if="param.type === 'select'" v-model="localConfig[param.field]">
            <option v-for="opt in param.options" :key="opt" :value="opt">{{ opt }}</option>
          </select>
          <GroovyCodeEditor
            v-else-if="param.type === 'code'"
            v-model="localConfig[param.field]"
            :placeholder="param.placeholder"
            height="300px"
          />
          <div v-else-if="param.type === 'hint'" class="hint-text">{{ param.hint }}</div>
          <p class="param-hint" v-if="param.hint && param.type !== 'hint'">{{ param.hint }}</p>
        </div>
      </div>
      <div class="config-footer">
        <button class="btn" @click="$emit('close')">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { flowApi } from '../api/index.js'
import GroovyCodeEditor from './GroovyCodeEditor.vue'

const props = defineProps(['node', 'configParams'])
const emit = defineEmits(['close', 'save'])

const localConfig = ref({ ...props.node.data.config })
const params = ref([])

watch(() => props.node, (n) => {
  if (n) localConfig.value = { ...n.data.config }
}, { immediate: true })

// Fetch config params from backend for this node type
const loadParams = async () => {
  try {
    // Get the task ID from the route
    const taskId = window.location.hash.split('/').pop()
    const { data } = await flowApi.get(taskId)
    if (data?.flow?.nodes) {
      const backendNode = data.flow.nodes.find(n => n.id === props.node.id)
      if (backendNode?.configParams) {
        params.value = backendNode.configParams
        return
      }
    }
  } catch (e) {
    // fallback to default params
  }
  // Default params based on node type
  params.value = getDefaultParams(props.node.data.type)
}

const getDefaultParams = (type) => {
  const defaults = {
    'TXT-INPUT': [
      { field: 'text', label: '输入文本', type: 'textarea', defaultValue: '', placeholder: '输入文本内容' }
    ],
    'KAFKA-CONSUMER': [
      { field: 'bootstrapServers', label: 'Bootstrap Servers', type: 'text', defaultValue: 'localhost:9092', placeholder: 'localhost:9092' },
      { field: 'topic', label: 'Topic', type: 'text', defaultValue: '', placeholder: '订阅主题' },
      { field: 'groupId', label: 'Group ID', type: 'text', defaultValue: 'transflow', placeholder: '消费者组ID' }
    ],
    'HTTP-SERVER': [
      { field: 'port', label: '监听端口', type: 'number', defaultValue: '8888', placeholder: '8888' },
      { field: 'path', label: '路径', type: 'text', defaultValue: '/api/data', placeholder: '/api/data' }
    ],
    'SYSLOG-INPUT': [
      { field: 'port', label: '监听端口', type: 'number', defaultValue: '514', placeholder: '514' }
    ],
    'FILE': [
      { field: 'path', label: '文件路径', type: 'text', defaultValue: '', placeholder: '/path/to/file' },
      { field: 'mode', label: '监听模式', type: 'select', defaultValue: 'TAIL', options: ['TAIL', 'FULL'] }
    ],
    'DIR': [
      { field: 'path', label: '目录路径', type: 'text', defaultValue: '', placeholder: '/path/to/dir' }
    ],
    'GROOVY': [
      { field: 'script', label: 'Groovy 脚本', type: 'code', defaultValue: '// data 为输入数据\ndef result = data\nreturn result', placeholder: 'def result = data\nreturn result' }
    ],
    'TO-JSON': [],
    'IF-ELSE': [
      { field: 'condition', label: '条件表达式', type: 'text', defaultValue: '', placeholder: 'data.status == 1' }
    ],
    'CONSOLE': [
      { field: 'prefix', label: '日志前缀', type: 'text', defaultValue: '[TransFlow]', placeholder: '[TransFlow]' }
    ],
    'HTTP-CLIENT': [
      { field: 'url', label: 'URL', type: 'text', defaultValue: '', placeholder: 'https://example.com/api' },
      { field: 'method', label: 'Method', type: 'select', defaultValue: 'POST', options: ['POST', 'GET', 'PUT'] }
    ],
    'HTTP-BACK': [
      { field: 'script', label: '响应脚本', type: 'code', defaultValue: '// data 为上游传入数据，requestId 为请求追溯 ID\nreturn ["code": 200, "message": "success", "data": data]', placeholder: 'return ["code": 200, "message": "success", "data": data]' }
    ],
    'KAFKA-PRODUCER': [
      { field: 'bootstrapServers', label: 'Bootstrap Servers', type: 'text', defaultValue: 'localhost:9092', placeholder: 'localhost:9092' },
      { field: 'topic', label: 'Topic', type: 'text', defaultValue: '', placeholder: '发送目标主题' }
    ],
    'SYSLOG-OUTPUT': [
      { field: 'host', label: '目标主机', type: 'text', defaultValue: 'localhost', placeholder: 'localhost' },
      { field: 'port', label: '目标端口', type: 'number', defaultValue: '514', placeholder: '514' }
    ],
    'TXT-OUT': []
  }
  return defaults[type] || []
}

const save = () => {
  emit('save', props.node.id, localConfig.value)
}

loadParams()
</script>

<style scoped>
.config-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.config-panel { background: #fff; border-radius: 12px; width: 680px; max-width: 90vw; max-height: 85vh; display: flex; flex-direction: column; box-shadow: 0 8px 32px rgba(0,0,0,0.2); }
.config-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--border); }
.config-header h3 { font-size: 16px; }
.close-btn { border: none; background: none; font-size: 24px; cursor: pointer; color: var(--text-secondary); }
.config-body { padding: 16px 20px; overflow-y: auto; flex: 1; }
.config-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--border); }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 4px; }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; outline: none;
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus { border-color: var(--primary); }
.param-hint { font-size: 11px; color: var(--text-secondary); margin-top: 4px; }
.hint-text { font-size: 13px; color: var(--text-secondary); font-style: italic; padding: 8px; background: #f9fafb; border-radius: 6px; }
.btn { padding: 8px 16px; border: 1px solid var(--border); border-radius: 6px; cursor: pointer; font-size: 13px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
</style>
