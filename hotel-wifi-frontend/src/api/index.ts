import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const instance: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, msg, data } = response.data
    if (code === 200) {
      return data
    }
    if (code === 401) {
      localStorage.removeItem('token')
      router.push('/login')
      return Promise.reject(new Error(msg))
    }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg))
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

// 通用请求方法
export function get<T = any>(url: string, params?: any): Promise<T> {
  return instance.get(url, { params })
}

export function post<T = any>(url: string, data?: any): Promise<T> {
  return instance.post(url, data)
}

export function put<T = any>(url: string, data?: any): Promise<T> {
  return instance.put(url, data)
}

export function del<T = any>(url: string): Promise<T> {
  return instance.delete(url)
}

export default instance
