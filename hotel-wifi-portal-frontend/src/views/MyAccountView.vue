<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const loading = ref(false)
const memberInfo = ref<any>(null)
const searchForm = reactive({ phone: '', tenantId: 1 })
const rechargeAmount = ref(100)
const rechargeVisible = ref(false)

// 如果已登录（store中有memberInfo），直接显示
onMounted(() => {
  if (store.memberInfo && store.memberId) {
    memberInfo.value = store.memberInfo
  }
})

async function searchByPhone() {
  if (!searchForm.phone) { ElMessage.warning('请输入手机号'); return }
  loading.value = true
  try {
    memberInfo.value = await get(`/member/phone/${searchForm.phone}`, { tenantId: searchForm.tenantId })
    // 同步到 store，让个人中心记住登录状态
    store.memberId = memberInfo.value?.id
    store.memberInfo = memberInfo.value
  } catch { ElMessage.error('未找到该手机号的会员信息'); memberInfo.value = null }
  finally { loading.value = false }
}

async function handleRecharge() {
  const mId = store.memberId ?? memberInfo.value?.id
  if (!mId) { ElMessage.error('请先登录'); return }
  try {
    await post('/payment/monnify/create', {
      memberId: mId, packageId: 0,
      customerName: memberInfo.value?.realName,
      customerEmail: (memberInfo.value?.phone || 'guest') + '@hotel.com',
    })
    rechargeVisible.value = false
    ElMessage.success('请完成支付')
  } catch (e: any) { ElMessage.error(e.message || '创建支付失败') }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">👤 个人中心</div>
      <div class="portal-subtitle">查询余额、在线充值</div>
      <div v-if="!memberInfo">
        <el-form label-position="top">
          <el-form-item label="租户ID"><el-input-number v-model="searchForm.tenantId" :min="1" style="width:100%" size="large" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="searchForm.phone" placeholder="请输入注册手机号" size="large" /></el-form-item>
          <el-button class="portal-full-btn" type="primary" :loading="loading" @click="searchByPhone">查询</el-button>
        </el-form>
      </div>
      <div v-else>
        <el-descriptions :column="1" border size="large">
          <el-descriptions-item label="用户名">{{ memberInfo.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ memberInfo.realName }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ memberInfo.phone }}</el-descriptions-item>
          <el-descriptions-item label="账户余额"><span style="font-size:20px;font-weight:700;color:#67C23A">¥{{ memberInfo.balance || 0 }}</span></el-descriptions-item>
          <el-descriptions-item label="套餐到期">{{ memberInfo.expireAt || '无' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="memberInfo.status === 1 ? 'success' : 'danger'">{{ memberInfo.status === 1 ? '正常' : '已停用' }}</el-tag></el-descriptions-item>
        </el-descriptions>
        <div style="display:flex;gap:12px;margin-top:20px">
          <el-button type="primary" style="flex:1" size="large" @click="rechargeVisible = true">💳 在线充值</el-button>
          <el-button style="flex:1" size="large" @click="router.push('/packages')">📦 购买套餐</el-button>
        </div>
        <div style="text-align:center;margin-top:16px;display:flex;flex-direction:column;gap:8px">
        <el-button link @click="memberInfo = null; store.memberInfo = null; store.memberId = null">← 切换账号</el-button>
        <el-button link @click="router.push('/login')">🔑 账号密码登录</el-button>
      </div>
      </div>
      <div style="text-align:center;margin-top:16px"><el-button link @click="router.push('/')">← 返回首页</el-button></div>
    </div>
    <el-dialog v-model="rechargeVisible" title="在线充值" width="90%" style="max-width:380px;border-radius:12px">
      <div style="text-align:center;padding:10px 0">
        <p style="color:#909399;margin-bottom:16px">选择充值金额</p>
        <div style="display:flex;flex-wrap:wrap;gap:10px;justify-content:center;margin-bottom:16px">
          <el-button v-for="n in [10,30,50,100,200,500]" :key="n" :type="rechargeAmount === n ? 'primary' : ''"
            @click="rechargeAmount = n" size="large">¥{{ n }}</el-button>
        </div>
        <el-form-item><el-input-number v-model="rechargeAmount" :min="1" style="width:100%" size="large" /></el-form-item>
      </div>
      <template #footer><el-button @click="rechargeVisible = false">取消</el-button><el-button type="primary" @click="handleRecharge">确认支付 ¥{{ rechargeAmount }}</el-button></template>
    </el-dialog>
  </div>
</template>
