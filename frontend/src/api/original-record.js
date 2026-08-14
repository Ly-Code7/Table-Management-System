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
  return request.post('/original-record/import', formData, {
    // 大批量导入（几万条 + 未过保物料下推）耗时可能超过默认 30s，放宽到 10 分钟
    timeout: 600000
  })
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

export function lookup156(materialCode, companyId) {
  return request.get('/original-record/lookup-156', { params: { materialCode, companyId } })
}

export function lookupDeliveryRef(machineOnMaterial, recordDate, companyId) {
  return request.get('/original-record/lookup-delivery-ref', { params: { machineOnMaterial, recordDate, companyId } })
}

export function downloadTemplate() {
  return request.get('/original-record/template', {
    responseType: 'blob'
  })
}

/** 上传维修图片到 OSS，返回 { key }。id 为维修记录主键，非空时图片以 id 命名 */
export function uploadImage(file, id) {
  const formData = new FormData()
  formData.append('file', file)
  if (id) formData.append('id', id)
  return request.post('/original-record/upload-image', formData, {
    timeout: 60000
  })
}

/** 按 key 生成 OSS 临时访问 URL（1 小时有效） */
export function getImageUrl(key) {
  return request.get('/original-record/image-url', { params: { key } })
}

/** 删除 OSS 图片（换图清理用） */
export function removeImage(key) {
  return request.delete('/original-record/image', { params: { key } })
}
