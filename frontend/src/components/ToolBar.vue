<template>
  <div class="toolbar">
    <el-button v-if="showAdd" type="primary" @click="$emit('add')">
      <el-icon><Plus /></el-icon>
      <span>新增</span>
    </el-button>
    <el-button type="danger" plain @click="$emit('batch-delete')" :disabled="selectedCount === 0">
      <el-icon><Delete /></el-icon>
      <span>批量删除</span>
      <span v-if="selectedCount > 0">({{ selectedCount }})</span>
    </el-button>
    <el-button plain @click="$emit('import')">
      <el-icon><Upload /></el-icon>
      <span>导入</span>
    </el-button>
    <el-button plain @click="$emit('export')">
      <el-icon><Download /></el-icon>
      <span>导出</span>
    </el-button>
    <el-button plain @click="$emit('template')">
      <el-icon><Document /></el-icon>
      <span>模板下载</span>
    </el-button>
    <slot></slot>
  </div>
</template>

<script setup>
defineProps({
  selectedCount: {
    type: Number,
    default: 0
  },
  showAdd: {
    type: Boolean,
    default: true
  }
})

defineEmits(['add', 'batch-delete', 'import', 'export', 'template'])
</script>
