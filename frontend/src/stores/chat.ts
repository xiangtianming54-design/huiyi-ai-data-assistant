import { defineStore } from 'pinia'
import { streamChat } from '../api/chatStream'
import type { Reference } from '../api'

/** 聊天消息 */
export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  references?: Reference[]
  streaming?: boolean
}

// 当前流式请求的中止函数（模块级，避免进入响应式状态）
let stopFn: (() => void) | undefined

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as ChatMessage[],
    sessionId: undefined as string | undefined,
    streaming: false
  }),
  actions: {
    /** 发送问题并开始流式接收 */
    ask(question: string) {
      this.messages.push({ role: 'user', content: question })
      this.messages.push({ role: 'assistant', content: '', streaming: true })
      // 必须通过响应式代理写数据（直接改裸对象引用不触发视图更新）
      const index = this.messages.length - 1
      this.streaming = true

      stopFn = streamChat(question, this.sessionId, {
        onSession: (id) => {
          this.sessionId = id
        },
        onToken: (token) => {
          this.messages[index].content += token
        },
        onReferences: (references) => {
          this.messages[index].references = references
        },
        onError: (message) => {
          if (!this.messages[index].content) {
            this.messages[index].content = message
          }
        },
        onDone: () => {
          this.messages[index].streaming = false
          this.streaming = false
          stopFn = undefined
        }
      })
    },
    /** 停止生成 */
    stop() {
      stopFn?.()
    },
    /** 清空会话（新会话） */
    reset() {
      this.stop()
      this.messages = []
      this.sessionId = undefined
    }
  }
})
