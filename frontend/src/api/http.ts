import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession, getAccessToken, getRefreshToken, setAccessToken, setRefreshToken } from '../auth/token'

export interface Result<T> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/api',
  timeout: 90_000
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<boolean> | null = null

http.interceptors.response.use(
  (response) => {
    const result = response.data as Result<unknown>
    if (result && typeof result.code === 'number' && result.code !== 0) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  async (error) => {
    const status = error.response?.status
    const original = error.config
    const retryable = original as { _retry?: boolean; url?: string; headers?: Record<string, string> }
    if (status === 401 && original && !retryable._retry && !String(retryable.url || '').includes('/auth/')) {
      retryable._retry = true
      refreshing = refreshing ?? refreshAccess()
      const ok = await refreshing.finally(() => {
        refreshing = null
      })
      if (ok) {
        retryable.headers = { ...retryable.headers, Authorization: `Bearer ${getAccessToken()}` }
        return http(original)
      }
      clearSession()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    if (status === 403) {
      ElMessage.error(error.response?.data?.message || '没有权限执行该操作')
    } else if (status !== 401) {
      ElMessage.error(error.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

async function refreshAccess(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const response = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
      '/api/v1/auth/refresh',
      { refreshToken }
    )
    if (response.data.code !== 0 || !response.data.data?.accessToken) {
      return false
    }
    setAccessToken(response.data.data.accessToken)
    setRefreshToken(response.data.data.refreshToken)
    return true
  } catch {
    return false
  }
}

export default http
