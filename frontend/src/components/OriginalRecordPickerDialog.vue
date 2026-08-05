<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="选择维修记录"
    width="1250px"
    class="record-picker-dialog"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 搜索区 -->
    <div class="picker-search">
      <el-input
        v-model="searchForm.keyword"
        placeholder="机台号/料号/维修人/厂房/维修描述…"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-date-picker
        v-model="searchForm.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 240px"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table
      :data="list"
      v-loading="loading"
      border
      stripe
      highlight-current-row
      @row-click="handleRowClick"
      style="width: 100%; margin-top: 12px"
      max-height="425"
    >
      <el-table-column prop="recordDate" label="日期" width="110" />
      <el-table-column prop="factory" label="厂房" width="90" />
      <el-table-column prop="machineNo" label="机台号" width="90" />
      <el-table-column prop="repairPerson" label="维修人" width="90" />
      <el-table-column prop="materialCode" label="料号" width="120" show-overflow-tooltip />
      <el-table-column prop="partName" label="配件名称" width="120" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="60" />

      <el-table-column prop="faultDescription" label="维修描述" min-width="160" show-overflow-tooltip />
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

    <div class="picker-tip">提示：点击表格行即可选择维修记录并回填到表单</div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import * as originalRecordApi from '../api/original-record'
import { usePagination } from '../composables/usePagination'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  companyId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'select'])

const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => originalRecordApi.getList({ ...params, companyId: props.companyId })
)

const searchForm = reactive({ keyword: '', dateRange: [] })

function doFetch() {
  return fetchData({
    keyword: searchForm.keyword,

    startDate: searchForm.dateRange?.[0] || undefined,
    endDate: searchForm.dateRange?.[1] || undefined,
    companyId: props.companyId,
    // 已被未过保物料关联的维修记录不再展示（一个维修记录只允许关联一条）
    excludeLinked: true
  })
}

function handleSearch() {
  queryParams.page = 1
  doFetch()
}

function handleReset() {
  searchForm.keyword = ''

  searchForm.dateRange = []
  queryParams.page = 1
  doFetch()
}

// 弹窗打开时加载数据
watch(() => props.modelValue, (val) => {
  if (val) {
    queryParams.page = 1
    doFetch()
  }
})

// 点击行回填（把整条维修记录交给父组件）
function handleRowClick(row) {
  emit('select', row)
  emit('update:modelValue', false)
}

</script>

<style scoped>
.picker-search {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.picker-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>

<style>
/* 固定弹窗尺寸：1250 x 688，内容在 body 内滚动 */
.record-picker-dialog {
  height: 688px;
  display: flex;
  flex-direction: column;
}
.record-picker-dialog .el-dialog__header {
  flex-shrink: 0;
}
.record-picker-dialog .el-dialog__body {
  flex: 1;
  overflow: auto;
}
.record-picker-dialog .el-dialog__footer {
  flex-shrink: 0;
}
</style>
