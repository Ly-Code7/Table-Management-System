import request from './request'

export function getList(params) {
  return request.get('/delivery-record', { params })
}

export function getDetail(id) {
  return request.get(`/delivery-record/${id}`)
}

export function getCopy(id) {
  return request.get(`/delivery-record/copy/${id}`)
}

export function create(data) {
  return request.post('/delivery-record', data)
}

export function update(id, data) {
  return request.put(`/delivery-record/${id}`, data)
}

export function remove(id) {
  return request.delete(`/delivery-record/${id}`)
}

export function batchDelete(ids) {
  return request.post('/delivery-record/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/delivery-record/import', formData)
}

export function exportExcel(params) {
  return request.get('/delivery-record/export', {
    params,
    responseType: 'blob'
  })
}

export function downloadTemplate() {
  return request.get('/delivery-record/template', {
    responseType: 'blob'
  })
}

export function getMaterials(params) {
  return request.get('/material/search', { params })
}

export function lookupByCode(materialCode) {
  return request.get('/delivery-record/lookup-by-code', { params: { materialCode } })
}
