<template>
  <div class="page-content">
    <PageHeader title="结算机台数" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="搜索" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="机型">
        <el-input v-model="searchForm.machineModel" placeholder="机型" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item label="统计月份">
        <el-date-picker v-model="searchForm.statMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" clearable style="width: 160px" />
      </el-form-item>
    </SearchForm>

    <ToolBar :selected-count="selectedRows.length" @add="handleAdd" @batch-delete="batchDelete" @import="handleImport" @export="handleExport" @template="handleTemplateDownload" />

    <el-table :data="list" v-loading="loading" border stripe @selection-change="handleSelectionChange" @sort-change="handleSortChange">
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column label="id" width="80" prop="id" sortable="custom">
        <template #default="{ $index }">{{ sortOrder === 'desc' ? total - (queryParams.page - 1) * queryParams.pageSize - $index : (queryParams.page - 1) * queryParams.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="materialCode" label="料号" width="130" sortable="custom" />
      <el-table-column prop="category" label="类别" width="300" />
      <el-table-column prop="partName" label="配件名称" width="120" show-overflow-tooltip />
      <el-table-column prop="unitUsage" label="单台用量" width="100" />
      <el-table-column prop="ratio" label="比例(%)" width="100">
        <template #default="{ row }">{{ row.ratio != null ? (row.ratio * 100).toFixed(2) + '%' : '' }}</template>
      </el-table-column>
      <el-table-column prop="unitPriceWithTax" label="含税单价" width="110" />
      <el-table-column prop="warrantyPeriod" label="质保期" width="90" />
      <el-table-column prop="priceType" label="价格类型" width="100" />
      <el-table-column prop="machineModel" label="机型" width="120" />
      <el-table-column prop="settlementMachineCount" label="结算机台数" width="120" />
      <el-table-column prop="statMonth" label="统计月份" width="100" />
      <el-table-column prop="remark" label="备注" width="150" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="100" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑结算机台数' : (isCopy ? '复制结算机台数' : '新增结算机台数')" width="800px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="料号" prop="materialCode">
              <el-autocomplete v-model="form.materialCode" :fetch-suggestions="searchMaterial156" placeholder="输入料号自动匹配156项" style="width:100%" @select="handleMaterialSelect" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-input v-model="form.category" placeholder="类别" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="配件名称" prop="partName">
              <el-input v-model="form.partName" placeholder="配件名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单台用量" prop="unitUsage">
              <el-input-number v-model="form.unitUsage" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="比例(%)" prop="ratio">
              <el-input-number v-model="form.ratio" :precision="2" :min="0" style="width: 100%">
                <template #suffix><span style="color:#909399">%</span></template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="含税单价" prop="unitPriceWithTax">
              <el-input-number v-model="form.unitPriceWithTax" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="质保期" prop="warrantyPeriod">
              <el-input v-model="form.warrantyPeriod" placeholder="质保期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格类型" prop="priceType">
              <el-select v-model="form.priceType" placeholder="请选择" clearable style="width:100%">
                <el-option label="新品价" value="新品价" />
                <el-option label="维修价" value="维修价" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="统计月份" prop="statMonth">
              <el-date-picker v-model="form.statMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机型" prop="machineModel">
              <el-input v-model="form.machineModel" placeholder="机型" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="结算机台数" prop="settlementMachineCount">
              <el-input-number v-model="form.settlementMachineCount" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label=" ">
              <el-button type="primary" @click="openMachineCountDialog">选择开机数量</el-button>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
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

    <!-- 开机数量选择弹窗 -->
    <el-dialog v-model="machineCountDialogVisible" title="选择开机数量" width="600px">
      <el-table :data="machineCountList" highlight-current-row @row-click="handleMachineCountSelect">
        <el-table-column prop="machineModel" label="机型" />
        <el-table-column prop="count" label="开机台数" />
      </el-table>
      <template #footer>
        <el-button @click="machineCountDialogVisible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/settlement-machine'
import { parseVoiceText } from '../../api/voice-parse'
import { search as search156Api } from '../../api/base-material-156'
import { getByMonth as getMachineCountByMonth } from '../../api/machine-count'
import { useCompanyStore } from '../../stores/company'
import { usePagination } from '../../composables/usePagination'
import { useTableSelection } from '../../composables/useTableSelection'
import { useCrud } from '../../composables/useCrud'
import { toSnakeCase, downloadBlob } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'
import ToolBar from '../../components/ToolBar.vue'

const companyStore = useCompanyStore()
const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => api.getList({ ...params, companyId: companyStore.currentCompanyId })
)
const { selectedRows, handleSelectionChange } = useTableSelection()
const { handleDelete, handleBatchDelete } = useCrud(api, doFetch)

const searchForm = reactive({ keyword: '', machineModel: '', statMonth: '' })
const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('desc')

const defaultForm = {
  id: null,
  materialCode: '',
  category: '',
  partName: '',
  unitUsage: null,
  ratio: null,
  unitPriceWithTax: null,
  warrantyPeriod: '6个月',
  priceType: '',
  statMonth: '',
  machineModel: '',
  settlementMachineCount: null,
  remark: ''
}
const form = reactive({ ...defaultForm })
const rules = {
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  partName: [{ required: true, message: '请输入配件名称', trigger: 'blur' }]
}

function doFetch() {
  return fetchData({
    ...searchForm,
    companyId: companyStore.currentCompanyId,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

function handleSearch() { queryParams.page = 1; doFetch() }
function handleReset() {
  Object.assign(searchForm, { keyword: '', machineModel: '', statMonth: '' })
  queryParams.page = 1; doFetch()
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
  const res = await api.getDetail(row.id); Object.assign(form, res.data)
  if (form.ratio != null) form.ratio = form.ratio * 100
  dialogVisible.value = true
}
async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getDetail(row.id)
  Object.assign(form, { ...res.data, id: null })
  if (form.ratio != null) form.ratio = form.ratio * 100
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form, companyId: companyStore.currentCompanyId }
    if (data.ratio != null) data.ratio = data.ratio / 100
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
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '结算机台数.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '结算机台数模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

// ---- 156项 autocomplete ----
async function searchMaterial156(query, cb) {
  if (!query || query.length < 1) { cb([]); return }
  try {
      const res = await search156Api(query, companyStore.currentCompanyId)
    const data = res.data || []
    cb(data.map(m => ({ value: m.materialCode, label: `${m.materialCode} - ${m.partName || m.systemName || ''}` })))
  } catch { cb([]) }
}

async function handleMaterialSelect(item) {
  form.materialCode = item.value
  // 查询156项表，回填关联字段
  try {
    const res = await api.lookup156(item.value)
    const d = res.data
    if (d && Object.keys(d).length > 0) {
      form.category = d.category || ''
      form.partName = d.partName || ''
      form.unitUsage = d.unitUsage ?? null
      form.ratio = d.ratio != null ? d.ratio * 100 : null
      form.unitPriceWithTax = d.unitPriceWithTax ?? null
    }
  } catch { /* 查不到就不回填 */ }
}

// ---- 开机数量弹窗 ----
const machineCountDialogVisible = ref(false)
const machineCountList = ref([])

async function openMachineCountDialog() {
  if (!form.statMonth) {
    ElMessage.warning('请先选择统计月份')
    return
  }
  try {
    const res = await getMachineCountByMonth(form.statMonth, companyStore.currentCompanyId)
    machineCountList.value = res.data || []
    machineCountDialogVisible.value = true
  } catch { ElMessage.error('查询开机数量失败') }
}

function handleMachineCountSelect(row) {
  form.machineModel = row.machineModel
  form.settlementMachineCount = row.count
  machineCountDialogVisible.value = false
}

const voiceText = ref('')
const voiceLoading = ref(false)
const voicePlaceholder = '请按格式朗读: 物料编码15297012400 类别风扇类 配件名称驱动风扇 单台机用量1.5 比例0.8 含税单价120 保修期6个月 价格类型新品价 机型发那科 结算机台数量1995 备注无'
async function handleVoiceParse() {
  if (!voiceText.value.trim()) { ElMessage.warning('请先输入文字'); return }
  voiceLoading.value = true
  try {
    const res = await parseVoiceText(voiceText.value.trim(), 'settlement-machine')
    const fields = res.data.fields || {}
    const fc = res.data.filledCount || 0
    if (!fc) { ElMessage.warning('未识别到有效字段，请检查格式'); return }
    const fm = {
      materialCode: 'materialCode', category: 'category', partName: 'partName',
      unitUsage: 'unitUsage', ratio: 'ratio', unitPriceWithTax: 'unitPriceWithTax',
      warrantyPeriod: 'warrantyPeriod', priceType: 'priceType', machineModel: 'machineModel',
      settlementMachineCount: 'settlementMachineCount', remark: 'remark'
    }
    for (const [k, v] of Object.entries(fields)) {
      if (fm[k] && v) {
        if (['unitUsage', 'ratio', 'unitPriceWithTax'].includes(k)) { const n = parseFloat(v); if (!isNaN(n)) form[fm[k]] = k === 'ratio' ? n * 100 : n }
        else if (k === 'settlementMachineCount') { const n = parseInt(v); if (!isNaN(n)) form[fm[k]] = n }
        else form[fm[k]] = v
      }
    }
    ElMessage.success(`已填充 ${fc} 个字段，请核对`)
  } catch { ElMessage.error('解析失败') }
  finally { voiceLoading.value = false }
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
