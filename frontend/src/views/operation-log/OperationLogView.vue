<template>
  <div class="page-content">
    <PageHeader title="操作日志" />

    <SearchForm :form="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="用户ID">
        <el-input v-model="searchForm.userId" placeholder="用户ID" clearable style="width: 120px" />
      </el-form-item>
      <el-form-item label="表名">
        <el-input v-model="searchForm.tableName" placeholder="表名" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item label="操作类型">
        <el-select v-model="searchForm.action" placeholder="全部" clearable style="width: 130px">
          <el-option label="INSERT" value="INSERT" />
          <el-option label="UPDATE" value="UPDATE" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期范围">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
      </el-form-item>
    </SearchForm>

    <el-table :data="list" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="username" label="用户名" width="100" />
      <el-table-column prop="action" label="操作类型" width="100">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tableName" label="表名" width="140" />
      <el-table-column prop="recordId" label="记录ID" width="80" />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP地址" width="140" />
      <el-table-column prop="companyId" label="公司ID" width="80" />
      <el-table-column prop="createdAt" label="操作时间" width="160" sortable="custom">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.pageSize" :page-sizes="[20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handlePageChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import * as api from '../../api/operation-log'
import { formatDateTime, toSnakeCase } from '../../utils'
import { usePagination } from '../../composables/usePagination'
import PageHeader from '../../components/PageHeader.vue'
import SearchForm from '../../components/SearchForm.vue'

const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => api.getList(params)
)

const searchForm = reactive({ userId: '', tableName: '', action: '' })
const dateRange = ref([])
const sortField = ref('id')
const sortOrder = ref('desc')

function doFetch() {
  return fetchData({
    ...searchForm,
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
    sortField: sortField.value,
    sortOrder: sortOrder.value
  })
}

function handleSearch() { queryParams.page = 1; doFetch() }
function handleReset() {
  Object.assign(searchForm, { userId: '', tableName: '', action: '' })
  dateRange.value = []; queryParams.page = 1; doFetch()
}

function handleSortChange({ prop, order }) {
  sortField.value = order ? toSnakeCase(prop) : 'id'
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  queryParams.page = 1; doFetch()
}

function actionTagType(action) {
  if (action === 'INSERT') return 'success'
  if (action === 'UPDATE') return 'warning'
  if (action === 'DELETE') return 'danger'
  return 'info'
}

onMounted(() => doFetch())
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
