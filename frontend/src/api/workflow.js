import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// Workflow Template API
export const workflowTemplateApi = {
  list() {
    return api.get('/workflow-templates')
  },
  get(id) {
    return api.get(`/workflow-templates/${id}`)
  },
  create(data) {
    return api.post('/workflow-templates', data)
  },
  update(id, data) {
    return api.put(`/workflow-templates/${id}`, data)
  },
  delete(id) {
    return api.delete(`/workflow-templates/${id}`)
  }
}

// Workflow Execution API
export const workflowExecutionApi = {
  list(templateId) {
    return api.get('/workflow-executions', { params: { templateId } })
  },
  get(id) {
    return api.get(`/workflow-executions/${id}`)
  },
  create(templateId) {
    return api.post('/workflow-executions', { templateId })
  },
  start(id) {
    return api.post(`/workflow-executions/${id}/start`)
  },
  updateStatus(id, status) {
    return api.put(`/workflow-executions/${id}/status`, { status })
  },
  updateNodeStatus(id, nodeId, status, detail) {
    return api.put(`/workflow-executions/${id}/nodes/${nodeId}/status`, { status, detail })
  },
  heartbeat(id) {
    return api.post(`/workflow-executions/${id}/heartbeat`)
  }
}

export default api
