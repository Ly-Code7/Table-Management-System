<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="选择物料"
    width="800px"
    class="material-picker-dialog"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 搜索区 -->
    <div class="picker-search">
      <el-input
        v-model="searchForm.keyword"
        placeholder="类别/物料名称/规格型号/物料编码"
        clearable
        style="width: 280px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button type="success" @click="handleAdd">新增物料</el-button>
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
      max-height="350"
    >
      <el-table-column prop="category" label="类别" width="90" />
      <el-table-column prop="materialName" label="物料名称" width="120" show-overflow-tooltip />
      <el-table-column prop="specModel" label="规格型号" width="140" show-overflow-tooltip />
      <el-table-column prop="materialCode" label="物料编码" width="165" />
      <el-table-column prop="remark" label="备注" width="130" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="80" />
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

    <div class="picker-tip">提示：点击表格行即可选择物料并回填到表单</div>

    <!-- 新增物料子弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增物料"
      width="500px"
      append-to-body
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
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as materialApi from '../api/material'
import { usePagination } from '../composables/usePagination'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  companyId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'select'])

const { list, total, loading, queryParams, fetchData, handlePageChange, handleSizeChange } = usePagination(
  (params) => materialApi.getList({ ...params, companyId: props.companyId })
)

const searchForm = reactive({ keyword: '' })

function doFetch() {
  return fetchData({
    keyword: searchForm.keyword,
    companyId: props.companyId
  })
}

function handleSearch() {
  queryParams.page = 1
  doFetch()
}

function handleReset() {
  searchForm.keyword = ''
  queryParams.page = 1
  doFetch()
}

// 弹窗打开时加载数据
watch(() => props.modelValue, (val) => {
  if (val) doFetch()
})

// 点击行回填
function handleRowClick(row) {
  emit('select', {
    category: row.category,
    materialName: row.materialName,
    specModel: row.specModel,
    materialCode: row.materialCode,
    remark: row.remark
  })
  emit('update:modelValue', false)
}

// 新增物料
const addDialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const defaultForm = { id: null, category: '', materialName: '', specModel: '', materialCode: '' }
const form = reactive({ ...defaultForm })
const rules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(form, { ...defaultForm })
  addDialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await materialApi.create({ ...form, companyId: props.companyId })
    ElMessage.success('新增成功')
    addDialogVisible.value = false
    queryParams.page = 1
    doFetch()
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.picker-search {
  display: flex;
  gap: 8px;
  align-items: center;
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
/* 固定弹窗尺寸：800 x 550，内容在 body 内滚动 */
.material-picker-dialog {
  height: 550px;
  display: flex;
  flex-direction: column;
}
.material-picker-dialog .el-dialog__header {
  flex-shrink: 0;
}
.material-picker-dialog .el-dialog__body {
  flex: 1;
  overflow: auto;
}
.material-picker-dialog .el-dialog__footer {
  flex-shrink: 0;
}
</style>
