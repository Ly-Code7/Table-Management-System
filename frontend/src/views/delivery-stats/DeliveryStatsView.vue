<template>
  <div class="page-content">
    <PageHeader title="送货超比统计" />

    <!-- 搜索筛选 -->
    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="搜索" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="类别">
        <el-input v-model="searchForm.category" placeholder="类别" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="年月">
        <el-input v-model="searchForm.yearMonth" placeholder="年月" clearable style="width: 140px" />
      </el-form-item>
    </SearchForm>

    <!-- 操作栏 -->
    <ToolBar
      :selected-count="selectedRows.length"
      @add="handleAdd"
      @batch-delete="batchDelete"
      @import="handleImport"
      @export="handleExport"
      @template="handleTemplateDownload"
    />

    <div style="margin-bottom:12px;display:flex;align-items:center;gap:12px">
      <el-date-picker v-model="refreshMonth" type="month" placeholder="选择要更新的月份" value-format="YYYY-MM" style="width:180px" />
      <el-button type="warning" @click="handleBatchRefresh">更新当月数据</el-button>
    </div>

    <!-- 全表合计 -->
    <div class="summary-row">
      <span class="summary-label">汇总（万元）</span>
      <el-date-picker v-model="summaryMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" clearable style="width:160px" @change="fetchTotals" />
      <span class="summary-item">送货金额合计：<b>{{ totals.deliveryAmount.toFixed(2) }}</b></span>
      <span class="summary-item">上机金额合计：<b>{{ totals.machineOnAmount.toFixed(2) }}</b></span>
      <span class="summary-item">返修金额合计：<b>{{ totals.repairAmount.toFixed(2) }}</b></span>
      <span class="summary-item">比例内金额合计：<b>{{ totals.agreedRatioAmount.toFixed(2) }}</b></span>
      <span class="summary-item">超比金额合计：<b>{{ totals.excessAmount.toFixed(2) }}</b></span>
      <span class="summary-item">超比含税总额：<b>{{ totals.excessTaxAmount.toFixed(2) }}</b></span>
    </div>

    <!-- 数据表格 -->
    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      style="width: 100%"
    >
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column prop="id" label="ID" width="64" sortable="custom" />
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="materialCode" label="料号" width="130" show-overflow-tooltip />
      <el-table-column prop="systemName" label="系统名称" width="120" show-overflow-tooltip />
      <el-table-column prop="partName" label="配件名称" width="120" show-overflow-tooltip />
      <el-table-column prop="unitUsage" label="单台机用量" width="100" />
      <el-table-column prop="ratio" label="比例(%)" width="100">
        <template #default="{ row }">{{ row.ratio != null ? (row.ratio * 100).toFixed(2) + '%' : '' }}</template>
      </el-table-column>
      <el-table-column prop="unitPriceWithTax" label="含税单价" width="100" />
      <el-table-column prop="machineCount" label="机台数" width="80" />
      <el-table-column prop="deliveryQuantity" label="送货数量" width="90" />
      <el-table-column prop="machineOnQuantity" label="上机数量" width="90" />
      <el-table-column prop="monthRepair" label="当月返修" width="90" />
      <el-table-column prop="agreedRatioQuantity" label="约定比例数量" width="120" />
      <el-table-column prop="excessQuantity" label="超比数量" width="90" />
      <el-table-column prop="excessAmountWithTax" label="超比含税金额" width="120" />
      <el-table-column prop="statDate" label="统计日期" width="110" />
      <el-table-column prop="yearMonth" label="年月" width="90" />
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" size="small" @click="handleCopy(row)">复制</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑送货超比统计' : (isCopy ? '复制送货超比统计' : '新增送货超比统计')"
      width="1000px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" size="default">
        <!-- 主表单 - 3列布局 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="料号" prop="materialCode">
              <el-autocomplete v-model="form.materialCode" :fetch-suggestions="searchMaterial156" placeholder="输入料号自动匹配156项" style="width:100%" @select="handleMaterialSelect" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="系统名称" prop="systemName">
              <el-autocomplete v-model="form.systemName" :fetch-suggestions="searchBySystemName" placeholder="输入系统名称自动匹配156项" style="width:100%" @select="handleSystemNameSelect" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类别" prop="category">
              <el-input v-model="form.category" placeholder="类别" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="配件名称" prop="partName">
              <el-input v-model="form.partName" placeholder="零件名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统计日期" prop="statDate">
              <el-date-picker v-model="form.statDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单台机用量" prop="unitUsage">
              <el-input-number v-model="form.unitUsage" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="比例(%)" prop="ratio">
              <el-input-number v-model="form.ratio" :precision="2" :min="0" style="width: 100%">
                <template #suffix><span style="color:#909399">%</span></template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="含税单价" prop="unitPriceWithTax">
              <el-input-number v-model="form.unitPriceWithTax" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="机台数" prop="machineCount">
              <el-input-number v-model="form.machineCount" :min="0" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="送货数量" prop="deliveryQuantity">
              <el-input-number v-model="form.deliveryQuantity" :min="0" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="上机数量" prop="machineOnQuantity">
              <el-input-number v-model="form.machineOnQuantity" :min="0" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="当月返修" prop="monthRepair">
              <el-input-number v-model="form.monthRepair" :min="0" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="约定比例数量">
              <el-input-number v-model="agreedRatio" :precision="2" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="含税金额合计(元)">
              <el-input-number v-model="calcExcessAmount" :precision="2" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="超比数量">
              <el-input-number v-model="calcExcessQty" :precision="0" :disabled="true" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 每日明细 -->
        <el-divider content-position="left">每日明细</el-divider>
        <el-table :data="dailies" border size="small" max-height="400">
          <el-table-column prop="dayNumber" label="日期" width="100" align="center">
            <template #default="{ row }">
              第{{ row.dayNumber }}天
            </template>
          </el-table-column>
          <el-table-column label="数值（自动统计）">
            <template #default="{ row }">
              <span :style="{ color: row.value > 0 ? 'red' : '' }">{{ row.value }}</span>
            </template>
          </el-table-column>
        </el-table>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/delivery-stats'
import { parseVoiceText } from '../../api/voice-parse'
import { search as search156Api } from '../../api/base-material-156'
import { useCompanyStore } from '../../stores/company'
import { usePagination } from '../../composables/usePagination'
import { useTableSelection } from '../../composables/useTableSelection'
import { useCrud } from '../../composables/useCrud'
import { getDaysInMonth, getYearMonth, downloadBlob, toSnakeCase } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'
import ToolBar from '../../components/ToolBar.vue'

const companyStore = useCompanyStore()
const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => api.getList({ ...params, companyId: companyStore.currentCompanyId })
)
const { selectedRows, handleSelectionChange } = useTableSelection()

const searchForm = reactive({
  keyword: '',
  category: '',
  yearMonth: ''
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('asc')

const defaultForm = {
  id: null,
  category: '',
  materialCode: '',
  systemName: '',
  partName: '',
  unitUsage: null,
  ratio: null,
  unitPriceWithTax: null,
  machineCount: null,
  deliveryQuantity: null,
  machineOnQuantity: null,
  monthRepair: null,
  statDate: ''
}
const form = reactive({ ...defaultForm })
const dailies = ref([])

// 自动计算：约定比例数量 = 单台机用量 × (比例/100) × 机台数
const agreedRatio = computed(() => {
  if (form.unitUsage != null && form.ratio != null && form.machineCount != null) {
    return (form.unitUsage * (form.ratio / 100) * form.machineCount).toFixed(2)
  }
  return null
})
// 超比数量 = max(0, 送货数量 - 当月返修 - 约定比例数量)
const calcExcessQty = computed(() => {
  const delivery = form.deliveryQuantity || 0
  const repair = form.monthRepair || 0
  const agreed = parseFloat(agreedRatio || 0)
  const val = delivery - repair - agreed
  return val > 0 ? val : 0
})
// 自动计算：含税金额合计 = 含税单价 × 超比数量 / 1.13
const calcExcessAmount = computed(() => {
  if (form.unitPriceWithTax != null) {
    return (form.unitPriceWithTax * calcExcessQty.value / 1.13).toFixed(2)
  }
  return null
})

const rules = {
  statDate: [{ required: true, message: '请选择统计日期', trigger: 'change' }],
  category: [{ required: true, message: '请输入类别', trigger: 'blur' }],
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }]
}

// 汇总金额 = Σ(含税单价 × 数量) ÷ 1.13 ÷ 10000，单位为万元
const DIVISOR = 1.13 * 10000

const summaryMonth = ref('')
const totals = reactive({
  deliveryAmount: 0,
  machineOnAmount: 0,
  repairAmount: 0,
  agreedRatioAmount: 0,
  excessAmount: 0,
  excessTaxAmount: 0
})

async function fetchTotals() {
  try {
    const params = { page: 1, pageSize: 9999, companyId: companyStore.currentCompanyId }
    if (summaryMonth.value) params.yearMonth = summaryMonth.value
    const res = await api.getList(params)
    const all = res.data.list || []
    const calc = (field) => all.reduce((acc, r) => acc + (Number(r.unitPriceWithTax) || 0) * (Number(r[field]) || 0), 0) / DIVISOR
    totals.deliveryAmount = calc('deliveryQuantity')
    totals.machineOnAmount = calc('machineOnQuantity')
    totals.repairAmount = calc('monthRepair')
    totals.agreedRatioAmount = calc('agreedRatioQuantity')
    // 超比金额合计 = Σ(含税单价 × 超比数量) ÷ 1.13 ÷ 10000
    totals.excessAmount = calc('excessQuantity')
    // 超比含税总额 = Σ(超比含税金额) ÷ 10000
    totals.excessTaxAmount = all.reduce((acc, r) => acc + (Number(r.excessAmountWithTax) || 0), 0) / 10000
  } catch { /* ignore */ }
}

function doFetch() {
  return fetchData({
    keyword: searchForm.keyword,
    category: searchForm.category,
    yearMonth: searchForm.yearMonth,
    companyId: companyStore.currentCompanyId,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

const { handleDelete, handleBatchDelete } = useCrud(api, doFetch)

function batchDelete() {
  handleBatchDelete(selectedRows.value.map(r => r.id))
}

function handleSearch() {
  queryParams.page = 1
  doFetch()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  searchForm.yearMonth = ''
  queryParams.page = 1
  doFetch()
}

function handleSortChange({ prop, order }) {
  if (order) {
    sortField.value = toSnakeCase(prop)
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortField.value = 'id'
    sortOrder.value = 'asc'
  }
  queryParams.page = 1
  doFetch()
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
      keyword: searchForm.keyword,
      category: searchForm.category,
      yearMonth: searchForm.yearMonth,
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '超比统计.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '超比统计模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

// ---- 每日明细辅助 ----

/**
 * 根据指定日期的月份生成 dailies 数组
 * 保留已有值，不足补 0，超出截断
 */
function generateDailies(dateStr, existing) {
  if (!dateStr) return []
  const yearMonth = getYearMonth(dateStr)
  const days = getDaysInMonth(yearMonth)
  const result = []
  for (let i = 1; i <= days; i++) {
    const prev = (existing || []).find(d => d.dayNumber === i)
    result.push({ dayNumber: i, value: prev ? prev.value : 0 })
  }
  return result
}

// 监听统计日期变化，重新生成 dailies
watch(() => form.statDate, (newDate) => {
  if (newDate) {
    dailies.value = generateDailies(newDate, dailies.value)
  }
})

// ---- 对话框操作 ----

function resetForm() {
  Object.assign(form, { ...defaultForm })
  dailies.value = []
}

function handleAdd() {
  isEdit.value = false; isCopy.value = false
  resetForm()
  // 默认当月
  const today = new Date()
  form.statDate = today.toISOString().slice(0, 10)
  // 生成当月 dailies
  dailies.value = generateDailies(form.statDate, [])
  dialogVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true; isCopy.value = false
  const res = await api.getDetail(row.id)
  Object.assign(form, res.data)
  if (form.ratio != null) form.ratio = form.ratio * 100
  try {
    const dailiesRes = await api.getDailies(row.id)
    const existingDailies = dailiesRes.data || []
    dailies.value = generateDailies(form.statDate, existingDailies)
  } catch {
    dailies.value = generateDailies(form.statDate, [])
  }
  dialogVisible.value = true
}

async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getDetail(row.id)
  Object.assign(form, { ...res.data, id: null })
  if (form.ratio != null) form.ratio = form.ratio * 100
  try {
    const dailiesRes = await api.getDailies(row.id)
    const existingDailies = dailiesRes.data || []
    dailies.value = generateDailies(form.statDate, existingDailies)
  } catch {
    dailies.value = generateDailies(form.statDate, [])
  }
  dialogVisible.value = true
}

// ---- 156项 autocomplete ----
async function searchMaterial156(query, cb) {
  if (!query || query.length < 1) { cb([]); return }
  try {
    const res = await search156Api(query)
    const data = res.data || []
    cb(data.map(m => ({ value: m.materialCode, label: `${m.materialCode} - ${m.partName || m.systemName || ''}`, item: m })))
  } catch { cb([]) }
}

async function searchBySystemName(query, cb) {
  if (!query || query.length < 1) { cb([]); return }
  try {
    const res = await search156Api(query)
    const data = res.data || []
    cb(data.map(m => ({ value: m.systemName, label: `${m.systemName} - ${m.materialCode}`, item: m })))
  } catch { cb([]) }
}

async function handleMaterialSelect(item) {
  form.materialCode = item.value
  await triggerAutoFill()
}

async function handleSystemNameSelect(item) {
  form.systemName = item.value
  // 如果料号为空，用系统名称查到的156项数据回填料号
  if (!form.materialCode && item.item && item.item.materialCode) {
    form.materialCode = item.item.materialCode
  }
  await triggerAutoFill()
}

async function triggerAutoFill() {
  if (!form.materialCode || !form.statDate) return
  try {
    const res = await api.autoFill(form.materialCode, form.statDate, companyStore.currentCompanyId)
    const d = res.data
    // 回填156项数据
    if (d.from156) {
      form.category = form.category || d.from156.category || ''
      form.systemName = form.systemName || d.from156.systemName || ''
      form.partName = d.from156.partName || ''
      form.unitUsage = d.from156.unitUsage ?? null
      form.ratio = d.from156.ratio != null ? d.from156.ratio * 100 : null
      form.unitPriceWithTax = d.from156.unitPriceWithTax ?? null
    }
    // 回填统计数据
    if (d.machineCount != null && d.machineCount !== 0) form.machineCount = d.machineCount
    if (d.deliveryQuantity != null) form.deliveryQuantity = d.deliveryQuantity
    if (d.machineOnQuantity != null) form.machineOnQuantity = d.machineOnQuantity
    if (d.monthRepair != null) form.monthRepair = d.monthRepair
    // 每日明细
    if (d.dailyQuantities && d.dailyQuantities.length > 0) {
      dailies.value = d.dailyQuantities.map(dq => ({
        dayNumber: dq.day,
        value: dq.count
      }))
    }
  } catch { /* 查不到就保持空值 */ }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form, dailies: dailies.value, companyId: companyStore.currentCompanyId }
    if (data.ratio != null) data.ratio = data.ratio / 100
    delete data.id
    if (isEdit.value) {
      await api.update(form.id, data)
      ElMessage.success('修改成功')
    } else {
      await api.create(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    doFetch()
  } finally {
    submitLoading.value = false
  }
}

const refreshMonth = ref('')

async function handleBatchRefresh() {
  if (!refreshMonth.value) { ElMessage.warning('请先选择要更新的月份'); return }
  try {
    const res = await api.batchRefresh(refreshMonth.value, companyStore.currentCompanyId)
    ElMessage.success(res.data?.msg || '更新完成')
    doFetch()
    fetchTotals()
  } catch { /* error handled */ }
}

// 默认汇总月份为当前月份
const voiceText = ref('')
const voiceLoading = ref(false)
const voicePlaceholder = '请按格式朗读: 类别风扇类 物料编码15297012400 系统名称冷却系统 配件名称驱动风扇 单台机用量1.5 比例0.8 含税单价120 机台数10 送货数量100 上机数量80 当月返修5 统计日期2026-07-01'
async function handleVoiceParse() {
  if (!voiceText.value.trim()) { ElMessage.warning('请先输入文字'); return }
  voiceLoading.value = true
  try {
    const res = await parseVoiceText(voiceText.value.trim(), 'delivery-stats')
    const fields = res.data.fields || {}
    const fc = res.data.filledCount || 0
    if (!fc) { ElMessage.warning('未识别到有效字段，请检查格式'); return }
    const fm = {
      category: 'category', materialCode: 'materialCode', systemName: 'systemName',
      partName: 'partName', unitUsage: 'unitUsage', ratio: 'ratio',
      unitPriceWithTax: 'unitPriceWithTax', machineCount: 'machineCount',
      deliveryQuantity: 'deliveryQuantity', machineOnQuantity: 'machineOnQuantity',
      monthRepair: 'monthRepair', statDate: 'statDate'
    }
    for (const [k, v] of Object.entries(fields)) {
      if (fm[k] && v) {
        if (['unitUsage', 'ratio', 'unitPriceWithTax'].includes(k)) { const n = parseFloat(v); if (!isNaN(n)) form[fm[k]] = k === 'ratio' ? n * 100 : n }
        else if (['machineCount', 'deliveryQuantity', 'machineOnQuantity', 'monthRepair'].includes(k)) { const n = parseInt(v); if (!isNaN(n)) form[fm[k]] = n }
        else form[fm[k]] = v
      }
    }
    ElMessage.success(`已填充 ${fc} 个字段，请核对`)
  } catch { ElMessage.error('解析失败') }
  finally { voiceLoading.value = false }
}

onMounted(() => {
  const now = new Date()
  summaryMonth.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0')
  doFetch()
  fetchTotals()
})
</script>

<style scoped>
.summary-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 6px;
  margin-bottom: 12px;
  background: #F5F5F7;
  border-radius: 8px;
  font-size: 13px;
  color: #1D1D1F;
  flex-wrap: wrap;
}

.summary-label {
  font-weight: 600;
  color: #0071E3;
  margin-right: 4px;
}

.summary-item b {
  color: #0071E3;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
