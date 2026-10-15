import { defineStore } from 'pinia'
import { streamChat } from '../api/chatStream'
import { analysisApi, chatApi, type AnalysisResult, type Reference } from '../api'

/** 聊天消息：analysis 存在时表示数据查询富结果 */
export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  references?: Reference[]
  analysis?: AnalysisResult
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
    /** 发送问题：先意图预检，知识问答走 SSE 流式，数据查询走分析接口 */
    async ask(question: string) {
      let intent = 'knowledge_qa'
      try {
        const response = await chatApi.intent(question)
        intent = response.data.data.intent
      } catch {
        // 预检失败时按知识问答处理（错误提示已由拦截器统一处理）
      }

      this.messages.push({ role: 'user', content: question })
      if (intent === 'data_query') {
        await this.askAnalysis(question)
      } else {
        this.askKnowledge(question)
      }
    },

    /** 知识问答：SSE 流式 */
    askKnowledge(question: string) {
      this.messages.push({ role: 'assistant', content: '', streaming: true })
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

    /** 数据查询：分析接口，富结果卡片 */
    async askAnalysis(question: string) {
      this.messages.push({ role: 'assistant', content: '', streaming: true })
      const index = this.messages.length - 1
      this.streaming = true
      try {
        const response = await analysisApi.query(question, this.sessionId)
        const result = response.data.data
        this.sessionId = result.sessionId
        this.messages[index].analysis = result
      } catch {
        this.messages[index].content = '分析服务暂不可用，请稍后重试。'
      } finally {
        this.messages[index].streaming = false
        this.streaming = false
      }
    },

    /** 停止生成（仅流式） */
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
