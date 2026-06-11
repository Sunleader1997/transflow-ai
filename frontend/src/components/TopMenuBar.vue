<template>
  <nav class="top-menu-bar">
    <div class="menu-left">
      <span class="app-logo">⬡</span>
      <span class="app-name">TransFlow</span>
      <div class="menu-items">
        <router-link to="/tasks" class="menu-item" :class="{ active: isActive('/tasks') || isActive('/flow') }">
          任务
        </router-link>
        <router-link to="/workflows" class="menu-item" :class="{ active: isActive('/workflows') || isActive('/workflow') }">
          工作流
        </router-link>
      </div>
    </div>
    <div class="menu-right">
      <span class="status-dot" :class="serverStatus"></span>
      <span class="status-text">{{ serverStatus === 'online' ? '已连接' : '未连接' }}</span>
    </div>
  </nav>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const serverStatus = ref('offline')

const isActive = (path) => {
  return route.path.startsWith(path)
}

const checkServer = async () => {
  try {
    const res = await fetch('/api/tasks')
    serverStatus.value = res.ok ? 'online' : 'offline'
  } catch {
    serverStatus.value = 'offline'
  }
}

let timer = null
onMounted(() => {
  checkServer()
  timer = setInterval(checkServer, 10000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.top-menu-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 28px;
  padding: 0 12px;
  background: linear-gradient(180deg, #f8f8f8 0%, #e8e8e8 100%);
  border-bottom: 1px solid #c0c0c0;
  font-size: 12px;
  user-select: none;
  -webkit-app-region: drag;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

.menu-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-logo {
  font-size: 14px;
  color: var(--primary);
  font-weight: bold;
}

.app-name {
  font-weight: 600;
  font-size: 13px;
  color: #333;
  margin-right: 12px;
}

.menu-items {
  display: flex;
  gap: 0;
  -webkit-app-region: no-drag;
}

.menu-item {
  padding: 2px 10px;
  color: #555;
  text-decoration: none;
  border-radius: 4px;
  font-size: 12px;
  transition: all 0.15s;
  cursor: pointer;
}

.menu-item:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #333;
}

.menu-item.active {
  background: rgba(25, 118, 210, 0.1);
  color: var(--primary);
  font-weight: 500;
}

.menu-right {
  display: flex;
  align-items: center;
  gap: 6px;
  -webkit-app-region: no-drag;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ccc;
}

.status-dot.online {
  background: #34c759;
  box-shadow: 0 0 4px rgba(52, 199, 89, 0.5);
}

.status-dot.offline {
  background: #ff3b30;
}

.status-text {
  font-size: 11px;
  color: #888;
}
</style>
