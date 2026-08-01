<template>
  <div class="page-content">
    <PageHeader title="物料表" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="搜索" clearable style="width: 200px" />
      </el-form-item>
    </SearchForm>

    <ToolBar :selected-count="selectedRows.length" @add="handleAdd" @batch-delete="batchDelete" @import="handleImport" @export="handleExport" @template="handleTemplateDownload" />

    <el-table :data="list" v-loading="loading" border stripe @selection-change="handleSelectionChange" @sort-change="handleSortChange">
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column label="序号" width="80" prop="id" sortable="custom">
        <template #default="{ $index }">{{ sortOrder === 'desc' ? total - (queryParams.page - 1) * queryParams.pageSize - $index : (queryParams.page - 1) * queryParams.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="category" label="类别" width="120" />
      <el-table-column prop="materialName" label="物料名称" width="140" show-overflow-tooltip />
      <el-table-column prop="specModel" label="规格型号" width="160" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="130" sortable="custom" />
      <el-table-column prop="remark" label="备注" width="150" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column label="操作" width="190" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑物料' : (isCopy ? '复制物料' : '新增物料')" width="600px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规格型号" prop="specModel">
              <el-input v-model="form.specModel" placeholder="规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="物料编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/material'
import { parseVoiceText } from '../../api/voice-parse'
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

const searchForm = reactive({ keyword: '' })
const dialogVisible = ref(false)
const isEdit = ref(false)
const isCopy = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('desc')

const defaultForm = {
  id: null,
  category: '',
  materialName: '',
  specModel: '',
  materialCode: '',
  remark: ''
}
const form = reactive({ ...defaultForm })
const rules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }]
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
  Object.assign(searchForm, { keyword: '' })
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
  dialogVisible.value = true
}
async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getDetail(row.id)
  Object.assign(form, { ...res.data, id: null })
  dialogVisible.value = true
}

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
      companyId: companyStore.currentCompanyId
    })
    downloadBlob(response.data, '物料表.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '物料表模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

// 语音输入
const voiceText = ref('')
const voiceLoading = ref(false)
const voicePlaceholder = '请按格式朗读: 类别风扇类 物料名称驱动风扇 规格型号109P0424H7D28 物料编码15297012400'
async function handleVoiceParse() {
  if (!voiceText.value.trim()) { ElMessage.warning('请先输入文字'); return }
  voiceLoading.value = true
  try {
    const res = await parseVoiceText(voiceText.value.trim(), 'material')
    const fields = res.data.fields || {}
    const fc = res.data.filledCount || 0
    if (!fc) { ElMessage.warning('未识别到有效字段，请检查格式'); return }
    const fm = { category: 'category', materialName: 'materialName', specModel: 'specModel', materialCode: 'materialCode', remark: 'remark' }
    for (const [k, v] of Object.entries(fields)) { if (fm[k] && v) form[fm[k]] = v }
    ElMessage.success(`已填充 ${fc} 个字段，请核对`)
  } catch { ElMessage.error('解析失败') }
  finally { voiceLoading.value = false }
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
