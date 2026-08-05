import request from './request'

export function getList(params) {
  return request.get('/material', { params })
}

export function search(keyword, companyId) {
  return request.get('/material/search', { params: { keyword, companyId } })
}

export function getDetail(id) {
  return request.get(`/material/${id}`)
}

export function create(data) {
  return request.post('/material', data)
}

export function update(id, data) {
  return request.put(`/material/${id}`, data)
}

export function remove(id) {
  return request.delete(`/material/${id}`)
}

export function batchDelete(ids) {
  return request.post('/material/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/material/import', formData)
}

export function exportExcel(params) {
  return request.get('/material/export', {
    params,
    responseType: 'blob'
  })
}

export function downloadTemplate() {
  return request.get('/material/template', {
    responseType: 'blob'
  })
}
