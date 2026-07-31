import axios from 'axios'

const AUTH_TOKEN_KEY = 'litchi.auth.token'
const API_TIMEOUT_MS = 30000
const CHAT_TIMEOUT_MS = 90000
const AGENT_TIMEOUT_MS = 120000

const api = axios.create({
  baseURL: '/api',
  timeout: API_TIMEOUT_MS
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

export interface AgentRunRequest {
  goal: string
  sessionId?: string
  maxSteps?: number
}

export interface AgentStep {
  sequence: number
  tool: string
  reason: string
  status: 'succeeded' | 'failed' | 'awaiting_approval'
  durationMs: number
  output: Record<string, unknown>
  error?: string
}

export interface AgentRunResponse {
  runId: string
  sessionId?: string
  goal: string
  status: 'created' | 'planning' | 'running' | 'waiting_approval' | 'completed' | 'degraded' | 'failed' | 'canceled'
  answer: string
  degraded: boolean
  riskLevel?: 'low' | 'medium' | 'high'
  reviewRequired?: boolean
  startedAt: string
  durationMs: number
  steps: AgentStep[]
  usage: {
    plannedSteps: number
    executedSteps: number
    maxSteps: number
    plannerMode: 'model' | 'fallback'
    writeToolsEnabled: boolean
  }
  checkpoint?: {
    workflowVersion?: string
    checkpointVersion?: number
    currentStep?: number
    nextStep?: string
    pendingTool?: string
    plannedTools?: string[]
    completedTools?: string[]
  }
  pendingAction?: Record<string, unknown>
}

export interface Orchard {
  id: string
  tenantId: string
  ownerId: string
  name: string
  location?: string
  variety?: string
  growthStage?: string
  areaMu?: number
  createdAt: string
  updatedAt: string
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
  collaboration?: {
    activePlans: number
    consultationCount: number
    pendingConsultations: number
    topDisease: string
    avgShopRating?: number | null
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

export interface UpdateStorageSettingsRequest {
  documentStorageDir: string
  documentStateFile: string
}

export interface StorageUpdateResponse {
  message: string
  settings: SystemSettingsResponse
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

export interface StoreProfile {
  shopId: string
  ownerId?: string | null
  ownerUsername?: string | null
  shopName: string
  contactName: string
  phone: string
  wechat: string
  address: string
  serviceArea: string
  specialties: string
  rating: number
  createdAt: string
  updatedAt: string
}

export interface RemedyPlan {
  id: string
  shopId: string
  ownerId?: string | null
  ownerUsername?: string | null
  shopName: string
  title: string
  diseaseTag: string
  stageTag: string
  summary: string
  products: string[]
  usageTips: string[]
  riskNotes: string[]
  inventoryStatus: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface RecommendedPlan {
  planId: string
  shopId: string
  shopName: string
  contactName: string
  phone: string
  wechat: string
  address: string
  serviceArea: string
  rating?: number | null
  title: string
  diseaseTag: string
  stageTag: string
  summary: string
  products: string[]
  usageTips: string[]
  riskNotes: string[]
  inventoryStatus: string
  score: number
  reasonTags: string[]
}

export interface ConsultationRecord {
  id: string
  farmerUserId: string
  farmerUsername: string
  diseaseTag: string
  stageTag: string
  question: string
  planId: string
  planTitle: string
  shopId: string
  shopName: string
  contactName: string
  phone: string
  wechat: string
  status: 'pending' | 'contacted' | 'completed'
  reasonTags: string[]
  createdAt: string
  updatedAt: string
}

export interface ShopTrend {
  diseaseTag: string
  totalConsultations: number
  recentConsultations: number
  latestAt: string
}

export interface EvaluationRubricScore {
  accuracyScore?: number | null
  safetyScore?: number | null
  completenessScore?: number | null
  actionabilityScore?: number | null
}

export interface EvaluationRecord {
  id: string
  sessionId?: string | null
  type: string
  question: string
  referenceAnswer?: string | null
  systemAnswer?: string | null
  autoScore?: number | null
  scoreBreakdown?: EvaluationRubricScore | null
  bleuScore?: number | null
  humanScore?: number | null
  reviewNote?: string | null
  reviewStatus?: string | null
  sourceCount?: number | null
  suggestedAction?: string | null
  improvementHint?: string | null
  sources?: ChatSource[] | null
  evaluated: boolean
  createdAt: string
}

export interface EvaluationStats {
  total: number
  evaluated: number
  avgAutoScore?: number | null
  avgBleuScore?: number | null
  avgHumanScore?: number | null
  reviewed?: number
  reviewPending?: number
  lowScoreCount?: number
  recentAvgAutoScore?: number | null
  previousAvgAutoScore?: number | null
  scoreTrendDelta?: number | null
  byType: Array<{
    type: string
    count: number
    avgAutoScore?: number | null
    avgBleuScore?: number | null
    avgHumanScore?: number | null
  }>
  activeFeedbackRules?: Array<{
    id: string
    category: string
    title: string
    instruction: string
    sourceType: string
    evidenceCount: number
    priority: number
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
  send: (data: ChatRequest) => api.post<ChatResponse>('/chats', data, { timeout: CHAT_TIMEOUT_MS }),
  history: (sessionId: string, page = 1, size = 20) =>
    api.get<PageResponse<ChatHistoryItem>>('/chats/history', {
      params: { sessionId, page, size }
    }),
  sessions: (page = 1, size = 10) =>
    api.get<PageResponse<ChatSessionItem>>('/chats/sessions', {
      params: { page, size }
    })
}

export const agentAPI = {
  run: (data: AgentRunRequest) =>
    api.post<AgentRunResponse>('/agents/runs', data, { timeout: AGENT_TIMEOUT_MS }),
  get: (runId: string) => api.get<AgentRunResponse>(`/agents/runs/${runId}`),
  start: (data: AgentRunRequest) =>
    api.post<AgentRunResponse>('/v1/agent-runs', data, { timeout: API_TIMEOUT_MS }),
  getV1: (runId: string) => api.get<AgentRunResponse>(`/v1/agent-runs/${runId}`),
  confirm: (runId: string, decision: 'approve' | 'reject') =>
    api.post<AgentRunResponse>(`/v1/agent-runs/${runId}/confirm`, { decision }),
  cancel: (runId: string) => api.post<AgentRunResponse>(`/v1/agent-runs/${runId}/cancel`)
}

export const orchardAPI = {
  list: () => api.get<Orchard[]>('/orchards'),
  create: (data: {
    name: string
    location?: string
    variety?: string
    growthStage?: string
    areaMu?: number
  }) => api.post<Orchard>('/orchards', data)
}

export const diagnosisAPI = {
  upload: (formData: FormData) =>
    api.post<DiagnosisResult>('/diagnoses', formData, {
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
  detail: (id: string) => api.get<KnowledgeGraphEntityDetail>(`/kg/entities/${id}`)
}

export const documentAPI = {
  upload: (formData: FormData) =>
    api.post<DocumentRecord>('/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }),
  list: (params?: { page?: number; size?: number; keyword?: string }) =>
    api.get<PageResponse<DocumentRecord>>('/documents', { params }),
  delete: (id: string) => api.delete<{ deleted: boolean; message: string }>(`/documents/${id}`)
}

export const evaluationAPI = {
  questions: (params?: { type?: string; evaluated?: boolean; page?: number; size?: number }) =>
    api.get<PageResponse<EvaluationRecord>>('/evaluations/questions', { params }),
  submitAnswer: (data: { id: string; systemAnswer: string }) =>
    api.post<EvaluationRecord>('/evaluations/answer', data),
  submitScore: (data: { id: string; humanScore: number; reviewNote?: string }) =>
    api.post<EvaluationRecord>('/evaluations/score', data),
  stats: () => api.get<EvaluationStats>('/evaluations/stats')
}

export const systemAPI = {
  health: () => api.get<SystemHealthResponse>('/health'),
  initialize: (scope: 'all' | 'graph' | 'vector' = 'all') =>
    api.post<SystemInitResponse>('/system/init', null, {
      params: { scope }
    }),
  overview: () => api.get<SystemOverviewResponse>('/system/overview'),
  bootstrapDemo: () => api.post<DemoBootstrapResponse>('/system/demo/bootstrap'),
  settings: () => api.get<SystemSettingsResponse>('/system/settings'),
  updateStorage: (data: UpdateStorageSettingsRequest) =>
    api.post<StorageUpdateResponse>('/system/storage', data)
}

export const feedbackAPI = {
  submit: (data: {
    module: string
    overallScore: number
    accuracyScore: number
    practicalityScore: number
    fluencyScore: number
    comment: string
  }) => api.post<FeedbackRecord>('/feedbacks', data),
  stats: () => api.get<FeedbackStats>('/feedbacks/stats')
}

export const shopAPI = {
  profile: () => api.get<StoreProfile>('/shop/profile'),
  saveProfile: (data: {
    shopName: string
    contactName: string
    phone: string
    wechat: string
    address: string
    serviceArea: string
    specialties: string
    rating: number
  }) => api.put<StoreProfile>('/shop/profile', data),
  plans: (page = 1, size = 10) => api.get<PageResponse<RemedyPlan>>('/shop/plans', { params: { page, size } }),
  createPlan: (data: {
    title: string
    diseaseTag: string
    stageTag: string
    summary: string
    products: string[]
    usageTips: string[]
    riskNotes: string[]
    inventoryStatus: string
    active: boolean
  }) => api.post<RemedyPlan>('/shop/plans', data),
  updatePlan: (
    id: string,
    data: {
      title: string
      diseaseTag: string
      stageTag: string
      summary: string
      products: string[]
      usageTips: string[]
      riskNotes: string[]
      inventoryStatus: string
      active: boolean
    }
  ) => api.put<RemedyPlan>(`/shop/plans/${id}`, data),
  deletePlan: (id: string) => api.delete<{ deleted: boolean; message: string }>(`/shop/plans/${id}`),
  trends: () => api.get<ShopTrend[]>('/shop/trends')
}

export const recommendationAPI = {
  list: (params: { diseaseTag?: string; stageTag?: string; query?: string }) =>
    api.get<RecommendedPlan[]>('/plans/recommendations', { params })
}

export const consultationAPI = {
  create: (data: {
    diseaseTag?: string
    stageTag?: string
    question?: string
    planId: string
    reasonTags?: string[]
  }) => api.post<ConsultationRecord>('/consultations', data),
  my: (page = 1, size = 10) => api.get<PageResponse<ConsultationRecord>>('/consultations/my', { params: { page, size } }),
  inbox: (page = 1, size = 10) => api.get<PageResponse<ConsultationRecord>>('/consultations/inbox', { params: { page, size } }),
  updateStatus: (id: string, data: { status: ConsultationRecord['status'] }) =>
    api.post<ConsultationRecord>(`/consultations/${id}/status`, data)
}

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export { AUTH_TOKEN_KEY }
export default api
