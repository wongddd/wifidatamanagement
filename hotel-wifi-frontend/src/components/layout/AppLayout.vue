<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/dashboard', title: '首页', icon: 'Odometer' },
  { path: '/tenants', title: '租户管理', icon: 'OfficeBuilding', role: 'SUPER_ADMIN' },
  { path: '/hotels', title: '酒店管理', icon: 'HomeFilled' },
  { path: '/members', title: '会员管理', icon: 'User' },
  { path: '/packages', title: '套餐管理', icon: 'Goods' },
  { path: '/billing', title: '计费管理', icon: 'Money' },
  { path: '/recharge-cards', title: '充值卡管理', icon: 'CreditCard' },
  { path: '/devices', title: '设备管理', icon: 'Monitor' },
  { path: '/reports', title: '统计报表', icon: 'DataAnalysis' },
]

const visibleMenuItems = computed(() =>
  menuItems.filter(item => !item.role || item.role === userStore.role)
)

function handleMenuSelect(path: string) {
  router.push(path)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <el-icon :size="28"><Monitor /></el-icon>
        <span>WiFi管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主体 -->
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="user-info">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
          <el-button type="danger" text @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}
.layout-aside {
  background-color: #304156;
  overflow-y: auto;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  gap: 8px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #dcdfe6;
  padding: 0 20px;
  height: 60px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
