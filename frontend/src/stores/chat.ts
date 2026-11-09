import { defineStore } from 'pinia'
import { streamChat } from '../api/chatStream'
import { analysisApi, chatApi, quotaApi, type AnalysisResult, type QuotaSnapshot, type Reference } from '../api'
import { COMPOUND_WAIT_HINT } from '../components/assistantWaitHint'
import { quotaErrorKind } from '../quota/quotaView'

type QuotaBlockKind = 'rate' | 'quota' | 'tier3'

/** 二次检查被限流/额度/等级拦截时，取阻断类型；其他错误为 null。 */
function quotaBlockOf(error: unknown): QuotaBlockKind | null {
  const code = (error as { code?: unknown }).code
  return typeof code === 'number' ? quotaErrorKind(code) : null
}

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
    streaming: false,
    quota: null as QuotaSnapshot | null,
    quotaBlock: null as 'rate' | 'quota' | 'tier3' | null
  }),
  actions: {
    /** 发送问题：先意图预检，知识问答走 SSE 流式，数据查询走分析接口，复合问题走统一问答 */
    async ask(question: string) {
      let intent = 'knowledge_qa'
      let admissionTicket: string | undefined
      try {
        const response = await chatApi.intent(question)
        intent = response.data.data.intent
        const ticket = response.data.data.admissionTicket
        if (typeof ticket === 'string') {
          admissionTicket = ticket
        }
      } catch (error) {
        const kind = quotaBlockOf(error)
        if (kind) {
          this.quotaBlock = kind
          return
        }
        // 其他预检失败仍按知识问答处理（错误提示已由拦截器统一处理）
      }

      this.quotaBlock = null
      this.messages.push({ role: 'user', content: question })
      if (intent === 'compound') {
        await this.askCompound(question, admissionTicket)
      } else if (intent === 'data_query') {
        await this.askAnalysis(question, admissionTicket)
      } else {
        this.askKnowledge(question, admissionTicket)
      }
    },

    /** 进入聊天页，或一问结束后，拉一次今日额度。不在吐字时调用。 */
    async refreshQuota() {
      try {
        const response = await quotaApi.mine()
        this.quota = response.data.data
      } catch {
        // 拦截器已提示；额度条保持上一次的数
      }
    },

    /** 复合问题：统一问答接口，每个子问题一个气泡（知识→Markdown，数据→富结果卡片） */
    async askCompound(question: string, admissionTicket: string | undefined) {
      this.streaming = true
      const markerIndex = this.messages.push({ role: 'assistant', content: COMPOUND_WAIT_HINT, streaming: true }) - 1
      try {
        const response = await chatApi.ask(question, this.sessionId, admissionTicket)
        const result = response.data.data
        this.sessionId = result.sessionId
        // 移除占位气泡，按子问题逐个渲染
        this.messages.splice(markerIndex, 1)
        for (const part of result.parts) {
          if (part.analysis) {
            this.messages.push({ role: 'assistant', content: '', analysis: part.analysis })
          } else {
            this.messages.push({ role: 'assistant', content: part.answer, references: part.references })
          }
        }
      } catch (error) {
        const kind = quotaBlockOf(error)
        if (kind) {
          // 二次检查被拦截：与预检一致，移除占位气泡并置阻断标记（提示已由拦截器统一处理）
          this.messages.splice(markerIndex, 1)
          this.quotaBlock = kind
        } else {
          this.messages[markerIndex].content = '问答服务暂不可用，请稍后重试。'
        }
      } finally {
        if (this.messages[markerIndex]) {
          this.messages[markerIndex].streaming = false
        }
        this.streaming = false
        void this.refreshQuota()
      }
    },

    /** 知识问答：SSE 流式 */
    askKnowledge(question: string, admissionTicket: string | undefined) {
      this.messages.push({ role: 'assistant', content: '', streaming: true })
      const index = this.messages.length - 1
      this.streaming = true

      stopFn = streamChat(question, this.sessionId, admissionTicket, {
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
        onQuotaBlock: (kind) => {
          // 流式入口被拦截：不留下空气泡，与预检一样只置阻断标记
          this.messages.splice(index, 1)
          this.quotaBlock = kind
        },
        onDone: () => {
          const message = this.messages[index]
          if (message) {
            message.streaming = false
          }
          this.streaming = false
          stopFn = undefined
          void this.refreshQuota()
        }
      })
    },

    /** 数据查询：分析接口，富结果卡片 */
    async askAnalysis(question: string, admissionTicket: string | undefined) {
      this.messages.push({ role: 'assistant', content: '', streaming: true })
      const index = this.messages.length - 1
      this.streaming = true
      try {
        const response = await analysisApi.query(question, this.sessionId, admissionTicket)
        const result = response.data.data
        this.sessionId = result.sessionId
        this.messages[index].analysis = result
      } catch (error) {
        const kind = quotaBlockOf(error)
        if (kind) {
          // 二次检查被拦截：与预检一致，移除占位气泡并置阻断标记（提示已由拦截器统一处理）
          this.messages.splice(index, 1)
          this.quotaBlock = kind
        } else {
          this.messages[index].content = '分析服务暂不可用，请稍后重试。'
        }
      } finally {
        const message = this.messages[index]
        if (message) {
          message.streaming = false
        }
        this.streaming = false
        void this.refreshQuota()
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
    },

    resetConversation() {
      this.reset()
    }
  }
})
