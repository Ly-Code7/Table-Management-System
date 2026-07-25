<template>
  <div class="login-page">
    <div class="bg-image" />

    <div class="login-header">
      <div class="header-left">
        <img src="/images/logo.png" alt="Logo" class="header-logo" />
        <span class="header-company">昊昱精密</span>
      </div>
      <div class="header-right">
        <span class="header-support">
          <el-icon :size="16"><Phone /></el-icon>
          技术支持：0755-84586807
        </span>
      </div>
    </div>

    <div class="login-body">
      <div class="login-left" />

      <div class="login-right">
        <div class="login-area">
          <h1 class="system-title">驻场维保数据采集平台</h1>
          <p class="system-subtitle">On-Site Maintenance Data Collection Platform</p>

          <div class="login-card">
            <div class="card-header">
              <h2>登录</h2>
              <p>请输入手机号获取验证码</p>
            </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            size="large"
            class="login-form"
          >
            <el-form-item label="手机号" prop="phoneNumber">
              <el-input
                v-model="form.phoneNumber"
                placeholder="请输入手机号"
                :prefix-icon="Iphone"
                maxlength="11"
                class="login-input"
              />
            </el-form-item>
            <el-form-item label="验证码" prop="code">
              <div style="display:flex;gap:12px;width:100%">
                <el-input
                  v-model="form.code"
                  placeholder="请输入6位验证码"
                  maxlength="6"
                  class="login-input"
                  style="flex:1"
                  @keyup.enter="handleLogin"
                />
                <el-button
                  :disabled="countdown > 0 || !validPhone"
                  class="code-btn"
                  @click="handleSendCode"
                >
                  {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                <span v-if="!loading">登 录</span>
              </el-button>
            </el-form-item>
          </el-form>
          </div>
        </div>
      </div>
    </div>

    <div class="login-footer">
      <span>Copyright &copy; {{ new Date().getFullYear() }}</span>
      <a href="https://www.top-tpm.com/homepage" target="_blank" class="footer-link">深圳市昊昱精密机电有限公司</a>
      <span>All Rights Reserved.</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Iphone, Phone } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import { sendSmsCode } from '../../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const codeLoading = ref(false)
const countdown = ref(0)
const formRef = ref(null)

const form = reactive({
  phoneNumber: '',
  code: ''
})

const validPhone = computed(() => /^1\d{10}$/.test(form.phoneNumber))

const rules = {
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ]
}

async function handleSendCode() {
  if (!validPhone.value) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  codeLoading.value = true
  try {
    await sendSmsCode(form.phoneNumber)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    codeLoading.value = false
  }
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.smsLogin(form.phoneNumber, form.code)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.bg-image {
  position: absolute;
  inset: 0;
  background: url('/images/login-bg.png') center/cover no-repeat;
  z-index: 0;
}
.bg-image::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
}
.login-header {
  position: relative; z-index: 2;
  display: flex; align-items: center; justify-content: space-between;
  height: 64px; padding: 0 40px;
  background: #fff; border-bottom: 1px solid rgba(0,0,0,0.08);
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-logo { width: 36px; height: 36px; object-fit: contain; border-radius: 4px; }
.header-company { font-size: 18px; font-weight: 600; color: #1a1a1a; letter-spacing: 2px; }
.header-right { display: flex; gap: 12px; }
.header-support {
  display: flex; align-items: center; gap: 6px;
  font-size: 15px; font-weight: 600; color: #7A2B2D;
  padding: 8px 18px; border-radius: 8px;
  background: rgba(122,43,45,0.08); border: 1px solid rgba(122,43,45,0.25);
  letter-spacing: 0.5px;
}
.login-body {
  position: relative; z-index: 1; flex: 1;
  display: flex; align-items: center; justify-content: flex-end;
  padding: 0 100px;
}
.login-left { flex: 1; }
.login-right { flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.login-area { display: flex; flex-direction: column; align-items: center; }
.system-title {
  font-size: 36px; font-weight: 700; color: #fff;
  margin: 0 0 8px; letter-spacing: 4px;
  text-shadow: 0 2px 12px rgba(0,0,0,0.3); text-align: center;
}
.system-subtitle {
  font-size: 14px; color: rgba(255,255,255,0.6);
  margin: 0 0 28px; letter-spacing: 2px; text-align: center;
}
.login-card {
  width: 420px; padding: 48px 44px 40px;
  background: rgba(255,255,255,0.12); backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  border: 1px solid rgba(255,255,255,0.2); border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2), inset 0 1px 0 rgba(255,255,255,0.15);
}
.card-header { text-align: center; margin-bottom: 32px; }
.card-header h2 { font-size: 24px; font-weight: 700; color: #fff; margin: 0 0 6px; letter-spacing: 1px; }
.card-header p { font-size: 14px; color: rgba(255,255,255,0.5); margin: 0; }
.login-form :deep(.el-form-item__label) { color: rgba(255,255,255,0.75); font-size: 13px; font-weight: 500; padding-bottom: 4px; }
.login-form :deep(.el-form-item) { margin-bottom: 18px; }
.login-input :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  border-radius: 10px; box-shadow: none; transition: all 0.3s ease;
}
.login-input :deep(.el-input__wrapper:hover) { border-color: rgba(255,255,255,0.3); background: rgba(255,255,255,0.12); }
.login-input :deep(.el-input__wrapper.is-focus) { border-color: rgba(255,255,255,0.5); box-shadow: 0 0 0 3px rgba(255,255,255,0.08); background: rgba(255,255,255,0.12); }
.login-input :deep(.el-input__inner) { color: #fff; }
.login-input :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,0.35); }
.login-input :deep(.el-input__prefix) { color: rgba(255,255,255,0.45); }
.code-btn {
  height: 40px; padding: 0 16px; white-space: nowrap;
  background: rgba(255,255,255,0.15); border: 1px solid rgba(255,255,255,0.3);
  color: #fff; border-radius: 10px; font-size: 13px; cursor: pointer;
  display: flex; align-items: center;
}
.code-btn:hover { background: rgba(255,255,255,0.25); }
.code-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.login-btn {
  width: 100%; height: 46px; font-size: 15px; font-weight: 600;
  border-radius: 10px; margin-top: 8px;
  background: rgba(255,255,255,0.9); border: none; color: #1a1a1a;
  letter-spacing: 4px; transition: all 0.3s ease;
}
.login-btn:hover { background: #fff; transform: translateY(-1px); box-shadow: 0 6px 20px rgba(0,0,0,0.25); }
.login-btn:active { transform: translateY(0); }
.login-footer {
  position: relative; z-index: 2;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  height: 48px; font-size: 13px; color: rgba(255,255,255,0.5);
  background: rgba(0,0,0,0.15); backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.footer-link { color: rgba(255,255,255,0.75); text-decoration: none; font-weight: 500; transition: color 0.2s; }
.footer-link:hover { color: #fff; }
@media (max-width: 900px) {
  .login-body { justify-content: center; padding: 40px 24px; }
  .login-left { display: none; }
  .system-title { font-size: 24px; }
  .login-card { width: 100%; max-width: 400px; padding: 32px 28px 28px; }
  .login-header { padding: 0 20px; }
  .header-company { font-size: 15px; }
}
</style>
