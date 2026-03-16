import axios from 'axios'

const AUTH_TOKEN_KEY = 'litchi.auth.token'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export interface AuthUser {
  id: string
  username: string
  role: string
  createdAt: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: AuthUser
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  role: 'farmer' | 'technician' | 'shopkeeper'
}

export interface ChatRequest {
  question: string
  sessionId: string
  useKnowledgeGraph?: boolean
  useVectorSearch?: boolean
}

export interface GraphEntity {
  id?: string
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

export interface ChatHistoryItem {
  id: string
  sessionId: string
  question: string
  answer: string
  sources?: ChatSource[]
  knowledgeGraph?: {
    entities?: GraphEntity[]
  }
  createdAt: string
}

export interface ChatSessionItem {
  sessionId: string
  title: string
  lastMessage: string
  updatedAt: string
  messageCount: number
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
  title?: string
  size: number
  contentType: string
  uploadTime: string
  chunkCount: number
  indexed: boolean
  statusMessage: string
  ownerId?: string
  ownerUsername?: string
}

export interface PageResponse<T> {
  total: number
  page: number
  size: number
  items: T[]
}

export interface SystemInitResponse {
  scope: string
  graphInitialized: boolean
  vectorInitialized: boolean
  message: string
}

export interface SystemHealthResponse {
  status: 'healthy' | 'degraded'
  services: Record<string, string>
  diagnosisDetails: {
    engine: string
    demoMode: boolean
    modelLoaded: boolean
  }
  documents: {
    total: number
    indexed: number
  }
  timestamp: string
}

export interface DemoSampleDocument {
  name: string
  title: string
  summary: string
}

export interface SystemOverviewResponse {
  services: SystemHealthResponse
  documents: {
    total: number
    indexed: number
    samples: DemoSampleDocument[]
  }
  knowledgeGraph: {
    nodeCount: number
    edgeCount: number
  }
  diagnosis: {
    engine: string
    demoMode: boolean
    modelLoaded: boolean
  }
  demoReady: boolean
  suggestedQuestions: string[]
  demoFlow: string[]
}

export interface DemoBootstrapResponse {
  message: string
  graphInitialized: boolean
  vectorInitialized: boolean
  importedDocuments: number
  skippedDocuments: number
  totalDocuments: number
  suggestedQuestions: string[]
}

export interface SystemSettingsResponse {
  environment: {
    profile: string
    autoBootstrap: boolean
    startupMaxAttempts: number
    startupRetryDelayMs: number
  }
  storage: {
    mysqlEnabled: boolean
    mysqlUrl: string
    neo4jUri: string
    milvusCollectionName: string
    documentStorageDir: string
    documentStateFile: string
  }
  services: {
    ollamaBaseUrl: string
    ollamaModel: string
    diagnosisServiceUrl: string
  }
  platform: {
    documentsTotal: number
    documentsIndexed: number
    sampleQuestions: string[]
    managedRoles: string[]
  }
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

export interface KnowledgeGraphEntityDetail {
  id: string
  label: string
  name?: string
  properties: Record<string, unknown>
  relations: Array<{
    type: string
    target: {
      id: string
      label: string
      name: string
    }
  }>
}

export interface EvaluationRecord {
  id: number
  type: string
  question: string
  referenceAnswer: string
  systemAnswer?: string | null
  bleuScore?: number | null
  humanScore?: number | null
  evaluated: boolean
  createdAt: string
}

export interface EvaluationStats {
  total: number
  evaluated: number
  avgBleuScore?: number | null
  avgHumanScore?: number | null
  byType: Array<{
    type: string
    count: number
    avgBleuScore?: number | null
    avgHumanScore?: number | null
  }>
}

export interface FeedbackRecord {
  id: string
  userId: string
  username: string
  role: string
  module: string
  overallScore: number
  accuracyScore: number
  practicalityScore: number
  fluencyScore: number
  comment: string
  createdAt: string
}

export interface FeedbackStats {
  total: number
  avgOverallScore?: number | null
  avgAccuracyScore?: number | null
  avgPracticalityScore?: number | null
  avgFluencyScore?: number | null
  byModule: Array<{
    module: string
    count: number
    avgOverallScore?: number | null
  }>
  recent: FeedbackRecord[]
}

export const authAPI = {
  login: (data: LoginRequest) => api.post<AuthResponse>('/auth/login', data),
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data),
  me: () => api.get<AuthUser>('/auth/me'),
  logout: () => api.post<{ success: boolean; message: string }>('/auth/logout')
}

export const chatAPI = {
  send: (data: ChatRequest) => api.post<ChatResponse>('/chat', data),
  history: (sessionId: string, page = 1, size = 20) =>
    api.get<PageResponse<ChatHistoryItem>>('/chat/history', {
      params: { sessionId, page, size }
    }),
  sessions: (page = 1, size = 10) =>
    api.get<PageResponse<ChatSessionItem>>('/chat/sessions', {
      params: { page, size }
    })
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
    }),
  search: (keyword: string, type?: string) =>
    api.get<GraphEntity[]>('/kg/search', {
      params: {
        keyword,
        ...(type ? { type } : {})
      }
    }),
  detail: (id: string) => api.get<KnowledgeGraphEntityDetail>(`/kg/entity/${id}`)
}

export const documentAPI = {
  upload: (formData: FormData) =>
    api.post<DocumentRecord>('/document', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }),
  list: (params?: { page?: number; size?: number; keyword?: string }) =>
    api.get<PageResponse<DocumentRecord>>('/document', { params }),
  delete: (id: string) => api.delete<{ deleted: boolean; message: string }>(`/document/${id}`)
}

export const evaluationAPI = {
  questions: (params?: { type?: string; evaluated?: boolean; page?: number; size?: number }) =>
    api.get<PageResponse<EvaluationRecord>>('/evaluation/questions', { params }),
  submitAnswer: (data: { id: number; systemAnswer: string }) =>
    api.post<EvaluationRecord>('/evaluation/answer', data),
  submitScore: (data: { id: number; humanScore: number }) =>
    api.post<EvaluationRecord>('/evaluation/score', data),
  stats: () => api.get<EvaluationStats>('/evaluation/stats')
}

export const systemAPI = {
  health: () => api.get<SystemHealthResponse>('/health'),
  initialize: (scope: 'all' | 'graph' | 'vector' = 'all') =>
    api.post<SystemInitResponse>('/system/init', null, {
      params: { scope }
    }),
  overview: () => api.get<SystemOverviewResponse>('/system/overview'),
  bootstrapDemo: () => api.post<DemoBootstrapResponse>('/system/demo/bootstrap'),
  settings: () => api.get<SystemSettingsResponse>('/system/settings')
}

export const feedbackAPI = {
  submit: (data: {
    module: string
    overallScore: number
    accuracyScore: number
    practicalityScore: number
    fluencyScore: number
    comment: string
  }) => api.post<FeedbackRecord>('/feedback', data),
  stats: () => api.get<FeedbackStats>('/feedback/stats')
}

export { AUTH_TOKEN_KEY }
export default api
