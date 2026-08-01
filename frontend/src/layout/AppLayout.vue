<template>
  <el-container class="layout-container">
    <!-- 顶部栏 -->
    <el-header class="layout-header">
      <div class="header-left">
        <span class="header-logo">数据采集系统</span>
      </div>
      <div class="header-right">
        <el-select
          v-model="currentCompanyId"
          class="company-select"
          placeholder="选择公司"
          size="default"
          @change="handleCompanyChange"
        >
          <el-option
            v-for="c in companyStore.companyList"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-badge :value="0" :hidden="true" v-if="authStore.isAdmin">
          <el-button link class="header-btn" @click="showLogDrawer = true">
            <el-icon :size="18"><Clock /></el-icon>
          </el-button>
        </el-badge>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <span class="user-info">
            <el-avatar :size="32" :icon="UserFilled" />
            <span class="user-name">{{ authStore.user?.realName || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <!-- 侧边栏 -->
      <el-aside :class="['layout-aside', { collapsed: isCollapsed }]">
        <div class="aside-toggle" @click="isCollapsed = !isCollapsed">
          <el-icon :size="16"><component :is="isCollapsed ? 'Expand' : 'Fold'" /></el-icon>
        </div>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          router
          class="aside-menu"
        >
          <el-menu-item index="/delivery-record">
            <el-icon><Document /></el-icon>
            <template #title>送货记录</template>
          </el-menu-item>
          <el-menu-item index="/original-record">
            <el-icon><Notebook /></el-icon>
            <template #title>维修记录</template>
          </el-menu-item>
          <el-menu-item index="/unwarranted-material">
            <el-icon><Aim /></el-icon>
            <template #title>未过保物料</template>
          </el-menu-item>
          <el-menu-item index="/delivery-stats">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>超比统计</template>
          </el-menu-item>
          <el-menu-item index="/settlement-machine">
            <el-icon><Coin /></el-icon>
            <template #title>结算机台数</template>
          </el-menu-item>
          <el-menu-item index="/machine-detail">
            <el-icon><Monitor /></el-icon>
            <template #title>机型明细</template>
          </el-menu-item>
          <el-menu-item index="/machine-count">
            <el-icon><TrendCharts /></el-icon>
            <template #title>开机数量</template>
          </el-menu-item>
          <el-menu-item index="/material">
            <el-icon><Box /></el-icon>
            <template #title>物料表</template>
          </el-menu-item>
          <el-menu-item index="/base-material-156">
            <el-icon><Collection /></el-icon>
            <template #title>156项</template>
          </el-menu-item>
          <el-menu-item index="/operation-log" v-if="authStore.isAdmin">
            <el-icon><List /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
          <el-menu-item index="/company" v-if="authStore.isAdmin">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>公司管理</template>
          </el-menu-item>
          <el-menu-item index="/user-management" v-if="authStore.isAdmin">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view :key="currentCompanyId" />
      </el-main>
    </el-container>

    <!-- 操作日志抽屉 -->
    <LogDrawer v-model="showLogDrawer" />
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  UserFilled, ArrowDown, Expand, Fold, Clock,
  Document, Notebook, Setting, DataAnalysis, Coin, Aim,
  Monitor, TrendCharts, Box, Collection, List, OfficeBuilding, User
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useCompanyStore } from '../stores/company'
import LogDrawer from '../components/LogDrawer.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const companyStore = useCompanyStore()

const isCollapsed = ref(false)
const showLogDrawer = ref(false)

const activeMenu = computed(() => route.path)
const currentCompanyId = ref(null)

onMounted(async () => {
  await authStore.fetchMe()
  await companyStore.fetchCompanies()
  currentCompanyId.value = companyStore.currentCompanyId
})

function handleCompanyChange(id) {
  companyStore.setCurrentCompany(id)
}

function handleUserCommand(command) {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'profile') {
    ElMessage.info('个人信息功能开发中')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5EA;
  padding: 0 20px;
  z-index: 100;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-logo {
  font-size: 17px;
  font-weight: 600;
  color: #1D1D1F;
  letter-spacing: -0.2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.company-select {
  width: 160px;
}

.header-btn {
  color: #1D1D1F;
  font-size: 18px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #F5F5F7;
}

.user-name {
  font-size: 14px;
  color: #1D1D1F;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-body {
  height: calc(100vh - 56px);
  overflow: hidden;
}

.layout-aside {
  width: 220px;
  background: #FFFFFF;
  border-right: 1px solid #E5E5EA;
  transition: width 0.2s ease;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.layout-aside.collapsed {
  width: 64px;
}

.aside-toggle {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 40px;
  cursor: pointer;
  color: #86868B;
  border-bottom: 1px solid #F0F0F2;
  transition: color 0.2s;
}

.aside-toggle:hover {
  color: #0071E3;
}

.aside-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
}

.aside-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: 8px;
}

.aside-menu :deep(.el-menu-item.is-active) {
  background: #F0F5FF;
  color: #0071E3;
  font-weight: 500;
}

.aside-menu :deep(.el-menu-item:hover) {
  background: #F5F5F7;
}

.layout-main {
  background: #F5F5F7;
  padding: 24px;
  overflow: hidden;
  height: 100%;
}
</style>
