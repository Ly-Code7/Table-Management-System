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
        <el-input
          v-model="keyword"
          :placeholder="searchPlaceholder"
          clearable
          style="width: 200px"
        />
        <el-select v-model="year" style="width: 110px" @change="fetchAll">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
        </el-select>
        <el-button type="primary" :loading="exportLoading" @click="handleExport">导出 Excel</el-button>
      </div>
    </div>

    <el-table :data="pagedRows" v-loading="loading" border stripe style="width: 100%" :row-class-name="rowClassName">
      <el-table-column label="序号" width="60">
        <template #default="{ row, $index }">{{ row.key === '合计' ? 0 : $index }}</template>
      </el-table-column>
      <!-- 机台看板列 -->
      <template v-if="isMachineTab">
        <el-table-column prop="key" label="厂房+机台号" width="130" fixed="left" show-overflow-tooltip />
        <el-table-column prop="factory" label="厂房" width="80" />
      </template>
      <!-- 料号看板列 -->
      <template v-else>
        <!-- 156项名称列（冻结首列）：合计行在此显示"合计"，恢复合计字样冻结在左侧的既有视觉 -->
        <el-table-column label="156项名称" width="140" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.key === '合计'">合计</span>
            <span v-else>{{ row.partName }}</span>
          </template>
        </el-table-column>
        <!-- 料号列：合计行置空（"合计"字样已移至首列，避免重复） -->
        <el-table-column label="料号" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.key === '合计'"></span>
            <span v-else>{{ row.key }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="类别" width="100" />
        <el-table-column prop="price" label="合约单价" width="100" align="right" />
      </template>
      <!-- 12 个月列 -->
      <el-table-column v-for="m in monthKeys" :key="m" :prop="`months.${m}`" :label="m" width="120" align="right">
        <template #default="{ row }">
          {{ formatCell(row.months?.[m], activeTab === 'repair-rate') }}
        </template>
      </el-table-column>
      <!-- 汇总列 -->
      <el-table-column v-if="activeTab === 'repair-rate'" prop="average" label="平均" width="90" align="right">
        <template #default="{ row }">{{ formatPercent(row.average) }}</template>
      </el-table-column>
      <template v-else>
        <el-table-column :label="isMachineTab ? '小计' : '合计'" width="150" align="right" fixed="right">
          <template #default="{ row }">{{ formatCell(row.total, false) }}</template>
        </el-table-column>
        <el-table-column v-if="!isMachineTab" prop="amount" label="金额" width="110" align="right" fixed="right">
          <template #default="{ row }">{{ formatCell(row.amount, false) }}</template>
        </el-table-column>
      </template>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="filteredRows.length"
        layout="total, prev, pager, next"
      />
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

// 结果缓存：key = `${tab}|${companyId}|${year}`，切 Tab/年份/公司往返秒开，不重复请求
const cache = new Map()
const CACHE_MAX = 30

const yearOptions = computed(() => {
  const y = new Date().getFullYear()
  return [y - 1, y, y + 1]
})

const isMachineTab = computed(() => ['repair-amount', 'fault-frequency'].includes(activeTab.value))

// 搜索框：按当前看板维度适配（机台类搜机台号/厂房，料号类搜料号/156项名称/类别）
const keyword = ref('')
const searchPlaceholder = computed(() =>
  isMachineTab.value ? '搜索机台号/厂房' : '搜索料号/156项名称/类别'
)

const monthKeys = computed(() => {
  const yy = String(year.value % 100).padStart(2, '0')
  return Array.from({ length: 12 }, (_, i) => `FY${yy}${String(i + 1).padStart(2, '0')}`)
})

// 后端在每个看板返回末尾附汇总行（key='合计'，各月 = 当月全部行合计，小计/合计 = 全年总计，金额 = 全部行金额之和）
const summaryRow = computed(() => rows.value.find(r => r.key === '合计') || null)
const dataRows = computed(() => rows.value.filter(r => r.key !== '合计'))

// 按关键词过滤数据行（合计行不受搜索影响，始终为全量合计）
const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return dataRows.value
  if (isMachineTab.value) {
    return dataRows.value.filter(r =>
      (r.key || '').toLowerCase().includes(kw) ||
      (r.factory || '').toLowerCase().includes(kw)
    )
  }
  return dataRows.value.filter(r =>
    (r.key || '').toLowerCase().includes(kw) ||
    (r.partName || '').toLowerCase().includes(kw) ||
    (r.category || '').toLowerCase().includes(kw)
  )
})

const pagedRows = computed(() => {
  // 全部看板分页渲染（每页 50 行）：低配机器上 el-table 全量渲染 183 行 × 15 列需 ~2s，
  // 分页后每次只渲染 50 行，切换 Tab 的卡顿显著缓解
  const start = (page.value - 1) * pageSize
  const slice = filteredRows.value.slice(start, start + pageSize)
  // 合计行固定在每页顶部（全量合计，不随分页丢失）
  return summaryRow.value ? [summaryRow.value, ...slice] : slice
})

function rowClassName({ row }) {
  return row.key === '合计' ? 'board-summary-row' : ''
}

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
  keyword.value = '' // 切换看板/年份/公司时重置搜索
  const cid = companyStore.currentCompanyId
  const key = `${activeTab.value}|${cid}|${year.value}`
  const hit = cache.get(key)
  if (hit) {
    rows.value = hit
    page.value = 1
    return
  }
  loading.value = true
  try {
    const res = await api[tabApiMap[activeTab.value]](cid, year.value)
    rows.value = res.data || []
    cache.set(key, rows.value)
    if (cache.size > CACHE_MAX) {
      // 淘汰最早插入的键，防止长时间使用内存膨胀
      cache.delete(cache.keys().next().value)
    }
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
// 搜索词变化时回到第一页
watch(keyword, () => { page.value = 1 })
onMounted(fetchAll)
</script>

<style scoped>
.board-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.toolbar-right { display: flex; gap: 10px; align-items: center; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 14px; }
.row-count { color: #909399; font-size: 13px; }
.board-summary-row { font-weight: 700; }
.board-summary-row td.el-table__cell { background-color: #f5f7fa; }
</style>
