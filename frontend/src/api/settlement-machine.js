import request from './request'

export function getList(params) {
  return request.get('/settlement-machine', { params })
}

export function getDetail(id) {
  return request.get(`/settlement-machine/${id}`)
}

export function create(data) {
  return request.post('/settlement-machine', data)
}

export function update(id, data) {
  return request.put(`/settlement-machine/${id}`, data)
}

export function remove(id) {
  return request.delete(`/settlement-machine/${id}`)
}

export function batchDelete(ids) {
  return request.post('/settlement-machine/batch-delete', { ids })
}

export function importExcel(file, companyId) {
  const formData = new FormData()
  formData.append('file', file)
  if (companyId) formData.append('companyId', companyId)
  return request.post('/settlement-machine/import', formData)
}

export function exportExcel(params) {
  return request.get('/settlement-machine/export', {
    params,
    responseType: 'blob'
  })
}

export function lookup156(materialCode, companyId) {
  return request.get('/settlement-machine/lookup-156', { params: { materialCode, companyId } })
}

export function downloadTemplate() {
  return request.get('/settlement-machine/template', {
    responseType: 'blob'
  })
}
