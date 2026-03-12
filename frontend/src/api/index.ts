import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export interface ChatRequest {
  question: string
}

export interface GraphEntity {
  label: string
  properties: Record<string, unknown>
}

export interface ChatSource {
  title: string
  content: string
  source: string
  page?: number
  score?: number
}

export interface ChatResponse {
  answer: string
  knowledgeGraph?: {
    entities?: GraphEntity[]
  }
  sources?: ChatSource[]
}

export interface DiagnosisDiseaseInfo {
  name: string
  confidence: number
}

export interface DiagnosisResult {
  disease: string
  confidence: number
  suggestions: string[]
  diseases?: DiagnosisDiseaseInfo[]
  engine?: string
  demoMode?: boolean
  note?: string
}

export interface DocumentRecord {
  id: string
  name: string
  size: number
  contentType: string
  uploadTime: string
  chunkCount: number
  indexed: boolean
  statusMessage: string
}

export interface KnowledgeGraphNode {
  id: string
  label: string
  properties: Record<string, unknown>
}

export interface KnowledgeGraphEdge {
  source: string
  target: string
  label: string
}

export interface KnowledgeGraphResponse {
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
}

export const chatAPI = {
  send: (data: ChatRequest) => api.post<ChatResponse>('/chat', data)
}

export const diagnosisAPI = {
  upload: (formData: FormData) =>
    api.post<DiagnosisResult>('/diagnosis', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
}

export const knowledgeGraphAPI = {
  visualize: (keyword?: string) =>
    api.get<KnowledgeGraphResponse>('/kg/visualize', {
      params: keyword?.trim() ? { keyword: keyword.trim() } : undefined
    })
}

export const documentAPI = {
  upload: (formData: FormData) =>
    api.post<DocumentRecord>('/document', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }),
  list: () => api.get<DocumentRecord[]>('/document'),
  delete: (id: string) => api.delete<{ deleted: boolean; message: string }>(`/document/${id}`)
}

export default api
