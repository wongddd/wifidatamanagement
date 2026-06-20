<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  tenantId: null as number | null,
  username: '',
  password: '',
})

const errorMsg = ref('')

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    // 有redirect参数时跳回原页面，否则去首页
    const redirect = route.query.redirect as string
    router.push(redirect || '/dashboard')
  } catch (e: any) {
    errorMsg.value = e.message || '用户名或密码错误'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <h2>计费系统</h2>
      <p class="subtitle">Hotel WiFi Billing & Management</p>

      <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" style="margin-bottom:16px" />

      <el-form @submit.prevent="handleLogin">
        <el-form-item label="租户ID">
          <el-input-number v-model="form.tenantId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="管理员用户名">
          <el-input v-model="form.username" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            管理员登录
          </el-button>
        </el-form-item>
      </el-form>

      <el-divider>
        <span style="color:#909399;font-size:12px">我不是管理员</span>
      </el-divider>

      <a href="/portal/" style="text-decoration:none">
        <el-button style="width:100%" type="success">
          📱 我是住客，去WiFi上网认证
        </el-button>
      </a>


    </div>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
  padding: 16px;
}
.login-card {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.login-card h2 {
  text-align: center;
  margin-bottom: 4px;
  color: #303133;
}
.subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 24px;
  font-size: 13px;
}

/* 小屏手机适配 */
@media (max-width: 480px) {
  .login-container {
    padding: 0;
    align-items: flex-start;
  }
  .login-card {
    max-width: 100%;
    min-height: 100vh;
    border-radius: 0;
    padding: 40px 20px 24px;
    box-shadow: none;
  }
  .login-card h2 {
    font-size: 22px;
  }
  .subtitle {
    font-size: 12px;
    margin-bottom: 20px;
  }
}
</style>
