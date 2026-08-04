import request from './request'

export function getList(params) {
  return request.get('/original-record', { params })
}

export function getDetail(id) {
  return request.get(`/original-record/${id}`)
}

export function getCopy(id) {
  return request.get(`/original-record/copy/${id}`)
}

export function create(data) {
  return request.post('/original-record', data)
}

export function update(id, data) {
  return request.put(`/original-record/${id}`, data)
}

export function remove(id) {
  return request.delete(`/original-record/${id}`)
}

export function linkedCount(id) {
  return request.get(`/original-record/${id}/linked-count`)
}

export function linkedCounts(ids) {
  return request.post('/original-record/linked-counts', { ids })
}

export function batchDelete(ids) {
  return request.post('/original-record/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/original-record/import', formData)
}

export function exportExcel(params) {
  return request.get('/original-record/export', {
    params,
    responseType: 'blob'
  })
}

export function lookupWarranty(machineOffMaterial, recordDate) {
  return request.get('/original-record/lookup-warranty', { params: { machineOffMaterial, recordDate } })
}

export function lookup156(materialCode) {
  return request.get('/original-record/lookup-156', { params: { materialCode } })
}

export function lookupDeliveryRef(machineOnMaterial, recordDate, companyId) {
  return request.get('/original-record/lookup-delivery-ref', { params: { machineOnMaterial, recordDate, companyId } })
}

export function downloadTemplate() {
  return request.get('/original-record/template', {
    responseType: 'blob'
  })
}
