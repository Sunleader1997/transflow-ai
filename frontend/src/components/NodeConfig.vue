<template>
  <div class="config-overlay">
    <div class="config-panel">
      <div class="config-header">
        <h3>节点配置 — {{ node.data.label }}</h3>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>
      <div class="config-main">
        <!-- 左侧：表单字段 -->
        <div class="config-sidebar">
          <div class="form-group" v-for="param in nonTextareaParams" :key="param.field">
            <label>{{ param.label }}</label>
            <input
              v-if="param.type === 'text'"
              v-model="localConfig[param.field]"
              :placeholder="param.placeholder"
            />
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
              height="200px"
            />
            <div v-else-if="param.type === 'hint'" class="hint-text">{{ param.hint }}</div>
            <p class="param-hint" v-if="param.hint && param.type !== 'hint'">{{ param.hint }}</p>
          </div>

          <!-- textarea 字段导航 -->
          <div v-if="textareaParams.length > 0" class="textarea-nav">
            <div class="textarea-nav-title">文本字段</div>
            <div
              v-for="param in textareaParams"
              :key="param.field"
              class="textarea-nav-item"
              :class="{ active: activeTextarea === param.field }"
              @click="activeTextarea = param.field"
            >
              {{ param.label }}
            </div>
          </div>
        </div>

        <!-- 右侧：Markdown 编辑器 -->
        <div class="config-editor">
          <div v-if="activeTextareaParam" class="editor-wrapper">
            <MarkdownEditor
              v-model="localConfig[activeTextarea]"
              :placeholder="activeTextareaParam.placeholder"
            />
          </div>
          <div v-else-if="textareaParams.length === 0 && nonTextareaParams.length === 0" class="editor-empty">
            该节点无配置项
          </div>
          <div v-else-if="textareaParams.length === 0" class="editor-empty">
            该节点无文本字段，请在左侧配置参数
          </div>
        </div>
      </div>
      <div v-if="compileError" class="compile-error">
        <span class="error-icon">&#9888;</span>
        <span class="error-text">{{ compileError }}</span>
      </div>
      <div class="config-footer">
        <button class="btn" @click="$emit('close')">取消</button>
        <button class="btn btn-primary" @click="save" :disabled="compiling">
          <span v-if="compiling">编译中...</span>
          <span v-else>保存</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { flowApi, groovyApi } from '../api/index.js'
import GroovyCodeEditor from './GroovyCodeEditor.vue'
import MarkdownEditor from './MarkdownEditor.vue'

const props = defineProps(['node', 'configParams'])
const emit = defineEmits(['close', 'save'])

const localConfig = ref({ ...props.node.data.config })
const params = ref([])
const compiling = ref(false)
const compileError = ref('')
const activeTextarea = ref('')

const nonTextareaParams = computed(() => params.value.filter(p => p.type !== 'textarea'))
const textareaParams = computed(() => params.value.filter(p => p.type === 'textarea'))
const activeTextareaParam = computed(() => textareaParams.value.find(p => p.field === activeTextarea.value))

watch(() => props.node, (n) => {
  if (n) {
    localConfig.value = { ...n.data.config }
    compileError.value = ''
  }
}, { immediate: true })

watch(textareaParams, (list) => {
  if (list.length > 0 && (!activeTextarea.value || !list.find(p => p.field === activeTextarea.value))) {
    activeTextarea.value = list[0].field
  }
}, { immediate: true })

watch(localConfig, () => {
  compileError.value = ''
}, { deep: true })

const fillDefaults = (config, paramList) => {
  const filled = { ...config }
  for (const p of paramList) {
    if (p.field && (filled[p.field] === undefined || filled[p.field] === '')) {
      filled[p.field] = p.defaultValue ?? ''
    }
  }
  return filled
}

const loadParams = async () => {
  try {
    const taskId = window.location.hash.split('/').pop()
    const { data } = await flowApi.get(taskId)
    if (data?.flow?.nodes) {
      const backendNode = data.flow.nodes.find(n => n.id === props.node.id)
      if (backendNode?.configParams) {
        params.value = backendNode.configParams
        localConfig.value = fillDefaults(localConfig.value, backendNode.configParams)
        return
      }
    }
  } catch (e) {
    // fallback
  }
  const defaults = getDefaultParams(props.node.data.type)
  params.value = defaults
  localConfig.value = fillDefaults(localConfig.value, defaults)
}

const getDefaultParams = (type) => {
  const defaults = {
    'TXT-INPUT': [
      { field: 'text', label: '输入文本', type: 'textarea', defaultValue: '', placeholder: '输入文本内容，支持 Markdown' }
    ],
    'KAFKA-CONSUMER': [
      { field: 'bootstrapServers', label: 'Bootstrap Servers', type: 'text', defaultValue: 'localhost:9092', placeholder: 'localhost:9092' },
      { field: 'topic', label: 'Topic', type: 'text', defaultValue: '', placeholder: '订阅主题' },
      { field: 'groupId', label: 'Group ID', type: 'text', defaultValue: 'transflow', placeholder: '消费者组ID' }
    ],
    'HTTP-SERVER': [
      { field: 'port', label: '监听端口', type: 'number', defaultValue: '8888', placeholder: '8888' },
      { field: 'defaultResponse', label: '默认返回', type: 'textarea', defaultValue: '{"code":200,"message":"ok"}', placeholder: '{"code":200,"message":"ok"}' },
      { field: 'timeout', label: '超时(秒)', type: 'number', defaultValue: '30', placeholder: '30' }
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

const save = async () => {
  compileError.value = ''

  const scriptFields = []
  for (const p of params.value) {
    if (p.type === 'code' && p.field) {
      const script = localConfig.value[p.field]
      if (script && script.trim()) {
        scriptFields.push({ field: p.field, script })
      }
    }
  }

  if (scriptFields.length > 0) {
    compiling.value = true
    try {
      for (const { script } of scriptFields) {
        const { data } = await groovyApi.compile(script)
        if (!data.success) {
          compileError.value = data.message || 'Groovy 编译失败'
          return
        }
      }
    } catch (e) {
      compileError.value = e.response?.data?.message || '编译请求失败，请检查后端服务'
      return
    } finally {
      compiling.value = false
    }
  }

  emit('save', props.node.id, localConfig.value)
}

loadParams()
</script>

<style scoped>
.config-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.config-panel { background: #fff; width: 95vw; height: 90vh; border-radius: 12px; display: flex; flex-direction: column; box-shadow: 0 12px 48px rgba(0,0,0,0.25); overflow: hidden; }
.config-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 20px; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.config-header h3 { font-size: 16px; }
.close-btn { border: none; background: none; font-size: 24px; cursor: pointer; color: var(--text-secondary); line-height: 1; }
.config-main { display: flex; flex: 1; min-height: 0; overflow: hidden; }
.config-sidebar { width: 280px; min-width: 280px; border-right: 1px solid var(--border); padding: 16px 16px 16px 20px; overflow-y: auto; background: #fafbfc; }
.config-editor { flex: 1; min-width: 0; padding: 16px 20px 16px 16px; overflow: hidden; display: flex; flex-direction: column; }
.editor-wrapper { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.editor-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 14px; }
.config-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--border); flex-shrink: 0; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 4px; }
.form-group input, .form-group select {
  width: 100%; padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; outline: none; box-sizing: border-box;
}
.form-group input:focus, .form-group select:focus { border-color: var(--primary); }
.param-hint { font-size: 11px; color: var(--text-secondary); margin-top: 4px; }
.hint-text { font-size: 13px; color: var(--text-secondary); font-style: italic; padding: 8px; background: #f9fafb; border-radius: 6px; }
.textarea-nav { margin-top: 20px; padding-top: 16px; border-top: 1px dashed var(--border); }
.textarea-nav-title { font-size: 11px; font-weight: 600; color: #888; text-transform: uppercase; margin-bottom: 8px; letter-spacing: 0.5px; }
.textarea-nav-item { padding: 8px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; color: #555; transition: all 0.15s; margin-bottom: 4px; }
.textarea-nav-item:hover { background: #e9ecef; }
.textarea-nav-item.active { background: #1976d2; color: #fff; }
.btn { padding: 8px 20px; border: 1px solid var(--border); border-radius: 6px; cursor: pointer; font-size: 13px; background: #fff; }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.compile-error { display: flex; align-items: center; gap: 8px; padding: 10px 20px; background: #fef2f2; border-top: 1px solid #fecaca; color: #b91c1c; font-size: 12px; flex-shrink: 0; }
.error-icon { font-size: 14px; }
.error-text { white-space: pre-wrap; word-break: break-all; }
</style>
