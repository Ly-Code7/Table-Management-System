<template>
  <div class="page-content">
    <PageHeader title="数据看板" />

    <div class="board-toolbar">
      <el-radio-group v-model="activeTab" @change="fetchAll">
        <el-radio-button label="repair-amount">维修金额</el-radio-button>
        <el-radio-button label="fault-frequency">故障频次</el-radio-button>
        <el-radio-button label="material-frequency">物料频次</el-radio-button>
        <el-radio-button label="repair-frequency">返修频次</el-radio-button>
        <el-radio-button label="repair-rate">物料返修率</el-radio-button>
      </el-radio-group>
      <div class="toolbar-right">
        <el-select v-model="year" style="width: 110px" @change="fetchAll">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
        </el-select>
        <el-button type="primary" :loading="exportLoading" @click="handleExport">导出 Excel</el-button>
      </div>
    </div>

    <el-table :data="pagedRows" v-loading="loading" border stripe style="width: 100%">
      <el-table-column type="index" label="序号" width="60" />
      <!-- 机台看板列 -->
      <template v-if="isMachineTab">
        <el-table-column prop="key" label="厂房+机台号" width="130" fixed="left" show-overflow-tooltip />
        <el-table-column prop="factory" label="厂房" width="80" />
      </template>
      <!-- 料号看板列 -->
      <template v-else>
        <el-table-column prop="category" label="类别" width="100" />
        <el-table-column prop="key" label="料号" width="130" fixed="left" show-overflow-tooltip />
        <el-table-column prop="partName" label="配件名称" width="140" show-overflow-tooltip />
        <el-table-column prop="price" label="合约单价" width="100" align="right" />
      </template>
      <!-- 12 个月列 -->
      <el-table-column v-for="m in monthKeys" :key="m" :prop="`months.${m}`" :label="m" width="80" align="right">
        <template #default="{ row }">
          {{ formatCell(row.months?.[m], activeTab === 'repair-rate') }}
        </template>
      </el-table-column>
      <!-- 汇总列 -->
      <el-table-column v-if="activeTab === 'repair-rate'" prop="average" label="平均" width="90" align="right">
        <template #default="{ row }">{{ formatPercent(row.average) }}</template>
      </el-table-column>
      <template v-else>
        <el-table-column :label="isMachineTab ? '小计' : '合计'" width="100" align="right" fixed="right">
          <template #default="{ row }">{{ formatCell(row.total, false) }}</template>
        </el-table-column>
        <el-table-column v-if="!isMachineTab" prop="amount" label="金额" width="110" align="right" fixed="right">
          <template #default="{ row }">{{ formatCell(row.amount, false) }}</template>
        </el-table-column>
      </template>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-if="isMachineTab"
        v-model:current-page="page"
        :page-size="pageSize"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
      <span v-else class="row-count">共 {{ rows.length }} 行</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../../api/board'
import { useCompanyStore } from '../../stores/company'
import { downloadBlob } from '../../utils'
import PageHeader from '../../components/PageHeader.vue'

const companyStore = useCompanyStore()
const activeTab = ref('repair-amount')
const year = ref(new Date().getFullYear())
const rows = ref([])
const loading = ref(false)
const exportLoading = ref(false)
const page = ref(1)
const pageSize = 50

const yearOptions = computed(() => {
  const y = new Date().getFullYear()
  return [y - 1, y, y + 1]
})

const isMachineTab = computed(() => ['repair-amount', 'fault-frequency'].includes(activeTab.value))

const monthKeys = computed(() => {
  const yy = String(year.value % 100).padStart(2, '0')
  return Array.from({ length: 12 }, (_, i) => `FY${yy}${String(i + 1).padStart(2, '0')}`)
})

const pagedRows = computed(() => {
  if (!isMachineTab.value) return rows.value
  const start = (page.value - 1) * pageSize
  return rows.value.slice(start, start + pageSize)
})

function formatCell(v, isRate) {
  if (v === null || v === undefined || v === '') return ''
  if (isRate) return formatPercent(v)
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function formatPercent(v) {
  if (v === null || v === undefined || v === '') return ''
  return (Number(v) * 100).toFixed(2) + '%'
}

async function fetchAll() {
  loading.value = true
  try {
    const cid = companyStore.currentCompanyId
    const res = await api[tabApiMap[activeTab.value]](cid, year.value)
    rows.value = res.data || []
    page.value = 1
  } catch { /* 拦截器已提示 */ }
  finally { loading.value = false }
}

const tabApiMap = {
  'repair-amount': 'getRepairAmount',
  'fault-frequency': 'getFaultFrequency',
  'material-frequency': 'getMaterialFrequency',
  'repair-frequency': 'getRepairFrequency',
  'repair-rate': 'getRepairRate'
}

async function handleExport() {
  exportLoading.value = true
  try {
    const cid = companyStore.currentCompanyId
    const res = await api.exportBoard(activeTab.value, cid, year.value)
    downloadBlob(res.data, `${activeTab.value}-${year.value}.xlsx`)
    ElMessage.success('导出成功')
  } catch { /* 拦截器已提示 */ }
  finally { exportLoading.value = false }
}

watch(() => companyStore.currentCompanyId, () => fetchAll())
onMounted(fetchAll)
</script>

<style scoped>
.board-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.toolbar-right { display: flex; gap: 10px; align-items: center; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 14px; }
.row-count { color: #909399; font-size: 13px; }
</style>
