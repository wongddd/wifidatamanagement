import axios from 'axios'

const instance = axios.create({
  baseURL: '/api/v1/portal',
  timeout: 15000,
})

instance.interceptors.response.use(
  (resp) => {
    const { code, msg, data } = resp.data
    if (code === 200) return data
    // Portal 端不弹窗，静默返回错误
    return Promise.reject(new Error(msg || '请求失败'))
  },
  (error) => Promise.reject(error)
)

export function get<T = any>(url: string, params?: any): Promise<T> {
  return instance.get(url, { params })
}
export function post<T = any>(url: string, data?: any): Promise<T> {
  return instance.post(url, data)
}

export default instance
