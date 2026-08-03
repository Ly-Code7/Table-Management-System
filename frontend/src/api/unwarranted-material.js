import request from './request'

export function getList(params) {
  return request.get('/unwarranted-material', { params })
}

export function getDetail(id) {
  return request.get(`/unwarranted-material/${id}`)
}

export function create(data) {
  return request.post('/unwarranted-material', data)
}

export function update(id, data) {
  return request.put(`/unwarranted-material/${id}`, data)
}

export function remove(id) {
  return request.delete(`/unwarranted-material/${id}`)
}

export function batchDelete(ids) {
  return request.post('/unwarranted-material/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/unwarranted-material/import', formData)
}

export function exportExcel(params) {
  return request.get('/unwarranted-material/export', {
    params,
    responseType: 'blob'
  })
}

export function lookupOriginal(id, companyId) {
  return request.get('/unwarranted-material/lookup-original', { params: { id, companyId } })
}

export function compute(params) {
  return request.get('/unwarranted-material/compute', { params })
}

export function downloadTemplate() {
  return request.get('/unwarranted-material/template', {
    responseType: 'blob'
  })
}
