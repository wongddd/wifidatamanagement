<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const paying = ref(false)
const payMethod = ref<'balance' | 'monnify'>('balance')

async function payByBalance() {
  paying.value = true
  try {
    await post('/orders/buy', {
      tenantId: store.tenantId, hotelId: store.hotelId,
      memberId: store.memberId, packageId: store.selectedPackage?.id,
    })
    ElMessage.success('购买成功！')
    router.push('/success')
  } catch (e: any) { ElMessage.error(e.message || '余额不足，请充值后再试') }
  finally { paying.value = false }
}

async function payByMonnify() {
  paying.value = true
  try {
    const res: any = await post('/payment/monnify/create', {
      memberId: store.memberId, packageId: store.selectedPackage?.id,
      customerName: store.memberInfo?.realName,
      customerEmail: (store.memberInfo?.phone || 'guest') + '@hotel.com',
    })
    if (res.checkoutUrl) window.location.href = res.checkoutUrl
    else ElMessage.error('支付初始化失败')
  } catch (e: any) { ElMessage.error(e.message || '支付服务暂不可用') }
  finally { paying.value = false }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">确认支付</div>
      <div class="portal-subtitle">{{ store.selectedPackage?.packageName }}</div>
      <div style="text-align:center;margin:24px 0">
        <span style="font-size:36px;font-weight:700;color:#f56c6c">¥{{ store.selectedPackage?.price }}</span>
      </div>
      <div style="margin-bottom:20px">
        <el-radio-group v-model="payMethod" style="width:100%">
          <div style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;cursor:pointer"
               :style="{ borderColor: payMethod === 'balance' ? '#409EFF' : '#dcdfe6' }"
               @click="payMethod = 'balance'">
            <el-radio value="balance" size="large"><span style="font-weight:600">💰 余额支付</span></el-radio>
          </div>
          <div style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;cursor:pointer"
               :style="{ borderColor: payMethod === 'monnify' ? '#00A86B' : '#dcdfe6' }"
               @click="payMethod = 'monnify'">
            <el-radio value="monnify" size="large">
              <span style="font-weight:600;color:#00A86B">💳 银行卡 / 转账 (Monnify)</span>
            </el-radio>
          </div>
        </el-radio-group>
      </div>
      <el-button class="portal-full-btn" type="primary" :loading="paying"
        @click="payMethod === 'balance' ? payByBalance() : payByMonnify()">
        立即支付 ¥{{ store.selectedPackage?.price }}
      </el-button>
      <div style="text-align:center;margin-top:12px"><el-button link @click="router.back()">← 更换套餐</el-button></div>
    </div>
  </div>
</template>
