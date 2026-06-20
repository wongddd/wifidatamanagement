<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)
const isTablet = computed(() => windowWidth.value > 768 && windowWidth.value <= 1024)

function onResize() {
  windowWidth.value = window.innerWidth
}

onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

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
  if (isMobile.value) {
    userStore.closeMobileDrawer()
  }
}

function handleLogout() {
  userStore.logout()
  userStore.closeMobileDrawer()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout-container">
    <!-- ============ 桌面端/平板端侧栏 ============ -->
    <el-aside
      v-if="!isMobile"
      :width="userStore.sidebarCollapsed ? '64px' : '220px'"
      class="layout-aside"
    >
      <div class="logo">
        <el-icon :size="28"><Monitor /></el-icon>
        <span v-show="!userStore.sidebarCollapsed" class="logo-text">WiFi管理</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        :collapse="userStore.sidebarCollapsed"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
      <!-- 平板端自动折叠，桌面端手动 -->
      <div v-if="!isTablet" class="sidebar-toggle" @click="userStore.toggleSidebar()">
        <span class="toggle-icon">{{ userStore.sidebarCollapsed ? '▶' : '◀' }}</span>
      </div>
    </el-aside>

    <!-- ============ 移动端侧栏抽屉 ============ -->
    <el-drawer
      v-if="isMobile"
      v-model="userStore.mobileDrawerOpen"
      direction="ltr"
      size="240px"
      :with-header="false"
      :close-on-press-escape="true"
      :modal="true"
    >
      <div class="mobile-drawer-logo">
        <el-icon :size="26"><Monitor /></el-icon>
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
      <div class="mobile-drawer-footer">
        <el-button type="danger" @click="handleLogout" style="width:100%">退出登录</el-button>
      </div>
    </el-drawer>

    <!-- ============ 主体区域 ============ -->
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <!-- 移动端：汉堡菜单 -->
          <el-button
            v-if="isMobile"
            text
            class="hamburger-btn"
            @click="userStore.toggleMobileDrawer()"
          >
            <el-icon :size="20"><Menu /></el-icon>
          </el-button>
          <!-- 平板端/桌面端：折叠按钮 -->
          <el-button
            v-else-if="!isTablet"
            text
            class="hamburger-btn"
            @click="userStore.toggleSidebar()"
          >
            <span class="toggle-text">{{ userStore.sidebarCollapsed ? '▶' : '◀' }}</span>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="user-info">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
          <el-button v-if="!isMobile" type="danger" text @click="handleLogout">退出登录</el-button>
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
  transition: width 0.3s;
  position: relative;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  gap: 8px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  overflow: hidden;
  white-space: nowrap;
}
.logo-text {
  overflow: hidden;
  text-overflow: ellipsis;
}
.sidebar-toggle {
  position: absolute;
  bottom: 0;
  width: 100%;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bfcbd9;
  cursor: pointer;
  border-top: 1px solid rgba(255,255,255,0.1);
  transition: background 0.2s;
  &:hover { background: rgba(255,255,255,0.05); }
}
.toggle-icon { font-size: 12px; user-select: none; }
.toggle-text { font-size: 12px; user-select: none; }
.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #dcdfe6;
  padding: 0 12px;
  height: 60px;
  @media (max-width: 768px) {
    height: 48px;
    padding: 0 8px;
  }
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  @media (max-width: 768px) {
    .el-breadcrumb { font-size: 12px; }
  }
}
.hamburger-btn {
  padding: 4px;
  font-size: 20px;
  flex-shrink: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  @media (max-width: 768px) {
    gap: 8px;
  }
}
.user-info {
  font-size: 14px;
  color: #606266;
  @media (max-width: 768px) {
    font-size: 12px;
  }
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
  @media (max-width: 768px) {
    padding: 12px 8px;
  }
}

// 移动端侧栏抽屉
:deep(.mobile-drawer-logo) {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  gap: 8px;
  background: #304156;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
:deep(.mobile-drawer-footer) {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 16px;
  border-top: 1px solid #dcdfe6;
}
:deep(.el-drawer__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
}
</style>
