<template>
  <div class="flow-node" :class="[typeClass, { selected: selected }]">
    <Handle type="target" :position="Position.Left" v-if="data.nodeType !== 'INPUT'" />
    <div class="node-content" @dblclick="$emit('config', id)">
      <span class="material-icons node-type-icon">{{ icon }}</span>
      <div class="node-text">
        <span class="node-label">{{ data.label }}</span>
        <span class="node-type">{{ data.type }}</span>
      </div>
      <span class="status-dot" :class="statusClass" :title="statusText"></span>
    </div>
    <div class="node-input-area" v-if="data.type === 'TXT-INPUT'">
      <textarea
        v-model="inputText"
        placeholder="输入数据，每行一条…"
        rows="4"
        @keydown.enter.ctrl="sendData"
      ></textarea>
      <button class="send-btn" @click.stop="sendData">发送</button>
    </div>
    <div class="txt-output-area" v-if="data.type === 'TXT-OUT' && data.outputText">
      <pre>{{ data.outputText }}</pre>
      <button class="copy-btn" @click.stop="copyOutput" title="一键复制">&#128203;</button>
    </div>
    <Handle type="source" :position="Position.Right" v-if="data.nodeType !== 'OUTPUT'" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps(['id', 'data', 'selected'])
const emit = defineEmits(['config', 'send'])

const inputText = ref('')

const typeClass = computed(() => {
  const m = { 'INPUT': 'type-input', 'MID': 'type-mid', 'OUTPUT': 'type-output' }
  return m[props.data.nodeType] || 'type-mid'
})

const icon = computed(() => {
  const icons = {
    'TXT-INPUT': 'text_fields',
    'KAFKA-CONSUMER': 'cloud_download',
    'HTTP-SERVER': 'dns',
    'SYSLOG-INPUT': 'router',
    'FILE': 'insert_drive_file',
    'DIR': 'folder',
    'GROOVY': 'code',
    'TO-JSON': 'data_object',
    'IF-ELSE': 'call_split',
    'CONSOLE': 'terminal',
    'HTTP-CLIENT': 'send',
    'KAFKA-PRODUCER': 'cloud_upload',
    'SYSLOG-OUTPUT': 'cast',
    'TXT-OUT': 'article'
  }
  return icons[props.data.type] || 'play_circle'
})

const statusClass = computed(() => {
  const state = props.data.status?.state || 'STOPPED'
  return {
    'RUNNING': 'status-running',
    'ERROR': 'status-error',
    'STOPPED': 'status-stopped'
  }[state] || 'status-stopped'
})

const statusText = computed(() => {
  const status = props.data.status
  if (!status) return ''
  return `${status.state} | 收:${status.recNumb || 0} 发:${status.sendNumb || 0}`
})

const sendData = () => {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', { nodeId: props.id, text })
  inputText.value = ''
}

const copyOutput = () => {
  if (props.data.outputText) {
    navigator.clipboard.writeText(props.data.outputText)
  }
}
</script>

<style scoped>
.flow-node {
  background: #fff;
  border: 2px solid #d1d5db;
  border-top: 3px solid #d1d5db;
  border-radius: 8px;
  min-width: 240px;
  max-width: 340px;
  font-size: 13px;
  cursor: default;
}
.flow-node.selected {
  border-color: #ec4899 !important;
  box-shadow: 0 0 12px #ec4899;
}
.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}
.node-type-icon { font-size: 20px; color: #555; flex-shrink: 0; }
.node-text { flex: 1; min-width: 0; }
.node-label { display: block; font-size: 13px; font-weight: 600; }
.node-type { display: block; font-size: 10px; color: #888; }
.status-dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.status-running { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.status-error { background: #ef4444; box-shadow: 0 0 6px #ef4444; }
.status-stopped { background: #9ca3af; }
.node-input-area { padding: 4px 10px 8px; border-top: 1px solid #d1f0e4; display: flex; flex-direction: column; }
.node-input-area textarea {
  width: 100%; padding: 6px 8px; border: 1px solid #d1d5db; border-radius: 4px;
  font-size: 12px; resize: vertical; outline: none; font-family: 'Consolas', monospace;
}
.node-input-area textarea:focus { border-color: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,0.15); }
.send-btn {
  margin-top: 6px; padding: 4px 14px; background: #22c55e; color: #fff;
  border: none; border-radius: 4px; cursor: pointer; font-size: 12px; align-self: flex-end;
}
.send-btn:hover { background: #16a34a; }
.txt-output-area { padding: 4px 10px 8px; border-top: 1px solid #eee; display: flex; flex-direction: column; }
.txt-output-area pre { font-size: 11px; max-height: 80px; overflow: auto; white-space: pre-wrap; word-break: break-all; color: #374151; margin: 0; }
.copy-btn { background: none; border: 1px solid #ddd; cursor: pointer; font-size: 14px; padding: 2px 6px; border-radius: 4px; align-self: flex-end; margin-top: 4px; }
.copy-btn:hover { background: #f0f0f0; }
.type-input  { border-top-color: #22c55e; }
.type-mid    { border-top-color: #3b82f6; }
.type-output { border-top-color: #f59e0b; }
</style>
