<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

const form = reactive({
  tenantId: store.tenantId, hotelId: store.hotelId, phone: '', verifyCode: '',
})

async function sendCode() {
  if (!form.phone) { ElMessage.warning('请输入手机号'); return }
  sending.value = true
  try {
    await post('/auth/whatsapp/send', { phone: form.phone })
    ElMessage.success('WhatsApp验证码已发送，请查看您的WhatsApp消息')
    countdown.value = 60
    const timer = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(timer) }, 1000)
  } catch { ElMessage.error('发送失败，请稍后重试') }
  finally { sending.value = false }
}

async function handleLogin() {
  if (!form.phone || !form.verifyCode) { ElMessage.warning('请输入手机号和验证码'); return }
  loading.value = true
  try {
    const res: any = await post('/auth/whatsapp', {
      phone: form.phone, verifyCode: form.verifyCode,
      tenantId: form.tenantId, hotelId: form.hotelId
    }).catch(async () => {
      await post('/member/register', { tenantId: form.tenantId, hotelId: form.hotelId, phone: form.phone })
      return await get(`/member/phone/${form.phone}`, { tenantId: form.tenantId })
    })
    if (res && res.memberId) {
      store.memberId = Number(res.memberId); store.memberInfo = res
      ElMessage.success('认证成功')
      router.push(store.selectedPackage ? '/pay' : '/packages')
    } else { ElMessage.error('认证失败，请重试') }
  } catch (e: any) { ElMessage.error(e.message || '网络错误') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">💬 WhatsApp 验证码登录</div>
      <div class="portal-subtitle">验证码将发送到您的 WhatsApp</div>
      <el-form label-position="top">
        <el-form-item label="手机号（需注册WhatsApp）">
          <el-input v-model="form.phone" placeholder="如 +8613800138000" size="large" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display:flex;gap:8px">
            <el-input v-model="form.verifyCode" placeholder="6位验证码" size="large" style="flex:1" />
            <el-button type="primary" :disabled="countdown > 0" :loading="sending" @click="sendCode" style="width:130px">
              {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-button class="portal-full-btn" type="primary" :loading="loading" @click="handleLogin">验证并上网</el-button>
      </el-form>
      <div style="text-align:center;margin-top:16px">
        <el-button link @click="router.push('/')">← 返回首页</el-button>
      </div>
    </div>
  </div>
</template>
