<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePortalStore } from '@/store/portal'

const router = useRouter()
const store = usePortalStore()
const countdown = ref(5)

onMounted(() => {
  // 倒计时自动跳转到 MikroTik 放行
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      // 在生产环境中，这里会重定向到 MikroTik Hotspot login 成功页
      // window.location.href = linkOrig + '?login=success'
    }
  }, 1000)
})
</script>

<template>
  <div class="portal-container">
    <div class="portal-card" style="text-align:center">
      <div style="font-size:64px;margin-bottom:16px">✅</div>
      <div class="portal-title" style="color:#67C23A">上网认证成功！</div>
      <div class="portal-subtitle">
        {{ store.selectedPackage?.packageName || '上网服务' }}已开通
      </div>

      <p style="color:#909399;font-size:14px;margin:24px 0">
        页面将在 <strong>{{ countdown }}</strong> 秒后自动跳转<br/>
        您已可以开始上网
      </p>

      <el-button class="portal-full-btn" type="primary" @click="router.push('/my')">
        查看个人中心
      </el-button>
      <div style="margin-top:12px">
        <el-button link @click="router.push('/')">返回首页</el-button>
      </div>
    </div>
  </div>
</template>
