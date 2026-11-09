import { ElMessage } from 'element-plus'
import type { Reference } from './index'
import { saveObservabilityIds } from '../auth/requestId'
import { getAccessToken, getWorkspaceId } from '../auth/token'
import { quotaErrorKind } from '../quota/quotaView'

export interface StreamCallbacks {
  onSession?: (sessionId: string) => void
  onToken?: (token: string) => void
  onReferences?: (references: Reference[]) => void
  onError?: (message: string) => void
  onDone?: () => void
  /** 入口被限流/额度/等级拦截（HTTP 200 的 JSON Result）：不吐字，只置阻断标记 */
  onQuotaBlock?: (kind: 'rate' | 'quota' | 'tier3') => void
}

export function streamChat(
  question: string,
  sessionId: string | undefined,
  admissionTicket: string | undefined,
  callbacks: StreamCallbacks
): () => void {
  const controller = new AbortController()

  void (async () => {
    try {
      const workspaceId = getWorkspaceId()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      }
      const token = getAccessToken()
      if (token) {
        headers.Authorization = `Bearer ${token}`
      }
      const payload: { question: string; sessionId?: string; admissionTicket?: string } = { question }
      if (sessionId) payload.sessionId = sessionId
      if (admissionTicket !== undefined) payload.admissionTicket = admissionTicket
      const response = await fetch(`/api/v1/workspaces/${workspaceId}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal
      })
      saveObservabilityIds(response.headers)
      if (!response.ok || !response.body) {
        callbacks.onError?.(`请求失败（HTTP ${response.status}）`)
        callbacks.onDone?.()
        return
      }

      // 业务异常由后端全局异常处理器写成 HTTP 200 的 JSON Result，而不是 SSE 流
      if ((response.headers.get('Content-Type') ?? '').includes('application/json')) {
        const result = (await response.json().catch(() => null)) as {
          code?: unknown
          message?: unknown
        } | null
        const code = result && typeof result.code === 'number' ? result.code : null
        const message = result && typeof result.message === 'string' ? result.message : '请求失败'
        const kind = code === null ? null : quotaErrorKind(code)
        if (kind) {
          // 与预检一致：提示并置阻断标记，不让调用方追加空气泡
          ElMessage.error(message)
          callbacks.onQuotaBlock?.(kind)
        } else {
          callbacks.onError?.(message)
        }
        callbacks.onDone?.()
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let eventName = 'message'
      let dataLines: string[] = []

      const dispatch = () => {
        const data = dataLines.join('\n')
        if (eventName === 'session') {
          callbacks.onSession?.(data)
        } else if (eventName === 'token') {
          callbacks.onToken?.(data)
        } else if (eventName === 'references') {
          try {
            callbacks.onReferences?.(JSON.parse(data) as Reference[])
          } catch {
            // ignore
          }
        } else if (eventName === 'error') {
          callbacks.onError?.(data)
        }
      }

      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const rawLine of lines) {
          const line = rawLine.replace(/\r$/, '')
          if (line === '') {
            if (dataLines.length > 0) dispatch()
            eventName = 'message'
            dataLines = []
            continue
          }
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).replace(/^ /, ''))
          }
        }
      }
      if (dataLines.length > 0) dispatch()
      callbacks.onDone?.()
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        callbacks.onError?.('连接中断，请重试')
      }
      callbacks.onDone?.()
    }
  })()

  return () => controller.abort()
}
