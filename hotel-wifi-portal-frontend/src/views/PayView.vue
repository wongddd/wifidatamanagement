<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const paying = ref(false)
const payMethod = ref<'balance' | 'monnify' | 'wechat' | 'alipay'>('balance')

async function payByBalance() {
  paying.value = true
  try {
    await post('/orders/buy', {
      tenantId: store.tenantId,
      hotelId: store.hotelId,
      memberId: store.memberId,
      packageId: store.selectedPackage?.id,
    })
    ElMessage.success('购买成功！')
    router.push('/success')
  } catch (e: any) {
    ElMessage.error(e.message || '余额不足，请选择其他支付方式')
  } finally { paying.value = false }
}

async function payByMonnify() {
  paying.value = true
  try {
    const res: any = await post('/payment/monnify/create', {
      memberId: store.memberId,
      packageId: store.selectedPackage?.id,
      customerName: store.memberInfo?.realName,
      customerEmail: store.memberInfo?.phone + '@hotel.com',
    })
    // 跳转到 Monnify 支付页面
    if (res.checkoutUrl) {
      window.location.href = res.checkoutUrl
    } else {
      ElMessage.error('支付初始化失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '支付服务暂不可用')
  } finally { paying.value = false }
}

async function payByWechat() {
  ElMessage.info('微信支付功能将在接入微信支付服务商后开放')
}

async function payByAlipay() {
  ElMessage.info('支付宝支付功能将在接入支付宝服务商后开放')
}

function handlePay() {
  switch (payMethod.value) {
    case 'balance': return payByBalance()
    case 'monnify': return payByMonnify()
    case 'wechat': return payByWechat()
    case 'alipay': return payByAlipay()
  }
}
</script>

<template>
  <div class="portal-container">
    <div class="portal-card">
      <div class="portal-title">确认支付</div>
      <div class="portal-subtitle">{{ store.selectedPackage?.packageName }}</div>

      <!-- 金额 -->
      <div style="text-align:center;margin:24px 0">
        <span style="font-size:36px;font-weight:700;color:#f56c6c">¥{{ store.selectedPackage?.price }}</span>
      </div>

      <!-- 支付方式 -->
      <div style="margin-bottom:20px">
        <el-radio-group v-model="payMethod" style="width:100%">
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'balance' ? '#409EFF' : '#dcdfe6' }"
            @click="payMethod = 'balance'"
          >
            <el-radio value="balance" size="large">
              <span style="font-weight:600">💰 余额支付</span>
              <span style="color:#909399;font-size:12px;display:block;margin-top:4px">使用账户余额直接支付</span>
            </el-radio>
          </div>
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'monnify' ? '#00A86B' : '#dcdfe6' }"
            @click="payMethod = 'monnify'"
          >
            <el-radio value="monnify" size="large">
              <span style="font-weight:600;color:#00A86B">💳 银行卡 / 转账</span>
              <span style="color:#909399;font-size:12px;display:block;margin-top:4px">支持Visa/Mastercard/银行转账 (Monnify)</span>
            </el-radio>
          </div>
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'wechat' ? '#09BB07' : '#dcdfe6' }"
            @click="payMethod = 'wechat'"
          >
            <el-radio value="wechat" size="large">
              <span style="font-weight:600;color:#09BB07">💚 微信支付</span>
              <span style="color:#C0C4CC;font-size:12px;display:block;margin-top:4px">即将开放</span>
            </el-radio>
          </div>
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'alipay' ? '#1677FF' : '#dcdfe6' }"
            @click="payMethod = 'alipay'"
          >
            <el-radio value="alipay" size="large">
              <span style="font-weight:600;color:#1677FF">💙 支付宝</span>
              <span style="color:#C0C4CC;font-size:12px;display:block;margin-top:4px">即将开放</span>
            </el-radio>
          </div>
        </el-radio-group>
      </div>

      <el-button
        class="portal-full-btn"
        type="primary"
        :loading="paying"
        @click="handlePay"
      >
        立即支付 ¥{{ store.selectedPackage?.price }}
      </el-button>

      <div style="text-align:center;margin-top:12px">
        <el-button link @click="router.back()">← 更换套餐</el-button>
      </div>
    </div>
  </div>
</template>
