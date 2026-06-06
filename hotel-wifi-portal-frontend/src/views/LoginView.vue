<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const loading = ref(false)
const form = reactive({ tenantId: store.tenantId, hotelId: store.hotelId, username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res: any = await post('/auth/username', form)
    // API返回 {memberId, username, realName, balance, phone, expireAt, status}
    if (res && res.memberId) {
      store.memberId = Number(res.memberId)
      store.memberInfo = res
      // 有选中套餐 → 去支付，否则 → 去选择套餐
      if (store.selectedPackage) {
        router.push('/pay')
      } else {
        router.push('/packages')
      }
    } else {
      ElMessage.error('认证失败：用户名或密码错误')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '网络错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">账号密码登录</div>
      <div class="portal-subtitle">请输入您的上网账号</div>

      <el-form @submit.prevent="handleLogin" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="手机号或用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password size="large" />
        </el-form-item>
        <el-button class="portal-full-btn" type="primary" :loading="loading" @click="handleLogin">
          登录上网
        </el-button>
      </el-form>

      <div style="text-align:center;margin-top:16px">
        <el-button link @click="router.push('/')">← 返回首页</el-button>
      </div>
    </div>
  </div>
</template>
