<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const packages = ref<any[]>([])
const selected = ref<any>(store.selectedPackage)

async function loadPackages() {
  try {
    packages.value = await get('/packages', { tenantId: store.tenantId })
  } catch {}
}

function select(pkg: any) {
  selected.value = pkg
  store.selectedPackage = pkg
}

async function confirmBuy() {
  if (!selected.value || !store.memberId) {
    ElMessage.warning('请先登录并选择套餐')
    return
  }
  router.push('/pay')
}

onMounted(loadPackages)
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">选择上网套餐</div>
      <div class="portal-subtitle">请选择适合您的套餐</div>

      <div
        v-for="item in packages"
        :key="item.id"
        class="package-card"
        :class="{ selected: selected?.id === item.id }"
        @click="select(item)"
      >
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <p style="font-weight:600;font-size:15px;margin-bottom:4px">{{ item.packageName }}</p>
            <p style="font-size:12px;color:#909399;display:flex;flex-wrap:wrap;align-items:center;gap:2px 6px">
              <el-tag size="small" :type="item.billingType === 'TIME' ? 'primary' : 'warning'">
                {{ item.billingType === 'TIME' ? '包时' : item.billingType === 'TRAFFIC' ? '包流量' : '混合' }}
              </el-tag>
              <span v-if="item.durationSeconds > 0" style="margin-left:6px">
                {{ item.durationSeconds >= 86400 ? (item.durationSeconds/86400).toFixed(0)+'天' : item.durationSeconds+'秒' }}
              </span>
              <span v-if="item.trafficBytes > 0" style="margin-left:6px">
                {{ (item.trafficBytes/1073741824).toFixed(1) }}GB
              </span>
              <span v-if="item.downloadLimitBps > 0" style="margin-left:6px">
                {{ (item.downloadLimitBps/1048576).toFixed(1) }}Mbps
              </span>
            </p>
          </div>
          <div style="text-align:right">
            <span class="price-symbol">¥</span>
            <span class="price">{{ item.price }}</span>
          </div>
        </div>
      </div>

      <el-button
        class="portal-full-btn"
        type="primary"
        :disabled="!selected"
        @click="confirmBuy"
        style="margin-top:16px"
      >
        {{ selected ? '购买 ¥' + selected.price : '请先选择套餐' }}
      </el-button>

      <div style="text-align:center;margin-top:12px">
        <el-button link @click="router.back()">← 返回</el-button>
      </div>
    </div>
  </div>
</template>
