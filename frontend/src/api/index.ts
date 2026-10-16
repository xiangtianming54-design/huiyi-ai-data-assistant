import http from './http'
import type { Result } from './http'
import { getWorkspaceId } from '../auth/token'

function workspacePath(suffix: string) {
  const id = getWorkspaceId()
  return `/v1/workspaces/${id}${suffix}`
}

export interface KnowledgeDoc {
  docId: number
  docName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: string
  createdAt: string
}

export interface Reference {
  source: string
  score: number
}

export interface AnalysisResult {
  sessionId: string
  question: string
  sql: string | null
  rows: Record<string, unknown>[]
  summary: { rowCount: number; chartType: string } | null
  answer: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export const knowledgeApi = {
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<Result<KnowledgeDoc>>(workspacePath('/knowledge/upload'), formData)
  },
  list() {
    return http.get<Result<PageResult<KnowledgeDoc>>>(workspacePath('/knowledge/docs'))
  },
  remove(docId: number) {
    return http.delete<Result<void>>(workspacePath(`/knowledge/docs/${docId}`))
  }
}

export interface IntentResult {
  intent: string
  questions?: string[]
}

export interface ChatPart {
  question: string
  intent: string
  answer: string
  references: Reference[]
  analysis?: AnalysisResult
}

export interface ChatAskResult {
  sessionId: string
  parts: ChatPart[]
}

export const chatApi = {
  intent(question: string) {
    return http.post<Result<IntentResult>>(workspacePath('/chat/intent'), { question })
  },
  ask(question: string, sessionId?: string) {
    return http.post<Result<ChatAskResult>>(workspacePath('/chat/ask'), { question, sessionId })
  }
}

export const analysisApi = {
  query(question: string, sessionId?: string) {
    return http.post<Result<AnalysisResult>>(workspacePath('/analysis/query'), { question, sessionId })
  }
}

export interface AuthTokens {
  userId: number
  username: string
  displayName: string
  accessToken: string
  refreshToken: string
  defaultWorkspaceId: number
}

export interface WorkspaceItem {
  workspaceId?: number
  id?: number
  code: string
  name: string
  role?: string
  memberStatus?: string
  status?: string
}

export const authApi = {
  login(username: string, password: string) {
    return http.post<Result<AuthTokens>>('/v1/auth/login', { username, password })
  },
  register(username: string, password: string, displayName: string) {
    return http.post<Result<AuthTokens>>('/v1/auth/register', { username, password, displayName })
  },
  logout(refreshToken: string) {
    return http.post<Result<void>>('/v1/auth/logout', { refreshToken })
  },
  me() {
    return http.get<Result<{ userId: number; username: string; displayName: string; status: string; workspaces: WorkspaceItem[] }>>('/v1/me')
  },
  workspaces() {
    return http.get<Result<WorkspaceItem[]>>('/v1/workspaces')
  }
}
