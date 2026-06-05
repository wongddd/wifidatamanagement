import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getMe, type LoginResult } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<LoginResult | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')

  async function login(params: { tenantId: number; username: string; password: string }) {
    const result = await loginApi(params)
    token.value = result.token
    userInfo.value = result
    localStorage.setItem('token', result.token)
    return result
  }

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      userInfo.value = await getMe()
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, role, login, fetchUserInfo, logout }
})
