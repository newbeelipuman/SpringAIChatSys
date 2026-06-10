<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiGet } from '../../lib/api'
import type {
  AuthHealthResponse,
  HealthResponse,
  MysqlBusinessSummaryDTO,
  MysqlDiagnosticsDTO,
  MysqlTableStatusDTO,
} from '../../lib/types'
import { safeString } from '../../lib/util'

type AnyJson = Record<string, unknown> | unknown[] | string | number | boolean | null
type JsonObject = Record<string, unknown>

const running = ref(false)
const error = ref<string | null>(null)

const springHealth = ref<AnyJson | null>(null)
const ragHealth = ref<HealthResponse | null>(null)
const llmHealth = ref<AnyJson | null>(null)
const llmConfig = ref<AnyJson | null>(null)
const mysqlSummary = ref<MysqlBusinessSummaryDTO | null>(null)
const mysqlDiagnostics = ref<MysqlDiagnosticsDTO | null>(null)
const authHealth = ref<AuthHealthResponse | null>(null)

const isAdmin = computed(() => authHealth.value?.currentRole === 'ADMIN')
const okPill = computed(() => isUpStatus(ragHealth.value?.status))
const ragDetails = computed(() => asObject(ragHealth.value?.details))
const llmHealthDetails = computed(() => asObject(llmHealth.value))
const llmConfigDetails = computed(() => asObject(llmConfig.value))
const springHealthDetails = computed(() => asObject(springHealth.value))
const mysqlEnabled = computed(() => Boolean(ragDetails.value.mysqlEnabled))
const mysqlAvailable = computed(() => Boolean(ragDetails.value.mysqlAvailable))
const chunkCount = computed(() => numberValue(ragDetails.value.chunkCount))
const accountScopeText = computed(() =>
  isAdmin.value
    ? '当前账号具备管理员视角，可查看全局历史和全局诊断，并可在历史页执行用户管理、角色分配、重置他人密码和删除指定用户数据。'
    : '当前账号为普通用户，只展示个人知识、个人提问和个人同步状态。',
)

async function refresh() {
  running.value = true
  error.value = null
  try {
    ;[
      springHealth.value,
      ragHealth.value,
      llmHealth.value,
      llmConfig.value,
      mysqlSummary.value,
      mysqlDiagnostics.value,
      authHealth.value,
    ] = await Promise.all([
      apiGet<AnyJson>('/health'),
      apiGet<HealthResponse>('/demo/health'),
      apiGet<AnyJson>('/llm/health'),
      apiGet<AnyJson>('/llm/config'),
      apiGet<MysqlBusinessSummaryDTO>('/demo/me/mysql-summary'),
      apiGet<MysqlDiagnosticsDTO>('/demo/me/mysql-diagnostics'),
      apiGet<AuthHealthResponse>('/auth/health'),
    ])
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

function asObject(value: unknown): JsonObject {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as JsonObject) : {}
}

function isUpStatus(value: unknown): boolean {
  const status = String(value ?? '').toUpperCase()
  return status === 'UP' || status === 'OK'
}

function numberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function textValue(value: unknown, fallback = '-'): string {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  return String(value)
}

function boolText(value: unknown): string {
  if (value === true) {
    return '是'
  }
  if (value === false) {
    return '否'
  }
  return '-'
}

function enabledText(value: unknown): string {
  return value === true ? '已启用' : value === false ? '未启用' : '-'
}

function availableText(value: unknown): string {
  return value === true ? '可用' : value === false ? '不可用' : '-'
}

function healthText(value: unknown): string {
  return isUpStatus(value) ? '正常' : textValue(value, '未知')
}

function roleText(value: unknown): string {
  const role = String(value ?? '').toUpperCase()
  if (role === 'ADMIN') {
    return '管理员'
  }
  if (role === 'USER') {
    return '普通用户'
  }
  return textValue(value)
}

function sourceText(value: unknown): string {
  const source = String(value ?? '')
  if (source === 'auth-token') {
    return '登录账号'
  }
  if (source === 'x-demo-user') {
    return 'X-Demo-User 演示'
  }
  if (source === 'default-demo-user') {
    return '默认演示用户'
  }
  return textValue(value)
}

function authModeText(value: unknown): string {
  const mode = String(value ?? '')
  if (mode === 'mysql-demo-token') {
    return 'MySQL 演示登录'
  }
  if (mode === 'demo-fallback-only') {
    return '仅演示身份'
  }
  return textValue(value)
}

function mysqlStatusText(value: unknown): string {
  const status = String(value ?? '')
  if (status === 'available') {
    return '连接正常'
  }
  if (status === 'unavailable') {
    return '连接异常'
  }
  if (status === 'not-configured') {
    return '未配置'
  }
  if (status === 'skipped') {
    return '未启用'
  }
  return textValue(value)
}

function vectorStoreText(value: unknown): string {
  const mode = String(value ?? '')
  if (mode === 'milvus') {
    return 'Milvus 向量库'
  }
  if (mode === 'memory') {
    return '内存向量库'
  }
  if (mode === 'temporary-memory') {
    return '临时内存知识'
  }
  return textValue(value)
}

function embeddingModeText(value: unknown): string {
  const mode = String(value ?? '')
  if (mode === 'local-hash') {
    return '本地哈希向量'
  }
  if (mode === 'external') {
    return '外部 Embedding 模型'
  }
  return textValue(value)
}

function chatModeText(value: unknown): string {
  const mode = String(value ?? '')
  if (mode === 'local-demo') {
    return '本地演示回答'
  }
  if (mode === 'spring-ai-openai') {
    return 'Spring AI 外部模型'
  }
  return textValue(value)
}

function alignedText(value: unknown): string {
  if (value === true) {
    return '一致'
  }
  if (value === false) {
    return '不一致'
  }
  return '-'
}

function tableNameText(value: string): string {
  const names: Record<string, string> = {
    app_user: '登录用户表',
    auth_session: '登录会话表',
    demo_user: '演示用户表',
    ingest_history: '知识录入历史表',
    knowledge_document: '知识文档表',
    knowledge_permission: '知识权限表',
    question_history: '提问历史表',
    user_suggestion_cache: '用户建议缓存表',
  }
  return names[value] ?? value
}

function tableStatusText(table: MysqlTableStatusDTO): string {
  if (!table.present) {
    return table.required ? '缺失' : '未创建'
  }
  if (table.missingColumns.length) {
    return `缺少字段：${table.missingColumns.join('、')}`
  }
  return '正常'
}

function roleCountsText(value: Record<string, number> | null | undefined): string {
  const entries = Object.entries(value ?? {})
  if (!entries.length) {
    return '-'
  }
  return entries.map(([role, count]) => `${roleText(role)} ${count}`).join('，')
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="actions" style="justify-content: space-between">
        <div>
          <h2 style="margin: 0 0 6px">运行状态</h2>
          <div class="muted small">普通用户查看个人运行状态；管理员查看全局诊断。</div>
        </div>
        <div class="actions">
          <span v-if="ragHealth" class="pill" :class="okPill ? 'ok' : 'bad'">
            知识库：{{ healthText(ragHealth.status) }}
          </span>
          <button class="btn" :disabled="running" @click="refresh">
            {{ running ? '刷新中…' : '刷新状态' }}
          </button>
        </div>
      </div>

      <div v-if="error" class="danger" style="margin-top: 10px">
        {{ error }}
      </div>
    </section>

    <section class="card">
      <h3 style="margin: 0 0 10px">知识库状态</h3>
      <div class="row">
        <div>
          <div class="muted small">服务状态</div>
          <div class="mono">{{ healthText(ragHealth?.status) }}</div>
        </div>
        <div>
          <div class="muted small">向量库类型</div>
          <div class="mono">{{ vectorStoreText(ragDetails.vectorStoreMode) }}</div>
        </div>
        <div>
          <div class="muted small">知识片段数</div>
          <div class="mono">{{ chunkCount ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">向量生成方式</div>
          <div class="mono">{{ embeddingModeText(ragDetails.embeddingMode) }}</div>
        </div>
      </div>
      <div class="empty-state" style="margin-top: 10px">
        {{
          chunkCount && chunkCount > 0
            ? '知识素材已导入，可以进行知识库问答。'
            : '当前知识片段数为 0，需要回到“知识录入”页重新导入素材。'
        }}
      </div>
      <div v-if="isAdmin" class="row compact-row">
        <div>
          <div class="muted small">默认召回数</div>
          <div class="mono">{{ textValue(ragDetails.defaultTopK) }}</div>
        </div>
        <div>
          <div class="muted small">最低匹配分</div>
          <div class="mono">{{ textValue(ragDetails.minScore) }}</div>
        </div>
        <div>
          <div class="muted small">当前身份来源</div>
          <div class="mono">{{ sourceText(ragDetails.currentIdentitySource) }}</div>
        </div>
        <div>
          <div class="muted small">当前角色</div>
          <div class="mono">{{ roleText(ragDetails.currentRole) }}</div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="actions" style="justify-content: space-between">
        <h3 style="margin: 0">账号与权限</h3>
        <span class="pill" :class="authHealth?.authEnabled ? 'ok' : ''">
          {{ authModeText(authHealth?.authMode) }}
        </span>
      </div>
      <div class="hr"></div>
      <div class="row">
        <div>
          <div class="muted small">当前身份</div>
          <div class="mono">{{ sourceText(authHealth?.currentIdentitySource) }}</div>
        </div>
        <div>
          <div class="muted small">当前角色</div>
          <div class="mono">{{ roleText(authHealth?.currentRole) }}</div>
        </div>
        <div v-if="isAdmin">
          <div class="muted small">管理员接口保护</div>
          <div class="mono">{{ authHealth?.adminEndpointsProtected ? '已开启' : '未开启' }}</div>
        </div>
      </div>
      <div class="empty-state" style="margin-top: 10px">
        {{ accountScopeText }}
      </div>
      <div v-if="isAdmin" class="row compact-row">
        <div>
          <div class="muted small">用户表</div>
          <div class="mono">{{ availableText(authHealth?.mysqlUserTableAvailable) }}</div>
        </div>
        <div>
          <div class="muted small">会话表</div>
          <div class="mono">{{ availableText(authHealth?.authSessionTableAvailable) }}</div>
        </div>
        <div>
          <div class="muted small">权限关系表</div>
          <div class="mono">{{ availableText(authHealth?.permissionRelationAvailable) }}</div>
        </div>
        <div>
          <div class="muted small">管理员账号</div>
          <div class="mono">{{ authHealth?.adminUserAvailable ? '已初始化' : '未初始化' }}</div>
        </div>
      </div>
      <div v-if="authHealth?.lastError" class="danger" style="margin-top: 10px">{{ authHealth.lastError }}</div>
    </section>

    <section class="card">
      <div class="actions" style="justify-content: space-between">
        <h3 style="margin: 0">个人数据同步</h3>
        <span class="pill" :class="mysqlEnabled ? (mysqlAvailable ? 'ok' : 'bad') : ''">
          {{ mysqlEnabled ? mysqlStatusText(ragDetails.mysqlStatus) : '未启用' }}
        </span>
      </div>
      <div class="hr"></div>
      <div class="row">
        <div>
          <div class="muted small">结构化存储</div>
          <div class="mono">{{ enabledText(ragDetails.mysqlEnabled) }}</div>
        </div>
        <div>
          <div class="muted small">同步服务</div>
          <div class="mono">{{ mysqlStatusText(ragDetails.mysqlStatus) }}</div>
        </div>
        <div>
          <div class="muted small">历史同步</div>
          <div class="mono">{{ enabledText(ragDetails.mysqlHistoryMirrorEnabled) }}</div>
        </div>
        <div>
          <div class="muted small">建议缓存</div>
          <div class="mono">{{ enabledText(ragDetails.mysqlSuggestionCacheEnabled) }}</div>
        </div>
      </div>
      <div v-if="mysqlSummary" class="hr"></div>
      <div v-if="mysqlSummary" class="row">
        <div>
          <div class="muted small">我的知识文档</div>
          <div class="mono">{{ mysqlSummary.currentUserKnowledgeDocuments }}</div>
        </div>
        <div>
          <div class="muted small">我的录入记录</div>
          <div class="mono">{{ mysqlSummary.currentUserIngestHistory }}</div>
        </div>
        <div>
          <div class="muted small">我的提问记录</div>
          <div class="mono">{{ mysqlSummary.currentUserQuestionHistory }}</div>
        </div>
        <div>
          <div class="muted small">个人建议缓存</div>
          <div class="mono">{{ mysqlSummary.currentUserSuggestionCacheReady ? '已生成' : '未生成' }}</div>
        </div>
      </div>
      <div v-if="mysqlSummary?.lastError" class="danger" style="margin-top: 10px">
        {{ mysqlSummary.lastError }}
      </div>
      <div
        v-if="ragDetails.mysqlError || ragDetails.mysqlLastError || ragDetails.mysqlSuggestionCacheLastError"
        class="danger"
        style="margin-top: 10px"
      >
        {{ ragDetails.mysqlError || ragDetails.mysqlLastError || ragDetails.mysqlSuggestionCacheLastError }}
      </div>
    </section>

    <section class="card">
      <h3 style="margin: 0 0 10px">模型接入状态</h3>
      <div class="row">
        <div>
          <div class="muted small">问答模式</div>
          <div class="mono">{{ chatModeText(llmConfigDetails.chatMode) }}</div>
        </div>
        <div>
          <div class="muted small">外部模型配置</div>
          <div class="mono">{{ boolText(llmConfigDetails.externalChatConfigured) }}</div>
        </div>
        <div>
          <div class="muted small">API Key</div>
          <div class="mono">{{ llmConfigDetails.apiKeyConfigured ? '已配置' : '未配置' }}</div>
        </div>
        <div>
          <div class="muted small">聊天模型</div>
          <div class="mono">{{ llmConfigDetails.chatModelAvailable ? '可用' : '本地演示' }}</div>
        </div>
        <div>
          <div class="muted small">向量模式</div>
          <div class="mono">{{ embeddingModeText(llmConfigDetails.embeddingMode) }}</div>
        </div>
        <div>
          <div class="muted small">模型健康</div>
          <div class="mono">{{ healthText(llmHealthDetails.status) }}</div>
        </div>
      </div>
      <div v-if="isAdmin" class="row compact-row">
        <div>
          <div class="muted small">聊天模型名称</div>
          <div class="mono">{{ textValue(llmConfigDetails.chatModel) }}</div>
        </div>
        <div>
          <div class="muted small">Embedding 模型</div>
          <div class="mono">{{ textValue(llmConfigDetails.embeddingModel) }}</div>
        </div>
        <div>
          <div class="muted small">服务地址</div>
          <div class="mono">{{ textValue(llmConfigDetails.baseUrl) }}</div>
        </div>
      </div>
    </section>

    <section v-if="isAdmin" class="card">
      <h3 style="margin: 0 0 10px">管理员全局诊断</h3>
      <div class="empty-state" style="margin-bottom: 10px">
        管理员可查看全局历史镜像、表结构、角色统计和认证用户统计。
      </div>
      <div v-if="mysqlSummary" class="row">
        <div>
          <div class="muted small">系统用户数</div>
          <div class="mono">{{ mysqlSummary.totalUsers }}</div>
        </div>
        <div>
          <div class="muted small">知识文档总数</div>
          <div class="mono">{{ mysqlSummary.totalKnowledgeDocuments }}</div>
        </div>
        <div>
          <div class="muted small">录入历史总数</div>
          <div class="mono">{{ mysqlSummary.totalIngestHistory }}</div>
        </div>
        <div>
          <div class="muted small">提问历史总数</div>
          <div class="mono">{{ mysqlSummary.totalQuestionHistory }}</div>
        </div>
      </div>
      <div v-if="mysqlDiagnostics" class="row compact-row">
        <div>
          <div class="muted small">本地审计知识记录</div>
          <div class="mono">{{ mysqlDiagnostics.jsonlKnowledgeRecords }}</div>
        </div>
        <div>
          <div class="muted small">MySQL 知识记录</div>
          <div class="mono">{{ mysqlDiagnostics.mysqlKnowledgeDocuments ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">知识镜像一致性</div>
          <div class="mono">{{ alignedText(mysqlDiagnostics.knowledgeMirrorAligned) }}</div>
        </div>
        <div>
          <div class="muted small">本地审计提问记录</div>
          <div class="mono">{{ mysqlDiagnostics.jsonlQuestionRecords }}</div>
        </div>
        <div>
          <div class="muted small">MySQL 提问记录</div>
          <div class="mono">{{ mysqlDiagnostics.mysqlQuestionHistory ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">提问镜像一致性</div>
          <div class="mono">{{ alignedText(mysqlDiagnostics.questionMirrorAligned) }}</div>
        </div>
        <div>
          <div class="muted small">权限关系数</div>
          <div class="mono">{{ mysqlDiagnostics.mysqlPermissionRelations ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">认证用户数</div>
          <div class="mono">{{ mysqlDiagnostics.mysqlAuthUsers ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">认证会话数</div>
          <div class="mono">{{ mysqlDiagnostics.mysqlAuthSessions ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">管理员数量</div>
          <div class="mono">{{ mysqlDiagnostics.adminUserCount ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">角色分布</div>
          <div class="mono">{{ roleCountsText(mysqlDiagnostics.userRoleCount) }}</div>
        </div>
      </div>
      <div v-if="mysqlDiagnostics?.tables?.length" class="table-status">
        <div
          v-for="table in mysqlDiagnostics.tables"
          :key="table.tableName"
          class="pill"
          :class="table.present && !table.missingColumns.length ? 'ok' : table.required ? 'bad' : ''"
        >
          {{ tableNameText(table.tableName) }}{{ table.required ? '' : '（可选）' }}：{{ tableStatusText(table) }}
        </div>
      </div>
      <div v-if="mysqlDiagnostics?.lastError" class="danger" style="margin-top: 10px">
        {{ mysqlDiagnostics.lastError }}
      </div>
    </section>

    <section v-if="isAdmin" class="card">
      <h3 style="margin: 0 0 10px">后端服务状态</h3>
      <div class="row">
        <div>
          <div class="muted small">Spring Boot</div>
          <div class="mono">{{ healthText(springHealthDetails.status) }}</div>
        </div>
        <div>
          <div class="muted small">RAG 服务</div>
          <div class="mono">{{ healthText(ragHealth?.status) }}</div>
        </div>
        <div>
          <div class="muted small">MySQL</div>
          <div class="mono">{{ mysqlStatusText(ragDetails.mysqlStatus) }}</div>
        </div>
      </div>
    </section>
  </div>
</template>
