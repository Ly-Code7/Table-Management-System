import request from './request'

export function getList(params) {
  return request.get('/machine-material', { params })
}

export function getDetail(id) {
  return request.get(`/machine-material/${id}`)
}

export function create(data) {
  return request.post('/machine-material', data)
}

export function update(id, data) {
  return request.put(`/machine-material/${id}`, data)
}

export function remove(id) {
  return request.delete(`/machine-material/${id}`)
}

export function batchDelete(ids) {
  return request.post('/machine-material/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/machine-material/import', formData)
}

export function exportExcel(params) {
  return request.get('/machine-material/export', {
    params,
    responseType: 'blob'
  })
}

export function lookupWarranty(machineOffMaterial) {
  return request.get('/machine-material/lookup-warranty', { params: { machineOffMaterial } })
}

export function lookupDelivery(machineOnMaterial) {
  return request.get('/machine-material/lookup-delivery', { params: { machineOnMaterial } })
}

export function downloadTemplate() {
  return request.get('/machine-material/template', {
    responseType: 'blob'
  })
}
