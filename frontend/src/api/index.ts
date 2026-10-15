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

export const analysisApi = {
  query(question: string, sessionId?: string) {
    return http.post<Result<AnalysisResult>>('/analysis/query', { question, sessionId })
  }
}
