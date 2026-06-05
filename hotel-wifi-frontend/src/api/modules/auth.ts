import { post, get } from '@/api'

export interface LoginParams {
  tenantId: number
  username: string
  password: string
}

export interface LoginResult {
  token: string
  userId: number
  tenantId: number
  username: string
  realName: string
  role: string
}

export function login(params: LoginParams): Promise<LoginResult> {
  return post('/auth/login', params)
}

export function getMe(): Promise<LoginResult> {
  return get('/auth/me')
}
