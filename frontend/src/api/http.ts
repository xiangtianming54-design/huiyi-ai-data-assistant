import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一返回体 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/api',
  // Text-to-SQL 链路包含多次 LLM 调用，给足超时
  timeout: 90_000
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as Result<unknown>
    if (result && typeof result.code === 'number' && result.code !== 0) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default http
