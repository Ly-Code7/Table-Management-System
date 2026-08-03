<template>
  <div class="page-content">
    <PageHeader title="维修记录" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="搜索" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="班次">
        <el-select v-model="searchForm.shift" placeholder="全部" clearable style="width: 100px">
          <el-option label="白班" value="白班" />
          <el-option label="夜班" value="夜班" />
        </el-select>
      </el-form-item>
      <el-form-item label="厂房">
        <el-input v-model="searchForm.factory" placeholder="厂房" clearable style="width: 120px" />
      </el-form-item>
      <el-form-item label="是否过保">
        <el-select v-model="searchForm.isOutOfWarranty" placeholder="全部" clearable style="width: 110px">
          <el-option label="未过保" value="未过保" />
          <el-option label="已过保" value="已过保" />
          <el-option label="无" value="无" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期范围">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
      </el-form-item>
    </SearchForm>

    <ToolBar :selected-count="selectedRows.length" @add="handleAdd" @batch-delete="batchDelete" @import="handleImport" @export="handleExport" @template="handleTemplateDownload" />

    <el-table :data="list" v-loading="loading" border stripe @selection-change="handleSelectionChange" @sort-change="handleSortChange">
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column label="序号" width="80" prop="id" sortable="custom" fixed="left">
        <template #default="{ $index }">{{ sortOrder === 'desc' ? total - (queryParams.page - 1) * queryParams.pageSize - $index : (queryParams.page - 1) * queryParams.pageSize + $index + 1 }}</template>
      </el-table-column>
      <!-- 日期信息 -->
      <el-table-column prop="recordDate" label="日期" width="105" sortable="custom" />
      <el-table-column prop="yearMonth" label="年月" width="85">
        <template #default="{ row }">
          {{ row.yearMonth || getYearMonth(row.recordDate) }}
        </template>
      </el-table-column>
      <!-- 班次 & 厂房 -->
      <el-table-column prop="shift" label="班次" width="75">
        <template #default="{ row }">
          <el-tag v-if="row.shift === '白班'" type="primary" size="small">白班</el-tag>
          <el-tag v-else-if="row.shift === '夜班'" type="info" size="small">夜班</el-tag>
          <span v-else>{{ row.shift }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="factory" label="厂房" width="80" />
      <!-- 机台信息 -->
      <el-table-column prop="machineNo" label="机台号" width="90" />
      <el-table-column prop="machineModel" label="机型" width="110" show-overflow-tooltip />
      <!-- 人员 -->
      <el-table-column prop="diagnostician" label="诊断人" width="80" />
      <el-table-column prop="repairPerson" label="维修人" width="80" />
      <el-table-column prop="confirmer" label="确认人" width="80" />
      <!-- 故障 -->
      <el-table-column prop="faultPhenomenon" label="故障现象" width="130" show-overflow-tooltip />
      <el-table-column prop="faultDescription" label="维修描述" width="130" show-overflow-tooltip />
      <!-- 物料 -->
      <el-table-column prop="materialCode" label="料号" width="115" sortable="custom" />
      <el-table-column prop="partName" label="配件" width="100" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="65" />
      <!-- 上/下机 -->
      <el-table-column prop="machineOnMaterial" label="上机物料号" width="120" show-overflow-tooltip />
      <el-table-column prop="machineOffMaterial" label="下机物料号" width="120" show-overflow-tooltip />
      <!-- 时间 -->
      <el-table-column label="报修时间" width="100">
        <template #default="{ row }">{{ formatTime(row.repairRequestTime) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="100">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="100">
        <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="lastMachineOnTime" label="上次上机时间" width="110" />
      <!-- 工时 & 过保 -->
      <el-table-column prop="repairHours" label="维修工时" width="90" />
      <el-table-column prop="downtimeHours" label="停机工时" width="90" />
      <el-table-column prop="isOutOfWarranty" label="是否过保" width="90">
        <template #default="{ row }">
          <el-tag :type="warrantyTagType(row.isOutOfWarranty)" size="small">{{ row.isOutOfWarranty || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deliveryRecordRef" label="送货记录引用" width="140" show-overflow-tooltip />
      <el-table-column prop="documentNo" label="单据号" width="110" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="操作人" width="80" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" @click="handleCopy(row)">复制</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.pageSize" :page-sizes="[20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑维修记录' : (isCopy ? '复制维修记录' : '新增维修记录')" width="900px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="班次" prop="shift">
              <el-select v-model="form.shift" style="width:100%"><el-option label="白班" value="白班" /><el-option label="夜班" value="夜班" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="厂房" prop="factory">
              <el-input v-model="form.factory" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="序列号" prop="serialNumber">
              <el-input v-model="form.serialNumber" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="机台号" prop="machineNo">
              <el-input v-model="form.machineNo" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="机型" prop="machineModel">
              <el-input v-model="form.machineModel" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="诊断人" prop="diagnostician">
              <el-input v-model="form.diagnostician" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="维修人" prop="repairPerson">
              <el-input v-model="form.repairPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="确认人" prop="confirmer">
              <el-input v-model="form.confirmer" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="报修时间" prop="repairRequestTime">
              <el-time-picker v-model="form.repairRequestTime" format="HH:mm" value-format="HH:mm" placeholder="选择时间" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" placeholder="选择时间" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" placeholder="选择时间" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="故障现象" prop="faultPhenomenon">
              <el-input v-model="form.faultPhenomenon" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修描述" prop="faultDescription">
              <el-input v-model="form.faultDescription" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="料号" prop="materialCode">
              <el-autocomplete v-model="form.materialCode" :fetch-suggestions="searchMaterials" placeholder="输入料号关键字自动匹配" style="width:100%" @select="handleMaterialSelect" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配件名称" prop="partName">
              <el-input v-model="form.partName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="上机物料号" prop="machineOnMaterial">
              <el-input v-model="form.machineOnMaterial" @blur="handleMachineOnMaterialBlur" />
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="下机物料号" prop="machineOffMaterial">
              <el-input v-model="form.machineOffMaterial" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上次上机时间">
              <el-input :model-value="warrantyInfo.lastMachineOnTime || '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否过保">
              <el-input :model-value="warrantyInfo.isOutOfWarranty || '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="送货记录引用" prop="deliveryRecordRef">
              <el-input v-model="form.deliveryRecordRef" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单据号">
              <el-input v-model="form.documentNo" placeholder="非必填" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="OCR图片识别">
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <el-upload
              :auto-upload="false"
              list-type="picture"
              :limit="1"
              accept="image/jpeg,image/png"
              :before-upload="beforeOcrUpload"
              :on-change="handleOcrFileChange"
              :on-exceed="handleOcrExceed"
              :file-list="ocrFileList"
              :disabled="ocrLoading"
            >
              <el-button type="primary" :disabled="ocrLoading">选择图片</el-button>
              <template #tip>
                <div style="font-size:12px;color:#909399;margin-top:4px">支持 jpg/png，大小不超过 10MB</div>
              </template>
            </el-upload>
            <el-button type="primary" @click="handleOcrRecognize" :loading="ocrLoading" :disabled="!ocrImageFile">AI解析</el-button>
          </div>
        </el-form-item>
        <el-form-item label="语音输入">
          <el-input v-model="voiceText" type="textarea" :rows="3" :placeholder="voicePlaceholder" />
          <el-button type="primary" size="small" style="margin-top:8px" @click="handleVoiceParse" :loading="voiceLoading">解析</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/original-record'
import { parseVoiceText } from '../../api/voice-parse'
import { recognizeOcr } from '../../api/ocr'
import { search as search156Api } from '../../api/base-material-156'
import { lookupBySerial } from '../../api/delivery-record'
import { useCompanyStore } from '../../stores/company'
import { usePagination } from '../../composables/usePagination'
import { useTableSelection } from '../../composables/useTableSelection'
import { useCrud } from '../../composables/useCrud'
import { toSnakeCase, getYearMonth, downloadBlob } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'
import ToolBar from '../../components/ToolBar.vue'

const companyStore = useCompanyStore()
const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => api.getList({ ...params, companyId: companyStore.currentCompanyId })
)
const { selectedRows, handleSelectionChange } = useTableSelection()
const { handleDelete, handleBatchDelete } = useCrud(api, doFetch)

const searchForm = reactive({ keyword: '', shift: '', factory: '', isOutOfWarranty: '' })
const dateRange = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('desc')

const defaultForm = {
  id: null, recordDate: '', shift: '', factory: '', serialNumber: '', machineNo: '',
  diagnostician: '', repairPerson: '', confirmer: '', repairRequestTime: '', startTime: '',
  endTime: '', machineModel: '', faultPhenomenon: '', faultDescription: '',
  materialCode: '', partName: '', quantity: null, machineOnMaterial: '', machineOffMaterial: '',
  remark: '', deliveryRecordRef: '', documentNo: ''
}
const form = reactive({ ...defaultForm })
const rules = {
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  factory: [{ required: true, message: '请输入厂房', trigger: 'blur' }]
}

function doFetch() {
  return fetchData({
    ...searchForm,
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
    companyId: companyStore.currentCompanyId,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

function handleSearch() { queryParams.page = 1; doFetch() }
function handleReset() {
  Object.assign(searchForm, { keyword: '', shift: '', factory: '', isOutOfWarranty: '' })
  dateRange.value = []; queryParams.page = 1; doFetch()
}

function handleSortChange({ prop, order }) {
  sortField.value = order ? toSnakeCase(prop) : 'id'
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  queryParams.page = 1; doFetch()
}

function resetForm() { Object.assign(form, { ...defaultForm }) }
function handleAdd() { isEdit.value = false; isCopy.value = false; resetForm(); dialogVisible.value = true }
async function handleEdit(row) {
  isEdit.value = true; isCopy.value = false
  const res = await api.getDetail(row.id)
  const d = { ...res.data,
    repairRequestTime: extractTime(res.data.repairRequestTime),
    startTime: extractTime(res.data.startTime),
    endTime: extractTime(res.data.endTime) }
  Object.assign(form, d)
  dialogVisible.value = true
}
async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getCopy(row.id)
  const d = { ...res.data, id: null,
    repairRequestTime: extractTime(res.data.repairRequestTime),
    startTime: extractTime(res.data.startTime),
    endTime: extractTime(res.data.endTime) }
  Object.assign(form, d)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const dateStr = form.recordDate || ''
    const data = { ...form, companyId: companyStore.currentCompanyId,
      repairRequestTime: form.repairRequestTime ? dateStr + ' ' + form.repairRequestTime : '',
      startTime: form.startTime ? dateStr + ' ' + form.startTime : '',
      endTime: form.endTime ? dateStr + ' ' + form.endTime : '' }
    delete data.id
    if (isEdit.value) await api.update(form.id, data)
    else await api.create(data)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false; doFetch()
  } finally { submitLoading.value = false }
}

function extractTime(dt) {
  if (!dt) return ''
  // 从 "2026-07-18 12:00" 或 "2026-07-18T12:00:00" 提取 HH:mm
  const m = dt.match(/(\d{2}:\d{2})/)
  return m ? m[1] : ''
}

function formatTime(dt) {
  if (!dt) return ''
  const m = dt.match(/(\d{2}:\d{2})/)
  return m ? m[1] : dt
}

async function searchMaterials(query, cb) {
  if (!query || query.length < 1) { cb([]); return }
  try {
    const res = await search156Api(query)
    const data = res.data || []
    cb(data.map(m => ({ value: m.materialCode, label: `${m.materialCode} - ${m.partName || m.systemName || ''}` })))
  } catch { cb([]) }
}

async function handleMaterialSelect(item) {
  form.materialCode = item.value
  // 查询156项表，回填配件名称
  try {
    const res = await api.lookup156(item.value)
    if (res.data && res.data.partName) {
      form.partName = res.data.partName
    }
  } catch { /* 查不到就不回填 */ }
}

// 过保实时查询
const warrantyInfo = reactive({ lastMachineOnTime: '', isOutOfWarranty: '' })
let warrantyTimer = null
watch(() => form.machineOffMaterial, (newVal) => {
  if (warrantyTimer) clearTimeout(warrantyTimer)
  if (!newVal || newVal.trim() === '') {
    warrantyInfo.lastMachineOnTime = ''
    warrantyInfo.isOutOfWarranty = ''
    return
  }
  warrantyTimer = setTimeout(async () => {
    try {
      const res = await api.lookupWarranty(newVal, form.recordDate || undefined)
      const data = res.data
      warrantyInfo.lastMachineOnTime = data.lastMachineOnTime || ''
      warrantyInfo.isOutOfWarranty = data.isOutOfWarranty || ''
    } catch {
      warrantyInfo.lastMachineOnTime = ''
      warrantyInfo.isOutOfWarranty = ''
    }
  }, 500)
})

async function handleMachineOnMaterialBlur() {
  const serial = form.machineOnMaterial
  if (!serial || serial.trim() === '') return
  try {
    // 回填料号
    const res = await lookupBySerial(serial.trim(), companyStore.currentCompanyId)
    const data = res.data
    if (data && data.materialCode) {
      form.materialCode = data.materialCode
      if (data.partName && !form.partName) {
        form.partName = data.partName
      }
    }
    // 回填本月送货记录匹配数到"送货记录引用"
    if (!form.deliveryRecordRef) {
      const date = form.recordDate || new Date().toISOString().slice(0, 10)
      const refRes = await api.lookupDeliveryRef(serial.trim(), date, companyStore.currentCompanyId)
      const count = refRes.data?.count || 0
      if (count > 0) {
        form.deliveryRecordRef = String(count)
      }
    }
  } catch {
    // 查询失败不提示，静默处理
  }
}

function warrantyTagType(val) {
  if (val === '未过保') return 'success'
  if (val === '已过保') return 'danger'
  return 'info'
}

function batchDelete() {
  handleBatchDelete(selectedRows.value.map(r => r.id))
}

function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const res = await api.importExcel(file, companyStore.currentCompanyId)
      const d = res.data
      ElMessage.success(`导入完成：成功 ${d.success} 条，失败 ${d.fail} 条`)
      doFetch()
    } catch { /* error handled in interceptor */ }
  }
  input.click()
}

async function handleExport() {
  try {
    const response = await api.exportExcel({
      ...searchForm,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '维修记录.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '维修记录模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

// 字段映射（语音识别 & OCR 识别共用）
const fieldMap = {
  recordDate: 'recordDate', shift: 'shift', factory: 'factory',
  serialNumber: 'serialNumber', machineNo: 'machineNo', machineModel: 'machineModel',
  diagnostician: 'diagnostician', repairPerson: 'repairPerson', confirmer: 'confirmer',
  repairRequestTime: 'repairRequestTime', startTime: 'startTime', endTime: 'endTime',
  faultPhenomenon: 'faultPhenomenon', faultDescription: 'faultDescription',
  materialCode: 'materialCode', partName: 'partName', quantity: 'quantity',
  machineOnMaterial: 'machineOnMaterial', machineOffMaterial: 'machineOffMaterial',
  remark: 'remark', deliveryRecordRef: 'deliveryRecordRef', documentNo: 'documentNo'
}

// OCR 图片识别
const ocrImageFile = ref(null)
const ocrLoading = ref(false)
const ocrFileList = ref([])

// 语音输入
const voiceText = ref('')
const voiceLoading = ref(false)
const voicePlaceholder = '请按格式朗读: 日期2026年7月21日 班次白班 厂房A 序列号001 机台号ESS 机型FANUC 诊断人张三 维修人李四 确认人王五 报修时间15时 开始时间15时30分 结束时间18时 故障现象主轴异响 维修描述更换丝杆 料号2212673-0461 配件名称丝杆 数量1 上机物料号M001 下机物料号M002 备注无'

async function handleVoiceParse() {
  if (!voiceText.value.trim()) { ElMessage.warning('请先输入文字'); return }
  voiceLoading.value = true
  try {
    const res = await parseVoiceText(voiceText.value.trim(), 'original-record')
    const fields = res.data.fields || {}
    const filledCount = res.data.filledCount || 0
    if (filledCount === 0) { ElMessage.warning('未识别到有效字段，请检查格式'); return }
    for (const [k, v] of Object.entries(fields)) {
      if (fieldMap[k] && v) {
        if (k === 'quantity') { const n = parseInt(v); if (!isNaN(n)) form[fieldMap[k]] = n }
        else if (['repairRequestTime', 'startTime', 'endTime'].includes(k)) {
          const m = v.match(/(\d{1,2})时(\d{1,2})?/)
          if (m) form[fieldMap[k]] = m[1].padStart(2, '0') + ':' + (m[2] || '00').padStart(2, '0')
        }
        else if (k === 'recordDate') {
          form[fieldMap[k]] = convertDate(v)
        }
        else form[fieldMap[k]] = v
      }
    }
    ElMessage.success(`已填充 ${filledCount} 个字段，请核对`)
  } catch { ElMessage.error('解析失败') }
  finally { voiceLoading.value = false }
}

function convertDate(str) {
  const m = str.match(/(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日/)
  if (m) return m[1] + '-' + m[2].padStart(2, '0') + '-' + m[3].padStart(2, '0')
  return str
}


// OCR 图片识别
function handleOcrFileChange(uploadFile, uploadFiles) {
  ocrFileList.value = uploadFiles
  ocrImageFile.value = uploadFiles.length > 0 ? uploadFiles[0].raw : null
}

function handleOcrExceed() {
  ElMessage.warning('请先移除当前图片再上传')
}

function beforeOcrUpload(file) {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png'
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isImage) { ElMessage.error('仅支持 jpg/png 格式'); return false }
  if (!isLt10M) { ElMessage.error('图片大小不能超过 10MB'); return false }
  return false
}

async function handleOcrRecognize() {
  if (!ocrImageFile.value) { ElMessage.warning('请先选择图片'); return }
  ocrLoading.value = true
  try {
    const res = await recognizeOcr(ocrImageFile.value)
    const fields = res.data.fields || {}
    const filledCount = res.data.filledCount || 0
    if (filledCount === 0) { ElMessage.warning('未识别到有效字段'); return }
    for (const [k, v] of Object.entries(fields)) {
      if (fieldMap[k] && v) {
        if (k === 'quantity') { const n = parseInt(v); if (!isNaN(n)) form[fieldMap[k]] = n }
        else if (['repairRequestTime', 'startTime', 'endTime'].includes(k)) {
          const m = v.match(/(\d{1,2})时(\d{1,2})?/)
          if (m) form[fieldMap[k]] = m[1].padStart(2, '0') + ':' + (m[2] || '00').padStart(2, '0')
        }
        else if (k === 'recordDate') {
          form[fieldMap[k]] = convertDate(v)
        }
        else form[fieldMap[k]] = v
      }
    }
    ElMessage.success(`已填充 ${filledCount} 个字段，请核对`)
  } catch { ElMessage.error('OCR识别失败') }
  finally { ocrLoading.value = false }
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
