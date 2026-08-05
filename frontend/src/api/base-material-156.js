import request from './request'

export function getList(params) {
  return request.get('/base-material-156', { params })
}

export function search(keyword, companyId) {
  return request.get('/base-material-156/search', { params: { keyword, companyId } })
}

export function getDetail(id) {
  return request.get(`/base-material-156/${id}`)
}

export function create(data) {
  return request.post('/base-material-156', data)
}

export function update(id, data) {
  return request.put(`/base-material-156/${id}`, data)
}

export function remove(id) {
  return request.delete(`/base-material-156/${id}`)
}

export function batchDelete(ids) {
  return request.post('/base-material-156/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/base-material-156/import', formData)
}

export function exportExcel(params) {
  return request.get('/base-material-156/export', { params, responseType: 'blob' })
}

export function downloadTemplate() {
  return request.get('/base-material-156/template', { responseType: 'blob' })
}
