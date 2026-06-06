import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'PortalHome',
    component: () => import('@/views/PortalHome.vue'),
    meta: { title: 'WiFi上网' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '账号登录' },
  },
  {
    path: '/sms',
    name: 'SmsLogin',
    component: () => import('@/views/SmsLoginView.vue'),
    meta: { title: '手机验证' },
  },
  {
    path: '/card',
    name: 'CardRedeem',
    component: () => import('@/views/CardRedeemView.vue'),
    meta: { title: '充值卡上网' },
  },
  {
    path: '/packages',
    name: 'Packages',
    component: () => import('@/views/PackageSelectView.vue'),
    meta: { title: '选择套餐' },
  },
  {
    path: '/pay',
    name: 'Pay',
    component: () => import('@/views/PayView.vue'),
    meta: { title: '支付' },
  },
  {
    path: '/my',
    name: 'MyAccount',
    component: () => import('@/views/MyAccountView.vue'),
    meta: { title: '个人中心' },
  },
  {
    path: '/success',
    name: 'Success',
    component: () => import('@/views/SuccessView.vue'),
    meta: { title: '上网成功' },
  },
]

const router = createRouter({
  history: createWebHistory('/portal/'),
  routes,
})

export default router
