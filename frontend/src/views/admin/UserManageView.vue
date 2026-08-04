<template>
  <div class="user-manage-page">
    <PageHeader title="用户管理" description="管理系统用户和角色权限" />

    <div class="content-card">
      <div class="card-toolbar">
        <el-button type="primary" @click="openRegister">
          <el-icon><Plus /></el-icon>
          注册新用户
        </el-button>
        <el-button @click="showScheduler = !showScheduler">
          <el-icon><Clock /></el-icon>
          定时任务设置
        </el-button>
      </div>

      <!-- 定时任务设置 -->
      <div v-if="showScheduler" class="scheduler-panel">
        <div class="scheduler-title">超比统计定时刷新设置</div>
        <div class="scheduler-row">
          <span class="scheduler-label">当前表达式：</span>
          <el-tag type="info">{{ schedulerCron }}</el-tag>
        </div>
        <div class="scheduler-row">
          <span class="scheduler-label">执行时间：</span>
          <el-time-picker
            v-model="schedulerTime"
            format="HH:mm"
            placeholder="选择时间"
            style="width: 160px"
          />
          <span class="scheduler-hint">每天该时间自动刷新超比统计数据</span>
        </div>
        <div class="scheduler-row">
          <el-button type="primary" size="small" :loading="schedulerLoading" @click="saveSchedulerTime">保存</el-button>
          <el-button size="small" @click="schedulerTime = null">重置</el-button>
        </div>
      </div>

      <el-table :data="users" border stripe v-loading="loading" style="width: 100%">
          <el-table-column label="序号" width="80" prop="id" sortable="custom" />
        <el-table-column prop="username" label="手机号" width="140" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.role === 'admin' ? 'warning' : 'success'"
              @click="handleToggleRole(row)"
              :disabled="row.id === currentUserId"
            >
              {{ row.role === 'admin' ? '降级为普通用户' : '提升为管理员' }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(row)"
              :disabled="row.id === currentUserId"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Delete Confirm Dialog -->
    <el-dialog v-model="deleteVisible" title="删除用户" width="400px">
      <p>确定要删除用户 <strong>{{ deleteTarget?.username }}</strong>（{{ deleteTarget?.realName }}）吗？此操作不可恢复。</p>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>

    <!-- 注册新用户弹窗 -->
    <el-dialog v-model="regVisible" title="注册新用户" width="440px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-position="top" size="large">
        <el-form-item label="手机号" prop="phoneNumber">
          <el-input v-model="regForm.phoneNumber" placeholder="请输入手机号" maxlength="11" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="regForm.realName" placeholder="请输入真实姓名" :prefix-icon="UserFilled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regVisible = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="handleRegister">确认注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Iphone, UserFilled, Clock } from '@element-plus/icons-vue'
import { getUsers, updateUserRole, deleteUser } from '../../api/admin'
import { register as registerApi } from '../../api/auth'
import { getSchedulerCron, updateSchedulerCron } from '../../api/scheduler'
import { useAuthStore } from '../../stores/auth'
import PageHeader from '../../components/PageHeader.vue'

const authStore = useAuthStore()
const currentUserId = computed(() => authStore.user?.id)

const users = ref([])
const loading = ref(false)

// Reset password
const resetPwdVisible = ref(false)
const resetPwdLoading = ref(false)
const resetPwdFormRef = ref(null)
const resetPwdTarget = ref(null)
const resetPwdForm = ref({
  password: ''
})
const resetPwdRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

// Delete
const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deleteTarget = ref(null)

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getUsers()
    users.value = res.data || []
  } catch {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}

async function handleToggleRole(row) {
  const newRole = row.role === 'admin' ? 'user' : 'admin'
  const roleLabel = newRole === 'admin' ? '管理员' : '普通用户'
  try {
    await ElMessageBox.confirm(
      `确定要将用户 "${row.username}" 的角色${newRole === 'admin' ? '提升' : '降级'}为"${roleLabel}"吗？`,
      '变更角色',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await updateUserRole(row.id, newRole)
    ElMessage.success('角色变更成功')
    await fetchUsers()
  } catch (err) {
    if (err !== 'cancel') {
      // error handled in interceptor
    }
  }
}

function handleResetPassword(row) {
  resetPwdTarget.value = row
  resetPwdForm.value.password = ''
  resetPwdVisible.value = true
}

async function confirmResetPassword() {
  const valid = await resetPwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  resetPwdLoading.value = true
  try {
    await resetUserPassword(resetPwdTarget.value.id, resetPwdForm.value.password)
    ElMessage.success('密码重置成功')
    resetPwdVisible.value = false
  } finally {
    resetPwdLoading.value = false
  }
}

function handleDelete(row) {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function confirmDelete() {
  deleteLoading.value = true
  try {
    await deleteUser(deleteTarget.value.id)
    ElMessage.success('用户已删除')
    deleteVisible.value = false
    await fetchUsers()
  } finally {
    deleteLoading.value = false
  }
}

// ===== 注册新用户 =====
const regVisible = ref(false)
const regLoading = ref(false)
const regFormRef = ref(null)

const regForm = reactive({
  username: '',
  realName: '',
  phoneNumber: '',
  realName: ''
})

const regRules = {
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }]
}

function openRegister() {
  regForm.phoneNumber = ''
  regForm.realName = ''
  regFormRef.value?.resetFields()
  regVisible.value = true
}

async function handleRegister() {
  const valid = await regFormRef.value.validate().catch(() => false)
  if (!valid) return
  regLoading.value = true
  try {
    await registerApi({
      phoneNumber: regForm.phoneNumber,
      realName: regForm.realName
    })
    ElMessage.success('用户注册成功')
    regVisible.value = false
    await fetchUsers()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    regLoading.value = false
  }
}

// ===== 定时任务设置 =====
const showScheduler = ref(false)
const schedulerCron = ref('')
const schedulerTime = ref(null)
const schedulerLoading = ref(false)

async function loadSchedulerCron() {
  try {
    const res = await getSchedulerCron()
    schedulerCron.value = res.data.cron
    // 从 cron 表达式解析时间 (格式: 秒 分 时 日 月 周)
    const parts = res.data.cron.split(/\s+/)
    if (parts.length >= 3) {
      const h = parseInt(parts[2])
      const m = parseInt(parts[1])
      if (!isNaN(h) && !isNaN(m)) {
        schedulerTime.value = new Date(2000, 0, 1, h, m, 0)
      }
    }
  } catch { /* ignore */ }
}

async function saveSchedulerTime() {
  if (!schedulerTime.value) { return }
  const h = schedulerTime.value.getHours()
  const m = schedulerTime.value.getMinutes()
  const cron = `0 ${m} ${h} * * *`
  schedulerLoading.value = true
  try {
    await updateSchedulerCron(cron)
    schedulerCron.value = cron
    ElMessage.success('定时任务已更新：每天 ' + String(h).padStart(2,'0') + ':' + String(m).padStart(2,'0') + ' 执行')
  } catch { /* error handled */ }
  finally { schedulerLoading.value = false }
}

onMounted(() => {
  fetchUsers()
  loadSchedulerCron()
})
</script>

<style scoped>
.user-manage-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.content-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  flex: 1;
  overflow: auto;
}

.card-toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}

.scheduler-panel {
  background: #F8F9FA;
  border: 1px solid #E5E5EA;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}

.scheduler-title {
  font-size: 15px;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 14px;
}

.scheduler-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.scheduler-label {
  font-size: 13px;
  color: #666;
  min-width: 90px;
}

.scheduler-hint {
  font-size: 12px;
  color: #999;
}
</style>
