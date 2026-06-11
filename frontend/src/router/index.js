import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/tasks'
  },
  {
    path: '/tasks',
    name: 'TaskList',
    component: () => import('../views/TaskList.vue')
  },
  {
    path: '/flow/:taskId',
    name: 'FlowEditor',
    component: () => import('../views/FlowEditor.vue'),
    props: true
  },
  {
    path: '/workflows',
    name: 'WorkflowList',
    component: () => import('../views/WorkflowList.vue')
  },
  {
    path: '/workflow/:templateId',
    name: 'WorkflowEditor',
    component: () => import('../views/WorkflowEditor.vue'),
    props: true
  },
  {
    path: '/workflow/:templateId/history',
    name: 'ExecutionHistory',
    component: () => import('../views/ExecutionHistory.vue'),
    props: true
  },
  {
    path: '/workflow/execution/:executionId',
    name: 'ExecutionDetail',
    component: () => import('../views/ExecutionDetail.vue'),
    props: true
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
