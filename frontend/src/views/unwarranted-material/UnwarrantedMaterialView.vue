<template>
  <div class="page-content">
    <PageHeader title="未过保物料" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="机台号/料号/配件/维修人…" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="厂房">
        <el-input v-model="searchForm.factory" placeholder="厂房" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="未过保">
        <el-select v-model="searchForm.warrantyStatus" placeholder="全部" clearable style="width: 120px">
          <el-option label="未过保" value="未过保" />
          <el-option label="已过保" value="已过保" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="searchForm.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
    </SearchForm>

    <ToolBar :selected-count="selectedRows.length" @add="handleAdd" @batch-delete="batchDelete" @import="handleImport" @export="handleExport" @template="handleTemplateDownload" />

    <el-table :data="list" v-loading="loading" border stripe @selection-change="handleSelectionChange" @sort-change="handleSortChange">
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column label="序号" width="70" prop="id" sortable="custom">
        <template #default="{ $index }">{{ sortOrder === 'desc' ? total - (queryParams.page - 1) * queryParams.pageSize - $index : (queryParams.page - 1) * queryParams.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="recordDate" label="日期" width="105" sortable="custom" />
      <el-table-column prop="factory" label="厂房" width="80" />
      <el-table-column prop="machineNo" label="机台号" width="90" />
      <el-table-column prop="plantMachine" label="厂房+机台号" width="120" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="130" show-overflow-tooltip />
      <el-table-column prop="category" label="类别" width="110" show-overflow-tooltip />
      <el-table-column prop="partName" label="配件名称" width="120" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="60" />
      <el-table-column label="未过保" width="90">
        <template #default="{ row }">
          <el-tag :type="warrantyTagType(row.warrantyStatus)" size="small">{{ row.warrantyStatus || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="occurrenceNo" label="第几次" width="70" />
      <el-table-column prop="totalCount" label="总次数" width="70" />
      <el-table-column prop="lastDate" label="上次日期" width="105" />
      <el-table-column prop="currentDate" label="本次日期" width="105" />
      <el-table-column prop="overSixMonths" label="超六个月" width="90" />
      <el-table-column prop="usageMonths" label="使用时长/月" width="100" />
      <el-table-column prop="lastRepairPerson" label="上次维修人" width="100" />
      <el-table-column prop="repairAmount" label="维修金额" width="100" />
      <el-table-column prop="createdBy" label="创建人" width="90" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑未过保物料' : (isCopy ? '复制未过保物料' : '新增未过保物料')" width="860px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <!-- 关联维修记录 -->
        <el-form-item label="关联维修记录">
          <div class="original-picker-row">
            <el-button type="primary" @click="pickerVisible = true">选择维修记录</el-button>
            <span v-if="pickedDesc" class="picked-desc">{{ pickedDesc }}</span>
          </div>
        </el-form-item>

        <!-- 基础字段 -->
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker v-model="form.recordDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厂房" prop="factory">
              <el-input v-model="form.factory" placeholder="厂房" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="机台号" prop="machineNo">
              <el-input v-model="form.machineNo" placeholder="机台号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修人">
              <el-input v-model="form.repairPerson" placeholder="维修人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="维修物料装上">
              <el-input v-model="form.repairMaterialOn" placeholder="维修物料装上" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="未过保">
              <el-select v-model="form.warrantyStatus" placeholder="请选择" clearable style="width: 100%">
                <el-option label="未过保" value="未过保" />
                <el-option label="已过保" value="已过保" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="配件名称">
              <el-input v-model="form.partName" placeholder="配件名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量">
              <el-input-number v-model="form.quantity" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="类别">
              <el-input v-model="form.category" placeholder="按物料编码自动回填" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="维修金额（合约）">
              <el-input-number v-model="form.repairAmount" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="设备维修调试">
          <el-input v-model="form.equipRepairDebugging" type="textarea" :rows="2" placeholder="设备维修调试" />
        </el-form-item>

        <!-- 派生字段（只读） -->
        <el-divider content-position="left">自动计算字段</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="唯一标识编号">
              <el-input :model-value="form.uniqueId || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厂房+机台号">
              <el-input :model-value="form.plantMachine || ''" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="年+月">
              <el-input :model-value="form.yearMonth || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="第几次">
              <el-input :model-value="form.occurrenceNo ?? ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总次数">
              <el-input :model-value="form.totalCount ?? ''" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="本次日期">
              <el-input :model-value="form.currentDate || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上次日期">
              <el-input :model-value="form.lastDate || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上次维修人">
              <el-input :model-value="form.lastRepairPerson || ''" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="本次日期+编号">
              <el-input :model-value="form.currentDateNo || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上次日期+编号">
              <el-input :model-value="form.lastDateNo || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label=" ">
              <el-button type="primary" @click="refreshCompute">重新计算</el-button>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超六个月">
              <el-input :model-value="form.overSixMonths || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用时长/月">
              <el-input :model-value="form.usageMonths || ''" disabled />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 选择维修记录弹窗 -->
    <OriginalRecordPickerDialog v-model="pickerVisible" :company-id="companyStore.currentCompanyId" @select="handleOriginalPicked" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/unwarranted-material'
import { useCompanyStore } from '../../stores/company'
import { usePagination } from '../../composables/usePagination'
import { useTableSelection } from '../../composables/useTableSelection'
import { useCrud } from '../../composables/useCrud'
import { toSnakeCase, downloadBlob } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'
import ToolBar from '../../components/ToolBar.vue'
import OriginalRecordPickerDialog from '../../components/OriginalRecordPickerDialog.vue'

const companyStore = useCompanyStore()
const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => api.getList({ ...params, companyId: companyStore.currentCompanyId })
)
const { selectedRows, handleSelectionChange } = useTableSelection()
const { handleDelete, handleBatchDelete } = useCrud(api, doFetch)

const searchForm = reactive({ keyword: '', factory: '', warrantyStatus: '', dateRange: [] })
const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('desc')
const pickerVisible = ref(false)
const pickedDesc = ref('')

function today() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const defaultForm = {
  id: null,
  originalRecordId: null,
  category: '',
  recordDate: today(),
  factory: '',
  machineNo: '',
  equipRepairDebugging: '',
  repairMaterialOn: '',
  repairPerson: '',
  warrantyStatus: '',
  partName: '',
  quantity: null,
  materialCode: '',
  repairAmount: null,
  uniqueId: '',
  lastDateNo: '',
  currentDateNo: '',
  plantMachine: '',
  yearMonth: '',
  totalCount: null,
  occurrenceNo: null,
  lastDate: '',
  currentDate: '',
  overSixMonths: '',
  usageMonths: '',
  lastRepairPerson: ''
}
const form = reactive({ ...defaultForm })
const rules = {
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  factory: [{ required: true, message: '请输入厂房', trigger: 'blur' }],
  machineNo: [{ required: true, message: '请输入机台号', trigger: 'blur' }],
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }]
}

function doFetch() {
  return fetchData({
    ...searchForm,
    startDate: searchForm.dateRange?.[0] || undefined,
    endDate: searchForm.dateRange?.[1] || undefined,
    companyId: companyStore.currentCompanyId,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

function handleSearch() { queryParams.page = 1; doFetch() }
function handleReset() {
  Object.assign(searchForm, { keyword: '', factory: '', warrantyStatus: '', dateRange: [] })
  queryParams.page = 1; doFetch()
}

function handleSortChange({ prop, order }) {
  sortField.value = order ? toSnakeCase(prop) : 'id'
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  queryParams.page = 1; doFetch()
}

function resetForm() {
  Object.assign(form, { ...defaultForm, recordDate: today() })
  pickedDesc.value = ''
}
function handleAdd() { isEdit.value = false; isCopy.value = false; resetForm(); dialogVisible.value = true }

async function handleEdit(row) {
  isEdit.value = true; isCopy.value = false
  const res = await api.getDetail(row.id)
  Object.assign(form, res.data)
  pickedDesc.value = `${form.recordDate || ''} ${form.factory || ''} ${form.machineNo || ''}`
  dialogVisible.value = true
  refreshCompute()
}
async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getDetail(row.id)
  // 复制为新记录：一个维修记录只允许关联一条未过保物料，复制时清空关联
  Object.assign(form, { ...res.data, id: null, originalRecordId: null })
  pickedDesc.value = `${form.recordDate || ''} ${form.factory || ''} ${form.machineNo || ''}`
  dialogVisible.value = true
  refreshCompute()
}

// 选择维修记录 → 回填基础字段 → 重算派生字段
async function handleOriginalPicked(record) {
  try {
    const res = await api.lookupOriginal(record.id, companyStore.currentCompanyId)
    Object.assign(form, res.data)
    pickedDesc.value = `${form.recordDate || ''} ${form.factory || ''} ${form.machineNo || ''}`
    await refreshCompute()
    ElMessage.success('已回填维修记录信息')
  } catch { /* error handled */ }
}

// 派生字段预览
async function refreshCompute() {
  if (!form.recordDate) return
  try {
    const res = await api.compute({
      factory: form.factory,
      machineNo: form.machineNo,
      materialCode: form.materialCode,
      recordDate: form.recordDate,
      companyId: companyStore.currentCompanyId,
      excludeId: form.id || undefined
    })
    Object.assign(form, res.data)
  } catch { /* 预览失败不阻塞 */ }
}

// 关键字段变化防抖重算
let computeTimer = null
watch(
  [() => form.factory, () => form.machineNo, () => form.materialCode, () => form.recordDate],
  () => {
    if (computeTimer) clearTimeout(computeTimer)
    computeTimer = setTimeout(() => refreshCompute(), 500)
  }
)

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form, companyId: companyStore.currentCompanyId }
    delete data.id
    if (isEdit.value) await api.update(form.id, data)
    else await api.create(data)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false; doFetch()
  } finally { submitLoading.value = false }
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
      startDate: searchForm.dateRange?.[0] || undefined,
      endDate: searchForm.dateRange?.[1] || undefined,
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '未过保物料.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '未过保物料模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

function warrantyTagType(val) {
  if (val === '未过保') return 'success'
  if (val === '已过保') return 'danger'
  return 'info'
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.original-picker-row { display: flex; align-items: center; gap: 12px; }
.picked-desc { color: #909399; font-size: 12px; }
</style>
