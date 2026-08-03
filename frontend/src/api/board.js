import request from './request'

export function getRepairAmount(companyId, year) {
  return request.get('/board/repair-amount', { params: { companyId, year } })
}

export function getFaultFrequency(companyId, year) {
  return request.get('/board/fault-frequency', { params: { companyId, year } })
}

export function getMaterialFrequency(companyId, year) {
  return request.get('/board/material-frequency', { params: { companyId, year } })
}

export function getRepairFrequency(companyId, year) {
  return request.get('/board/repair-frequency', { params: { companyId, year } })
}

export function getRepairRate(companyId, year) {
  return request.get('/board/repair-rate', { params: { companyId, year } })
}

export function exportBoard(board, companyId, year) {
  return request.get('/board/export', {
    params: { board, companyId, year },
    responseType: 'blob'
  })
}
