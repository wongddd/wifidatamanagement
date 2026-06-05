<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '@/api'
import { usePortalStore } from '@/store/portal'

const router = useRouter()
const store = usePortalStore()
const packages = ref<any[]>([])

async function loadPackages() {
  try {
    packages.value = await get('/packages', { tenantId: store.tenantId })
  } catch {}
}

loadPackages()
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">WiFi 上网认证</div>
      <div class="portal-subtitle">选择上网方式</div>

      <!-- 套餐直购 -->
      <div v-if="packages.length > 0" style="margin-bottom:20px">
        <p style="font-size:14px;color:#606266;margin-bottom:10px;font-weight:600">🔥 热门套餐</p>
        <div
          v-for="item in packages.slice(0, 3)"
          :key="item.id"
          class="package-card"
          :class="{ selected: store.selectedPackage?.id === item.id }"
          @click="store.selectedPackage = item; router.push('/login')"
        >
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <p style="font-weight:600;margin-bottom:4px">{{ item.packageName }}</p>
              <p style="font-size:12px;color:#909399">
                {{ item.billingType === 'TIME' ? '包时' : '包流量' }}
                · {{ item.durationSeconds ? (item.durationSeconds/86400).toFixed(0)+'天' : '' }}
                {{ item.trafficBytes ? (item.trafficBytes/1073741824).toFixed(0)+'GB' : '不限' }}
              </p>
            </div>
            <div>
              <span class="price-symbol">¥</span>
              <span class="price">{{ item.price }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 登录方式 -->
      <el-button class="portal-full-btn" type="primary" @click="router.push('/login')">
        账号密码登录
      </el-button>

      <div class="portal-divider"><span>其他方式</span></div>

      <div style="display:flex;gap:12px">
        <el-button style="flex:1" @click="router.push('/sms')">
          📱 手机验证码
        </el-button>
        <el-button style="flex:1" @click="router.push('/card')">
          🎫 充值卡
        </el-button>
      </div>

      <div style="text-align:center;margin-top:20px">
        <el-button link type="primary" @click="router.push('/my')">
          已有账号？查看个人中心
        </el-button>
      </div>
    </div>
  </div>
</template>
