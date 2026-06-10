export type RetrievalScope = 'persistent' | 'temporary' | 'all'

export interface IngestRequest {
  docId?: string | null
  documentName?: string | null
  content: string
  source?: string | null
}

export interface IngestResponse {
  docId: string | null
  documentName: string | null
  chunkCount: number
  embeddingDimensions: number
  vectorStoreMode: string
  elapsedMs: number
}

export interface DemoMaterialDTO {
  filename: string
  docId: string
  documentName: string
  displayName: string
  category: string
  description: string
  sampleQuestions: string[]
  source: string
  bytes: number
}

export interface IngestMaterialsItemDTO {
  filename: string
  ingest: IngestResponse | null
  error: string | null
}

export interface IngestMaterialsResponse {
  items: IngestMaterialsItemDTO[]
  successCount: number
  failCount: number
  totalChunks: number
  vectorStoreMode: string | null
  elapsedMs: number
}

export interface ChatRequest {
  question: string
  topK?: number | null
  scope?: RetrievalScope
}

export interface CitationDTO {
  chunkId: string
  docId: string
  documentName: string
  source: string
  score: number
}

export interface RetrievedChunkDTO {
  chunkId: string
  docId: string
  documentName: string
  content: string
  source: string
  score: number
}

export interface ChatResponse {
  question: string
  answer: string
  citations: CitationDTO[]
  retrievedChunks: RetrievedChunkDTO[]
  elapsedMs: number
  mode: string
}

export interface AskResponse {
  answer: string
  mode: string
  elapsedMs: number
}

export interface SearchRequest {
  question: string
  topK?: number | null
  scope?: RetrievalScope
}

export interface SearchResponse {
  question: string
  topK: number
  retrievedChunks: RetrievedChunkDTO[]
  elapsedMs: number
}

export interface HealthResponse {
  status: string
  details: Record<string, unknown>
}

export interface AuthUserDTO {
  userKey: string
  username: string
  displayName: string
  role: string
  source: string
  authenticated: boolean
}

export interface AdminUserDTO {
  userKey: string
  username: string
  role: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface AdminUserDataDeleteResponse {
  userKey: string
  clearedKnowledgeRecords: number
  clearedQuestionRecords: number
  clearedTemporaryChunks: number
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface AuthResponse {
  token: string
  tokenType: string
  user: AuthUserDTO
}

export interface AuthHealthResponse {
  authEnabled: boolean
  authMode: string
  currentIdentitySource: string
  currentRole: string
  mysqlUserTableAvailable: boolean
  authSessionTableAvailable: boolean
  permissionRelationAvailable: boolean
  adminUserAvailable: boolean
  adminEndpointsProtected: boolean
  lastError: string
}

export interface MysqlBusinessSummaryDTO {
  mysqlEnabled: boolean
  userId: string
  totalUsers: number
  totalKnowledgeDocuments: number
  totalIngestHistory: number
  totalQuestionHistory: number
  currentUserKnowledgeDocuments: number
  currentUserIngestHistory: number
  currentUserQuestionHistory: number
  currentUserSuggestionCacheReady: boolean
  lastError: string
}

export interface MysqlTableStatusDTO {
  tableName: string
  required: boolean
  present: boolean
  missingColumns: string[]
}

export interface MysqlDiagnosticsDTO {
  mysqlEnabled: boolean
  mysqlAvailable: boolean
  userId: string
  tables: MysqlTableStatusDTO[]
  jsonlKnowledgeRecords: number
  jsonlQuestionRecords: number
  mysqlKnowledgeDocuments: number | null
  mysqlQuestionHistory: number | null
  mysqlPermissionRelations: number | null
  mysqlAuthUsers: number | null
  mysqlAuthSessions: number | null
  adminUserCount: number | null
  userRoleCount: Record<string, number>
  knowledgeMirrorAligned: boolean | null
  questionMirrorAligned: boolean | null
  lastError: string
}

export interface HistoryClearResponse {
  clearedCount: number
}

export interface LlmConfigResponse {
  baseUrl: string
  chatModel: string
  embeddingModel: string
  chatMode: string
  externalChatConfigured: boolean
  apiKeyConfigured: boolean
  chatModelAvailable: boolean
  activeProfiles: string[]
  embeddingMode: string
}

export interface KnowledgeRecordDTO {
  id: string
  userId: string
  docId: string
  documentName: string
  source: string
  vectorStoreMode: string
  chunkCount: number
  embeddingDimensions: number
  contentPreview: string
  createdAt: string
}

export interface QuestionRecordDTO {
  id: string
  userId: string
  question: string
  answerPreview: string
  mode: string
  requestType: string
  topK: number
  retrievalScope: RetrievalScope
  retrievedChunkCount: number
  citationChunkIds: string[]
  elapsedMs: number
  createdAt: string
}
