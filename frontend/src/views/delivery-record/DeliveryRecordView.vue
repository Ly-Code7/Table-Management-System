<template>
  <div class="page-content">
    <PageHeader title="送货记录" />

    <!-- 搜索筛选 -->
    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="物料编码/名称/规格/序列号/品牌" clearable style="width: 220px" />
      </el-form-item>
      <el-form-item label="类别">
        <el-input v-model="searchForm.category" placeholder="类别" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="产品属性">
        <el-select v-model="searchForm.productAttr" placeholder="全部" clearable style="width: 120px">
          <el-option label="新品" value="新品" />
          <el-option label="维修品" value="维修品" />
          <el-option label="免费" value="免费" />
        </el-select>
      </el-form-item>
      <el-form-item label="厂房">
        <el-input v-model="searchForm.factory" placeholder="厂房" clearable style="width: 120px" />
      </el-form-item>
      <el-form-item label="日期范围">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
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
    >
    </ToolBar>

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
      <el-table-column label="序号" width="80" prop="id" sortable="custom" fixed="left">
        <template #default="{ $index }">{{ sortOrder === 'desc' ? total - (queryParams.page - 1) * queryParams.pageSize - $index : (queryParams.page - 1) * queryParams.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="recordDate" label="日期" width="110" sortable="custom" />
      <el-table-column prop="yearMonth" label="年月" width="90">
        <template #default="{ row }">
          {{ row.yearMonth || getYearMonth(row.recordDate) }}
        </template>
      </el-table-column>
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="materialName" label="物料名称" width="120" show-overflow-tooltip />
      <el-table-column prop="specModel" label="规格型号" width="180" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="130" sortable="custom" />
      <el-table-column prop="materialSerial" label="序列号" width="140" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="70" />
      <el-table-column prop="unit" label="单位" width="60" />
      <el-table-column prop="brand" label="品牌" width="100" />
      <el-table-column prop="productAttr" label="产品属性" width="90">
        <template #default="{ row }">
          <el-tag :type="row.productAttr === '新品' ? 'success' : 'warning'" size="small">
            {{ row.productAttr }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="factory" label="厂房" width="90" />
      <el-table-column prop="shipmentNo" label="送货单号" width="130" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" width="150" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="80" />
      <el-table-column prop="updatedAt" label="更新时间" width="160" sortable="custom" />
      <el-table-column label="操作" width="170" fixed="right">
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
      :title="isEdit ? '编辑送货记录' : (isCopy ? '复制送货记录' : '新增送货记录')"
      width="800px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" size="default">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="日期" prop="recordDate">
              <el-date-picker v-model="form.recordDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料编码" prop="materialCode">
              <el-autocomplete v-model="form.materialCode" :fetch-suggestions="searchMaterials" placeholder="输入料号关键字自动匹配" style="width: 100%" @select="handleMaterialSelect" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-input v-model="form.category" placeholder="类别" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="物料名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规格型号" prop="specModel">
              <el-input v-model="form.specModel" placeholder="规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列号" prop="materialSerial">
              <el-input v-model="form.materialSerial" placeholder="物料序列号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" placeholder="单位" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="品牌" prop="brand">
              <el-input v-model="form.brand" placeholder="品牌" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="产品属性" prop="productAttr">
              <el-select v-model="form.productAttr" placeholder="请选择" style="width: 100%">
                <el-option label="新品" value="新品" />
                <el-option label="维修品" value="维修品" />
                <el-option label="免费" value="免费" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="厂房" prop="factory">
              <el-input v-model="form.factory" placeholder="厂房" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送货单号" prop="shipmentNo">
              <el-input v-model="form.shipmentNo" placeholder="送货单号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="备注" type="textarea" :rows="2" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as deliveryApi from '../../api/delivery-record'
import { search as searchMaterialsApi } from '../../api/material'
import { parseVoiceText } from '../../api/voice-parse'
import { useCompanyStore } from '../../stores/company'
import { usePagination } from '../../composables/usePagination'
import { useTableSelection } from '../../composables/useTableSelection'
import { useCrud } from '../../composables/useCrud'
import { downloadBlob, toSnakeCase, getYearMonth } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'
import ToolBar from '../../components/ToolBar.vue'

const companyStore = useCompanyStore()
const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => deliveryApi.getList({ ...params, companyId: companyStore.currentCompanyId })
)
const { selectedRows, handleSelectionChange } = useTableSelection()
const { handleDelete, handleBatchDelete } = useCrud(deliveryApi, doFetch)

const searchForm = reactive({
  keyword: '',
  category: '',
  productAttr: '',
  factory: ''
})
const dateRange = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const materialSearchCache = ref([])
const form = reactive({
  id: null,
  recordDate: '',
  category: '',
  materialName: '',
  specModel: '',
  materialCode: '',
  materialSerial: '',
  quantity: null,
  unit: '',
  brand: '',
  productAttr: '',
  factory: '',
  shipmentNo: '',
  remark: '',
  companyId: null
})

const rules = {
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  category: [{ required: true, message: '请输入类别', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

const sortField = ref('id')
const sortOrder = ref('desc')

function doFetch() {
  return fetchData({
    keyword: searchForm.keyword,
    category: searchForm.category,
    productAttr: searchForm.productAttr,
    factory: searchForm.factory,
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
    companyId: companyStore.currentCompanyId,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

function handleSearch() {
  queryParams.page = 1
  doFetch()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  searchForm.productAttr = ''
  searchForm.factory = ''
  dateRange.value = []
  queryParams.page = 1
  doFetch()
}

function handleSortChange({ prop, order }) {
  if (order) {
    sortField.value = toSnakeCase(prop)
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortField.value = 'id'
    sortOrder.value = 'desc'
  }
  queryParams.page = 1
  doFetch()
}

function resetForm() {
  form.id = null
  form.recordDate = ''
  form.category = ''
  form.materialName = ''
  form.specModel = ''
  form.materialCode = ''
  form.materialSerial = ''
  form.quantity = null
  form.unit = ''
  form.brand = ''
  form.productAttr = ''
  form.factory = ''
  form.shipmentNo = ''
  form.remark = ''
  form.companyId = companyStore.currentCompanyId
}

function handleAdd() {
  isEdit.value = false
  isCopy.value = false
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true
  isCopy.value = false
  const res = await deliveryApi.getDetail(row.id)
  Object.assign(form, res.data)
  dialogVisible.value = true
}

async function handleCopy(row) {
  isEdit.value = false
  isCopy.value = true
  const res = await deliveryApi.getCopy(row.id)
  const data = res.data
  Object.assign(form, { ...data, id: null })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form, companyId: companyStore.currentCompanyId }
    delete data.id
    if (isEdit.value) {
      await deliveryApi.update(form.id, data)
      ElMessage.success('修改成功')
    } else {
      await deliveryApi.create(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    doFetch()
  } finally {
    submitLoading.value = false
  }
}

function batchDelete() {
  handleBatchDelete(selectedRows.value.map(r => r.id))
}

// 导入
function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const res = await deliveryApi.importExcel(file, companyStore.currentCompanyId)
      const d = res.data
      const failDetails = d.failDetails || []
      if (d.fail > 0 && failDetails.length > 0) {
        const reasons = failDetails.slice(0, 10).map(f => `第${f.row}行: ${f.reason}`).join('<br>')
        const more = failDetails.length > 10 ? `<br>... 还有 ${failDetails.length - 10} 条` : ''
        ElMessage.warning({ message: `导入完成：成功 ${d.success} 条，失败 ${d.fail} 条`, duration: 5000 })
        setTimeout(() => {
          ElMessageBox.alert(`<div style="max-height:300px;overflow-y:auto;font-size:13px">${reasons}${more}</div>`,
            `失败明细 (${failDetails.length}条)`, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' })
        }, 300)
      } else {
        ElMessage.success(`导入完成：成功 ${d.success} 条`)
      }
      doFetch()
    } catch {
      // error handled in interceptor
    }
  }
  input.click()
}

// 导出
async function handleExport() {
  try {
    const response = await deliveryApi.exportExcel({
      keyword: searchForm.keyword,
      category: searchForm.category,
      productAttr: searchForm.productAttr,
      factory: searchForm.factory,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '送货记录.xlsx')
    ElMessage.success('导出成功')
  } catch {
    // error handled
  }
}

// 模板下载
async function handleTemplateDownload() {
  try {
    const response = await deliveryApi.downloadTemplate()
    downloadBlob(response.data, '送货记录模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch {
    // error handled
  }
}

// 语音输入
const voiceText = ref('')
const voiceLoading = ref(false)
const voicePlaceholder = '请按格式朗读: 日期2026年7月21日 类别风扇类 物料名称驱动风扇 规格型号109P0424H7D28 物料编码15297012400 序列号SN001 数量10 单位台 品牌BYD 产品属性新品 厂房A车间 送货单号SH001 备注无'

async function handleVoiceParse() {
  if (!voiceText.value.trim()) { ElMessage.warning('请先输入文字'); return }
  voiceLoading.value = true
  try {
    const res = await parseVoiceText(voiceText.value.trim(), 'delivery-record')
    const fields = res.data.fields || {}
    const filledCount = res.data.filledCount || 0
    if (filledCount === 0) { ElMessage.warning('未识别到有效字段，请检查格式'); return }
    const fieldMap = {
      recordDate: 'recordDate', category: 'category', materialName: 'materialName',
      specModel: 'specModel', materialCode: 'materialCode', materialSerial: 'materialSerial',
      quantity: 'quantity', unit: 'unit', brand: 'brand', productAttr: 'productAttr',
      factory: 'factory', shipmentNo: 'shipmentNo', remark: 'remark'
    }
    for (const [k, v] of Object.entries(fields)) {
      if (fieldMap[k] && v) {
        if (k === 'quantity') { const n = parseInt(v); if (!isNaN(n)) form[fieldMap[k]] = n }
        else form[fieldMap[k]] = v
      }
    }
    ElMessage.success(`已填充 ${filledCount} 个字段，请核对`)
  } catch { ElMessage.error('解析失败') }
  finally { voiceLoading.value = false }
}

// 物料编码模糊搜索
async function searchMaterials(query, cb) {
  if (!query || query.length < 1) { cb([]); return }
  try {
    const res = await searchMaterialsApi(query)
    const data = res.data || []
    materialSearchCache.value = data
    cb(data.map(m => ({ value: m.materialCode, label: `${m.materialCode} - ${m.materialName || ''}` })))
  } catch { cb([]) }
}

async function handleMaterialSelect(item) {
  form.materialCode = item.value
  // 先从物料表填充基础字段
  const matched = materialSearchCache.value.find(m => m.materialCode === item.value)
  if (matched) {
    form.category = matched.category || form.category
    form.materialName = matched.materialName || form.materialName
    form.specModel = matched.specModel || form.specModel
  }
  // 再从最近的送货记录中回填更多字段（品牌、单位、厂房等）
  try {
    const res = await deliveryApi.lookupByCode(item.value)
    const dr = res.data
    if (dr) {
      if (!form.category && dr.category) form.category = dr.category
      if (!form.materialName && dr.materialName) form.materialName = dr.materialName
      if (!form.specModel && dr.specModel) form.specModel = dr.specModel
      if (!form.brand && dr.brand) form.brand = dr.brand
      if (!form.unit && dr.unit) form.unit = dr.unit
      if (!form.factory && dr.factory) form.factory = dr.factory
      if (!form.productAttr && dr.productAttr) form.productAttr = dr.productAttr
      if (!form.materialSerial && dr.materialSerial) form.materialSerial = dr.materialSerial
    }
  } catch { /* 查不到就不填充 */ }
}

onMounted(() => {
  doFetch()
})
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
