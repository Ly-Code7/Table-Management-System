<template>
  <div class="page-content">
    <PageHeader title="公司管理" />

    <ToolBar :selected-count="0" @add="handleAdd" />

    <el-table :data="list" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="name" label="公司名称" min-width="200" />
      <el-table-column prop="createdAt" label="创建时间" width="180" sortable="custom">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.pageSize" :page-sizes="[20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公司' : '新增公司'" width="450px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="公司名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入公司名称" />
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
import * as api from '../../api/company'
import { formatDateTime, toSnakeCase } from '../../utils'
import { usePagination } from '../../composables/usePagination'
import { useCompanyStore } from '../../stores/company'
import PageHeader from '../../components/PageHeader.vue'
import ToolBar from '../../components/ToolBar.vue'

const companyStore = useCompanyStore()

// Adapt company API naming to standard pattern
const apiAdapter = {
  getList: () => api.getCompanies(),
  getDetail: (id) => api.getCompany(id),
  create: (data) => api.createCompany(data),
  update: (id, data) => api.updateCompany(id, data),
  remove: (id) => api.deleteCompany(id),
}

const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => apiAdapter.getList(params)
)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const sortField = ref('id')
const sortOrder = ref('desc')

const defaultForm = { id: null, name: '' }
const form = reactive({ ...defaultForm })
const rules = {
  name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }]
}

async function doFetch() {
  try {
    const res = await apiAdapter.getList()
    list.value = res.data || []
    total.value = list.value.length
  } catch {
    // handled by interceptor
  }
}

function handleSortChange({ prop, order }) {
  sortField.value = order ? toSnakeCase(prop) : 'id'
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  queryParams.page = 1; doFetch()
}

function resetForm() { Object.assign(form, { ...defaultForm }) }
function handleAdd() { isEdit.value = false; resetForm(); dialogVisible.value = true }
async function handleEdit(row) {
  isEdit.value = true
  const res = await apiAdapter.getDetail(row.id); Object.assign(form, res.data)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    const data = { ...form }
    delete data.id
    if (isEdit.value) await apiAdapter.update(form.id, data)
    else await apiAdapter.create(data)
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    doFetch()
    companyStore.fetchCompanies()
  } finally { submitLoading.value = false }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定要删除该条记录吗？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await apiAdapter.remove(id)
    ElMessage.success('删除成功')
    doFetch()
    companyStore.fetchCompanies()
  } catch {
    // 取消删除
  }
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
