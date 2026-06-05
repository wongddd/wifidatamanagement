import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页', icon: 'Odometer' },
      },
      {
        path: 'tenants',
        name: 'Tenants',
        component: () => import('@/views/tenant/TenantListView.vue'),
        meta: { title: '租户管理', icon: 'OfficeBuilding' },
      },
      {
        path: 'hotels',
        name: 'Hotels',
        component: () => import('@/views/hotel/HotelListView.vue'),
        meta: { title: '酒店管理', icon: 'HomeFilled' },
      },
      {
        path: 'members',
        name: 'Members',
        component: () => import('@/views/member/MemberListView.vue'),
        meta: { title: '会员管理', icon: 'User' },
      },
      {
        path: 'packages',
        name: 'Packages',
        component: () => import('@/views/package/PackageListView.vue'),
        meta: { title: '套餐管理', icon: 'Goods' },
      },
      {
        path: 'billing',
        name: 'Billing',
        component: () => import('@/views/billing/BillingView.vue'),
        meta: { title: '计费管理', icon: 'Money' },
      },
      {
        path: 'recharge-cards',
        name: 'RechargeCards',
        component: () => import('@/views/billing/RechargeCardView.vue'),
        meta: { title: '充值卡管理', icon: 'CreditCard' },
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('@/views/device/DeviceListView.vue'),
        meta: { title: '设备管理', icon: 'Monitor' },
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportView.vue'),
        meta: { title: '统计报表', icon: 'DataAnalysis' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || ''} - 酒店WiFi管理系统`

  if (to.meta.requiresAuth !== false) {
    const userStore = useUserStore()
    if (!userStore.token) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }
  next()
})

export default router
