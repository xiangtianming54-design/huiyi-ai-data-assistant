import type { Reference } from './index'
import { getAccessToken, getWorkspaceId } from '../auth/token'

export interface StreamCallbacks {
  onSession?: (sessionId: string) => void
  onToken?: (token: string) => void
  onReferences?: (references: Reference[]) => void
  onError?: (message: string) => void
  onDone?: () => void
}

export function streamChat(question: string, sessionId: string | undefined, callbacks: StreamCallbacks): () => void {
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
      const response = await fetch(`/api/v1/workspaces/${workspaceId}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify(sessionId ? { question, sessionId } : { question }),
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        callbacks.onError?.(`请求失败（HTTP ${response.status}）`)
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
