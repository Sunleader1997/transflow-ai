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
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
