<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const loading = ref(false)

const form = reactive({
  tenantId: store.tenantId, cardNo: '', cardPassword: '', memberId: 0,
})

async function handleRedeem() {
  if (!form.cardNo || !form.cardPassword) {
    ElMessage.warning('请输入卡号和密码')
    return
  }
  loading.value = true
  try {
    const res: any = await post('/card/redeem', form)
    store.memberId = res.memberId
    store.memberInfo = { ...res, id: res.memberId }
    ElMessage.success(`充值成功！余额：¥${Number(res.balance).toFixed(2)}`)
    router.push('/my')
  } catch (e: any) {
    ElMessage.error(e.message || '核销失败')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">🎫 充值卡上网</div>
      <div class="portal-subtitle">请输入充值卡信息</div>

      <el-form label-position="top">
        <el-form-item label="卡号">
          <el-input v-model="form.cardNo" placeholder="请输入充值卡号" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.cardPassword" placeholder="请输入卡密" size="large" />
        </el-form-item>
        <el-form-item label="会员ID（选填）">
          <el-input-number v-model="form.memberId" :min="0" placeholder="已有账号时填入" style="width:100%" size="large" />
        </el-form-item>
        <el-button class="portal-full-btn" type="primary" :loading="loading" @click="handleRedeem">
          核销并上网
        </el-button>
      </el-form>

      <div style="text-align:center;margin-top:8px;color:#909399;font-size:12px">
        未填会员ID将自动创建新账号
      </div>
      <div style="text-align:center;margin-top:12px">
        <el-button link @click="router.push('/')">← 返回首页</el-button>
      </div>
    </div>
  </div>
</template>
