import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// Task CRUD
export const taskApi = {
  list() {
    return api.get('/tasks')
  },
  get(id) {
    return api.get(`/tasks/${id}`)
  },
  create(data) {
    return api.post('/tasks', data)
  },
  update(id, data) {
    return api.put(`/tasks/${id}`, data)
  },
  delete(id) {
    return api.delete(`/tasks/${id}`)
  }
}

// Flow management
export const flowApi = {
  get(taskId) {
    return api.get(`/flow/${taskId}`)
  },
  save(taskId, data) {
    return api.put(`/flow/${taskId}`, data)
  }
}

// Flow data & status
export const flowDataApi = {
  emit(taskId, nodeId, data) {
    return api.post('/flow-data/emit', { taskId, nodeId, data })
  },
  getOutput(nodeId) {
    return api.get(`/flow-data/output/${nodeId}`)
  },
  getNodeStatuses(taskId) {
    return api.get(`/flow-data/node-statuses/${taskId}`)
  }
}

export default api
