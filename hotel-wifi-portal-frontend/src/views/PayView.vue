<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/api'
import { usePortalStore } from '@/store/portal'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = usePortalStore()
const paying = ref(false)
const payMethod = ref<'balance' | 'wechat' | 'alipay'>('balance')

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

async function payByWechat() {
  ElMessage.info('微信支付功能将在接入微信支付服务商后开放')
  // TODO: 调微信 JSAPI
}

async function payByAlipay() {
  ElMessage.info('支付宝支付功能将在接入支付宝服务商后开放')
  // TODO: 跳支付宝H5支付
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
            </el-radio>
          </div>
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'wechat' ? '#09BB07' : '#dcdfe6' }"
            @click="payMethod = 'wechat'"
          >
            <el-radio value="wechat" size="large">
              <span style="font-weight:600;color:#09BB07">💚 微信支付</span>
            </el-radio>
          </div>
          <div
            style="border:1px solid #dcdfe6;border-radius:8px;padding:12px 16px;margin-bottom:10px;width:100%;cursor:pointer"
            :style="{ borderColor: payMethod === 'alipay' ? '#1677FF' : '#dcdfe6' }"
            @click="payMethod = 'alipay'"
          >
            <el-radio value="alipay" size="large">
              <span style="font-weight:600;color:#1677FF">💙 支付宝</span>
            </el-radio>
          </div>
        </el-radio-group>
      </div>

      <el-button
        class="portal-full-btn"
        type="primary"
        :loading="paying"
        @click="payMethod === 'balance' ? payByBalance() : payMethod === 'wechat' ? payByWechat() : payByAlipay()"
      >
        立即支付 ¥{{ store.selectedPackage?.price }}
      </el-button>

      <div style="text-align:center;margin-top:12px">
        <el-button link @click="router.back()">← 更换套餐</el-button>
      </div>
    </div>
  </div>
</template>
