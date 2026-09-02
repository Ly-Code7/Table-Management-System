<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="维修记录详情"
    width="900px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-loading="loading" class="detail-body">
      <template v-if="record">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="记录 ID">{{ record.id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ record.recordDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年+月">{{ record.yearMonth || '-' }}</el-descriptions-item>
          <el-descriptions-item label="班次">{{ record.shift || '-' }}</el-descriptions-item>
          <el-descriptions-item label="厂房">{{ record.factory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="厂房+机台">{{ record.plantMachine || '-' }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ record.serialNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="机台号">{{ record.machineNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="机型">{{ record.machineModel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="诊断人">{{ record.diagnostician || '-' }}</el-descriptions-item>
          <el-descriptions-item label="维修人">{{ record.repairPerson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认人">{{ record.confirmer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报修时间">{{ record.repairRequestTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ record.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ record.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="维修工时">{{ record.repairHours ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="停机工时">{{ record.downtimeHours ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据号">{{ record.documentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="故障现象" :span="3">{{ record.faultPhenomenon || '-' }}</el-descriptions-item>
          <el-descriptions-item label="维修描述" :span="3">{{ record.faultDescription || '-' }}</el-descriptions-item>
          <el-descriptions-item label="料号">{{ record.materialCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="156项名称">{{ record.material156Name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="配件名称">{{ record.partName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ record.quantity ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="上机物料号">{{ record.machineOnMaterial || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下机物料号">{{ record.machineOffMaterial || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下机料号">{{ record.machineOffCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上机是否客户物料">
            <span v-if="record.machineOnCustomer === '是'" class="customer-yes">是</span>
            <span v-else>{{ record.machineOnCustomer || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="下机是否客户物料">
            <span v-if="record.machineOffCustomer === '是'" class="customer-yes">是</span>
            <span v-else>{{ record.machineOffCustomer || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="送货记录引用">{{ record.deliveryRecordRef || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ record.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ record.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ record.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <!-- 维修图片（有则展示，可点击放大） -->
        <div v-if="imageUrl" class="detail-image">
          <el-image :src="imageUrl" :preview-src-list="[imageUrl]" fit="contain" style="max-width: 320px" />
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未找到该维修记录（可能已被删除）" />
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import * as api from '../api/original-record'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  recordId: { type: [Number, String], default: null }
})

defineEmits(['update:modelValue'])

const record = ref(null)
const loading = ref(false)
const imageUrl = ref('')

async function load() {
  if (!props.recordId) { record.value = null; return }
  loading.value = true
  imageUrl.value = ''
  try {
    const res = await api.getDetail(props.recordId)
    record.value = res.data || null
    // 有维修图片时拉签名 URL（1 小时有效）
    if (res.data?.imageKey) {
      try {
        const img = await api.getImageUrl(res.data.imageKey)
        imageUrl.value = img.data?.url || ''
      } catch { /* 图片拉取失败不阻塞 */ }
    }
  } catch {
    record.value = null // 记录不存在（可能已删除）或跨公司，展示空态
  } finally {
    loading.value = false
  }
}

// 弹窗打开且 recordId 变化时加载
watch(() => props.modelValue, (val) => { if (val) load() })
watch(() => props.recordId, () => { if (props.modelValue) load() })
</script>

<style scoped>
.detail-body { min-height: 120px; }
.customer-yes { color: #f56c6c; font-weight: 600; }
.detail-image { margin-top: 12px; display: flex; justify-content: center; }
</style>
