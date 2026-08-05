import request from './request'

export function getList(params) {
  return request.get('/machine-count', { params })
}

export function getDetail(id) {
  return request.get(`/machine-count/${id}`)
}

export function create(data) {
  return request.post('/machine-count', data)
}

export function update(id, data) {
  return request.put(`/machine-count/${id}`, data)
}

export function remove(id) {
  return request.delete(`/machine-count/${id}`)
}

export function batchDelete(ids) {
  return request.post('/machine-count/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/machine-count/import', formData)
}

export function exportExcel(params) {
  return request.get('/machine-count/export', {
    params,
    responseType: 'blob'
  })
}

export function clearByMonth(statMonth, companyId) {
  return request.post('/machine-count/clear-by-month', null, { params: { statMonth, companyId } })
}

export function getByMonth(statMonth, companyId) {
  return request.get('/machine-count/by-month', { params: { statMonth, companyId } })
}

export function downloadTemplate() {
  return request.get('/machine-count/template', {
    responseType: 'blob'
  })
}
