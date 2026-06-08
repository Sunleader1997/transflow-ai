<template>
  <div class="code-editor-wrapper">
    <div ref="editorRef" class="code-editor"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightSpecialChars, placeholder as cmPlaceholder } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { javascript } from '@codemirror/lang-javascript'
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, foldGutter, indentOnInput } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap, autocompletion } from '@codemirror/autocomplete'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  height: { type: String, default: '300px' },
  extraVars: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
let view = null

// Groovy keywords
const groovyKeywords = [
  'def', 'var', 'return', 'if', 'else', 'for', 'while', 'do', 'switch', 'case',
  'break', 'continue', 'try', 'catch', 'finally', 'throw', 'throws', 'new',
  'class', 'interface', 'extends', 'implements', 'import', 'package', 'as',
  'in', 'instanceof', 'assert', 'true', 'false', 'null', 'this', 'super'
]

// data 变量及其常用方法/属性
const dataCompletions = [
  { label: 'data', type: 'variable', detail: '上游输入数据', info: '上游节点传入的数据对象，类型可能是 Map / List / String' },
  { label: 'data.get', type: 'method', detail: '获取字段值', info: 'data.get("key") 获取 Map 中的字段' },
  { label: 'data.put', type: 'method', detail: '设置字段值', info: 'data.put("key", value) 设置 Map 中的字段' },
  { label: 'data.keySet', type: 'method', detail: '所有键', info: 'data.keySet() 返回所有键的集合' },
  { label: 'data.values', type: 'method', detail: '所有值', info: 'data.values() 返回所有值的集合' },
  { label: 'data.size', type: 'method', detail: '大小', info: 'data.size() 返回集合大小' },
  { label: 'data.isEmpty', type: 'method', detail: '是否为空', info: 'data.isEmpty() 判断集合是否为空' },
  { label: 'data.containsKey', type: 'method', detail: '包含键', info: 'data.containsKey("key") 判断是否包含指定键' },
  { label: 'data.toString', type: 'method', detail: '转字符串', info: 'data.toString() 转为字符串表示' },
]

// requestId 变量
const requestIdCompletions = [
  { label: 'requestId', type: 'variable', detail: '请求追溯 ID', info: 'HTTP 请求 ID，用于 HTTP-BACK 节点追溯原始请求' },
]

// Groovy 常用方法
const groovyCompletions = [
  { label: 'println', type: 'function', detail: '打印输出', info: 'println(value) 输出到控制台' },
  { label: 'toString', type: 'method', detail: '转字符串' },
  { label: 'toInteger', type: 'method', detail: '转整数' },
  { label: 'toDouble', type: 'method', detail: '转浮点数' },
  { label: 'collect', type: 'method', detail: '映射转换', info: 'list.collect { it.transform() } 对集合元素做映射' },
  { label: 'find', type: 'method', detail: '查找元素', info: 'list.find { it.name == "target" } 查找满足条件的第一个元素' },
  { label: 'findAll', type: 'method', detail: '过滤元素', info: 'list.findAll { it.active } 过滤出满足条件的元素' },
  { label: 'each', type: 'method', detail: '遍历', info: 'list.each { println it } 遍历集合' },
  { label: 'inject', type: 'method', detail: '聚合', info: 'list.inject(0) { sum, item -> sum + item } 聚合操作' },
  { label: 'any', type: 'method', detail: '任一满足', info: 'list.any { it > 0 } 是否存在满足条件的元素' },
  { label: 'every', type: 'method', detail: '全部满足', info: 'list.every { it > 0 } 是否所有元素满足条件' },
  { label: 'groupBy', type: 'method', detail: '分组', info: 'list.groupBy { it.type } 按条件分组' },
  { label: 'sort', type: 'method', detail: '排序' },
  { label: 'unique', type: 'method', detail: '去重' },
  { label: 'flatten', type: 'method', detail: '展平', info: 'list.flatten() 展平嵌套集合' },
  { label: 'join', type: 'method', detail: '拼接', info: 'list.join(",") 用分隔符拼接' },
  { label: 'split', type: 'method', detail: '分割', info: 'str.split(",") 按分隔符分割' },
  { label: 'trim', type: 'method', detail: '去空格' },
  { label: 'replace', type: 'method', detail: '替换' },
  { label: 'matches', type: 'method', detail: '正则匹配', info: 'str.matches("\\d+") 正则匹配' },
]

const transflowCompletions = context => {
  const word = context.matchBefore(/[\w.]+/)
  if (!word || (word.from === word.to && !context.explicit)) return null

  const allItems = [
    ...dataCompletions,
    ...requestIdCompletions,
    ...groovyCompletions,
    ...groovyKeywords.map(k => ({ label: k, type: 'keyword', detail: '关键字' })),
    ...props.extraVars.map(v => ({
      label: v.name,
      type: 'variable',
      detail: v.detail || '',
      info: v.info || ''
    }))
  ]

  // 去重
  const seen = new Set()
  const options = allItems
    .filter(item => {
      if (seen.has(item.label)) return false
      seen.add(item.label)
      return true
    })
    .map(item => ({
      label: item.label,
      type: item.type,
      detail: item.detail,
      info: item.info,
      boost: item.label === 'data' ? 10 : item.label === 'requestId' ? 9 : 0
    }))

  return {
    from: word.from,
    options,
    validFor: /^[\w.]*$/
  }
}

const updateListener = EditorView.updateListener.of((update) => {
  if (update.docChanged) {
    emit('update:modelValue', update.state.doc.toString())
  }
})

onMounted(() => {
  const extensions = [
    lineNumbers(),
    highlightActiveLine(),
    highlightSpecialChars(),
    history(),
    foldGutter(),
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    syntaxHighlighting(defaultHighlightStyle),
    javascript(),
    autocompletion({
      override: [transflowCompletions],
      activateOnTyping: true,
      maxRenderedOptions: 30
    }),
    keymap.of([
      ...defaultKeymap,
      ...historyKeymap,
      ...closeBracketsKeymap
    ]),
    updateListener,
    EditorView.theme({
      '&': {
        height: props.height,
        fontSize: '13px',
        backgroundColor: '#fafafa'
      },
      '.cm-scroller': { overflow: 'auto' },
      '.cm-content': {
        fontFamily: "'JetBrains Mono', 'Fira Code', 'Consolas', monospace",
        caretColor: '#1976d2'
      },
      '.cm-gutters': {
        backgroundColor: '#f0f0f0',
        borderRight: '1px solid #e0e0e0',
        color: '#999'
      },
      '.cm-activeLine': { backgroundColor: '#e8f0fe' },
      '.cm-activeLineGutter': { backgroundColor: '#e8f0fe' },
      '&.cm-focused .cm-cursor': { borderLeftColor: '#1976d2' },
      '.cm-tooltip': {
        border: '1px solid #ddd',
        backgroundColor: '#fff',
        borderRadius: '6px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.12)'
      },
      '.cm-tooltip-autocomplete': {
        '& > ul': { maxHeight: '220px', fontFamily: "'JetBrains Mono', 'Consolas', monospace", fontSize: '12px' },
        '& > ul > li': { padding: '4px 8px' },
        '& > ul > li[aria-selected]': { backgroundColor: '#e8f0fe', color: '#1976d2' }
      },
      '.cm-completionLabel': { fontWeight: '500' },
      '.cm-completionDetail': { color: '#999', marginLeft: '8px', fontStyle: 'normal', fontSize: '11px' }
    })
  ]

  if (props.placeholder) {
    extensions.push(cmPlaceholder(props.placeholder))
  }

  const state = EditorState.create({
    doc: props.modelValue,
    extensions
  })

  view = new EditorView({
    state,
    parent: editorRef.value
  })
})

watch(() => props.modelValue, (val) => {
  if (view && val !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: val }
    })
  }
})

onBeforeUnmount(() => {
  if (view) view.destroy()
})
</script>

<style scoped>
.code-editor-wrapper {
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 6px;
  overflow: hidden;
}
.code-editor :deep(.cm-editor) {
  border-radius: 6px;
}
.code-editor :deep(.cm-focused) {
  outline: none;
}
</style>
