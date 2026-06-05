import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePortalStore = defineStore('portal', () => {
  // 租户/酒店（从 URL 参数或默认）
  const tenantId = ref(1)
  const hotelId = ref(1)

  // 当前会员
  const memberId = ref<number | null>(null)
  const memberInfo = ref<any>(null)

  // 选中的套餐
  const selectedPackage = ref<any>(null)

  return { tenantId, hotelId, memberId, memberInfo, selectedPackage }
})
