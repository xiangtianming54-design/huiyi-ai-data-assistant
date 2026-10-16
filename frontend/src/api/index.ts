import http from './http'
import type { Result } from './http'

/** 知识库文档 */
export interface KnowledgeDoc {
  docId: number
  docName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: string
  createdAt: string
}

/** 引用来源 */
export interface Reference {
  source: string
  score: number
}

/** 分析结果 */
export interface AnalysisResult {
  sessionId: string
  question: string
  sql: string | null
  rows: Record<string, unknown>[]
  summary: { rowCount: number; chartType: string } | null
  answer: string
}

export const knowledgeApi = {
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<Result<KnowledgeDoc>>('/knowledge/upload', formData)
  },
  list() {
    return http.get<Result<KnowledgeDoc[]>>('/knowledge/docs')
  },
  remove(docId: number) {
    return http.delete<Result<void>>(`/knowledge/docs/${docId}`)
  }
}

/** 意图预检结果（compound 时携带子问题） */
export interface IntentResult {
  intent: string
  questions?: string[]
}

/** 复合问答的单个子问题结果 */
export interface ChatPart {
  question: string
  intent: string
  answer: string
  references: Reference[]
  analysis?: AnalysisResult
}

/** 统一问答响应 */
export interface ChatAskResult {
  sessionId: string
  parts: ChatPart[]
}

export const chatApi = {
  /** 意图预检：knowledge_qa 走 SSE 流式，data_query 走分析接口，compound 走统一问答 */
  intent(question: string) {
    return http.post<Result<IntentResult>>('/chat/intent', { question })
  },
  /** 统一问答（复合问题多部分回答） */
  ask(question: string, sessionId?: string) {
    return http.post<Result<ChatAskResult>>('/chat/ask', { question, sessionId })
  }
}

export const analysisApi = {
  query(question: string, sessionId?: string) {
    return http.post<Result<AnalysisResult>>('/analysis/query', { question, sessionId })
  }
}
