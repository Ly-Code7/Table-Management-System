import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/delivery-record',
    children: [
      {
        path: 'delivery-record',
        name: 'DeliveryRecord',
        component: () => import('../views/delivery-record/DeliveryRecordView.vue'),
        meta: { title: '送货记录' }
      },
      {
        path: 'original-record',
        name: 'OriginalRecord',
        component: () => import('../views/original-record/OriginalRecordView.vue'),
        meta: { title: '维修记录' }
      },
      {
        path: 'unwarranted-material',
        name: 'UnwarrantedMaterial',
        component: () => import('../views/unwarranted-material/UnwarrantedMaterialView.vue'),
        meta: { title: '未过保物料' }
      },
      {
        path: 'delivery-stats',
        name: 'DeliveryStats',
        component: () => import('../views/delivery-stats/DeliveryStatsView.vue'),
        meta: { title: '送货超比统计' }
      },
      {
        path: 'settlement-machine',
        name: 'SettlementMachine',
        component: () => import('../views/settlement-machine/SettlementMachineView.vue'),
        meta: { title: '结算机台数' }
      },
      {
        path: 'board',
        name: 'Board',
        component: () => import('../views/board/BoardView.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'machine-detail',
        name: 'MachineDetail',
        component: () => import('../views/machine-detail/MachineDetailView.vue'),
        meta: { title: '机型明细' }
      },
      {
        path: 'machine-count',
        name: 'MachineCount',
        component: () => import('../views/machine-count/MachineCountView.vue'),
        meta: { title: '开机数量' }
      },
      {
        path: 'material',
        name: 'Material',
        component: () => import('../views/material/MaterialView.vue'),
        meta: { title: '物料表' }
      },
      {
        path: 'base-material-156',
        name: 'BaseMaterial156',
        component: () => import('../views/base-material-156/BaseMaterial156View.vue'),
        meta: { title: '156项' }
      },
      {
        path: 'operation-log',
        name: 'OperationLog',
        component: () => import('../views/operation-log/OperationLogView.vue'),
        meta: { title: '操作日志', requiresAdmin: true }
      },
      {
        path: 'company',
        name: 'Company',
        component: () => import('../views/company/CompanyView.vue'),
        meta: { title: '公司管理', requiresAdmin: true }
      },
      {
        path: 'user-management',
        name: 'UserManagement',
        component: () => import('../views/admin/UserManageView.vue'),
        meta: { title: '用户管理', requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/')
    return
  }
  // 检查管理员权限
  if (to.meta.requiresAdmin) {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        if (user.role !== 'admin') {
          ElMessage.warning('您没有管理员权限，无法访问该页面')
          next('/delivery-record')
          return
        }
      } catch {
        next('/delivery-record')
        return
      }
    } else {
      next('/delivery-record')
      return
    }
  }
  next()
})

export default router
