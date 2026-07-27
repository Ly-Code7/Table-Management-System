<template>
  <div class="page-content">
    <PageHeader title="156项" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="搜索料号/系统名称/配件名称/类别" clearable style="width: 260px" />
      </el-form-item>
    </SearchForm>

    <ToolBar
      :selected-count="selectedRows.length"
      @add="handleAdd"
      @batch-delete="batchDelete"
      @import="handleImport"
      @export="handleExport"
      @template="handleTemplateDownload"
    />

    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <el-table-column type="selection" width="44" fixed="left" />
      <el-table-column label="序号" width="60">
        <template #default="{ $index }">{{ total - (queryParams.page - 1) * queryParams.pageSize - $index }}</template>
      </el-table-column>
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="materialCode" label="料号" width="130" sortable="custom" show-overflow-tooltip />
      <el-table-column prop="systemName" label="系统名称" width="120" show-overflow-tooltip />
      <el-table-column prop="partName" label="配件名称" width="140" show-overflow-tooltip />
      <el-table-column prop="unitUsage" label="单台机用量" width="100" />
      <el-table-column label="比例" width="80">
        <template #default="{ row }">
          {{ row.ratio != null ? parseFloat((row.ratio * 100).toFixed(4)) + '%' : '' }}
        </template>
      </el-table-column>
      <el-table-column label="含税单价" width="110">
        <template #default="{ row }">
          {{ row.unitPriceWithTax != null ? Number(row.unitPriceWithTax).toFixed(2) : '' }}
        </template>
      </el-table-column>
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑156项' : (isCopy ? '复制156项' : '新增156项')"
      width="800px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-input v-model="form.category" placeholder="类别" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="料号" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="料号（唯一）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="系统名称" prop="systemName">
              <el-input v-model="form.systemName" placeholder="系统名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配件名称" prop="partName">
              <el-input v-model="form.partName" placeholder="配件名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="单台机用量" prop="unitUsage">
              <el-input-number v-model="form.unitUsage" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="比例(%)" prop="ratio">
              <el-input-number v-model="form.ratio" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="含税单价" prop="unitPriceWithTax">
              <el-input-number v-model="form.unitPriceWithTax" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
import * as api from '../../api/base-material-156'
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
  materialCode: '',
  systemName: '',
  partName: '',
  unitUsage: null,
  ratio: null,
  unitPriceWithTax: null
}
const form = reactive({ ...defaultForm })
const rules = {
  materialCode: [{ required: true, message: '请输入料号', trigger: 'blur' }]
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
  searchForm.keyword = ''
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
  const res = await api.getDetail(row.id)
  const d = { ...res.data, ratio: res.data.ratio != null ? res.data.ratio * 100 : null }
  Object.assign(form, d)
  dialogVisible.value = true
}
async function handleCopy(row) {
  isEdit.value = false; isCopy.value = true
  const res = await api.getDetail(row.id)
  const d = { ...res.data, id: null, ratio: res.data.ratio != null ? res.data.ratio * 100 : null }
  Object.assign(form, d)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form, companyId: companyStore.currentCompanyId,
      ratio: form.ratio != null ? form.ratio / 100 : null }
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
      if (d.fail > 0) {
        const detailLines = d.failDetails.slice(0, 10).map(f => `第${f.row}行: ${f.reason}`)
        const detailText = detailLines.join('<br>') + (d.failDetails.length > 10 ? '<br>...更多错误已省略' : '')
        ElMessage.warning(`导入完成：成功 ${d.success} 条，失败 ${d.fail} 条`)
        ElMessageBox.alert(detailText, '导入失败详情', {
          confirmButtonText: '知道了',
          type: 'warning',
          dangerouslyUseHTMLString: true
        }).catch(() => {})
      } else {
        ElMessage.success(`导入完成：成功 ${d.success} 条`)
      }
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
    downloadBlob(response.data, '156项.xlsx')
    ElMessage.success('导出成功')
  } catch { /* error handled */ }
}

async function handleTemplateDownload() {
  try {
    const response = await api.downloadTemplate()
    downloadBlob(response.data, '156项模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch { /* error handled */ }
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
