<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet, apiPost } from '../../lib/api'
import type { AskResponse, ChatResponse, LlmConfigResponse, RetrievalScope, SearchResponse } from '../../lib/types'
import { clip, formatMs, formatScore, safeString } from '../../lib/util'

type ChatMode = 'search' | 'direct' | 'rag'

const question = ref(localStorage.getItem('chat.question') ?? '')
const topK = ref<number>(Number(localStorage.getItem('chat.topK') ?? '3') || 3)
const scope = ref<RetrievalScope>((localStorage.getItem('chat.scope') as RetrievalScope | null) ?? 'all')
const mode = ref<ChatMode>((localStorage.getItem('chat.mode') as ChatMode | null) ?? 'rag')

const running = ref(false)
const error = ref<string | null>(null)

const chat = ref<ChatResponse | null>(null)
const search = ref<SearchResponse | null>(null)
const direct = ref<AskResponse | null>(null)
const llmConfig = ref<LlmConfigResponse | null>(null)
const mysqlSuggestions = ref<string[]>([])

const sampleQuestions = ['RAG 系统的主要流程是什么？', 'Milvus 和内存向量库有什么区别？', '前端页面需要展示哪些字段？']
const modeLabels: Record<ChatMode, string> = {
  search: '只看片段',
  direct: '直接问模型',
  rag: '知识库回答',
}
const scopeLabels: Record<RetrievalScope, string> = {
  persistent: '持久知识库',
  temporary: '临时知识',
  all: '全部知识',
}
const route = useRoute()

if (typeof route.query.q === 'string' && route.query.q.trim()) {
  question.value = route.query.q
}

watch(
  () => route.query.q,
  (value) => {
    if (typeof value === 'string' && value.trim()) {
      question.value = value
      persist()
    }
  },
)

const canAsk = computed(() => question.value.trim().length > 0 && !running.value)
const hasOutput = computed(() => Boolean(chat.value || search.value || direct.value))
const currentModeLabel = computed(() => modeLabels[mode.value])
const displayQuestions = computed(() => (mysqlSuggestions.value.length ? mysqlSuggestions.value : sampleQuestions))
const directStatus = computed(() => {
  if (!llmConfig.value) return '模型状态读取中'
  if (llmConfig.value.externalChatConfigured) return `${llmConfig.value.chatModel} 已连接`
  if (!llmConfig.value.activeProfiles.includes('deepseek')) return 'DeepSeek profile 未启用'
  if (!llmConfig.value.apiKeyConfigured) return 'DeepSeek API Key 未配置'
  if (!llmConfig.value.chatModelAvailable) return 'ChatModel 未加载'
  return 'DeepSeek 未连接'
})

const chunkById = computed(() => {
  const map = new Map<string, { content: string; score: number }>()
  for (const chunk of chat.value?.retrievedChunks ?? []) {
    map.set(chunk.chunkId, { content: chunk.content, score: chunk.score })
  }
  return map
})

function isThinContent(content: string): boolean {
  const compact = content.replace(/\s+/g, '')
  return compact.length <= 12 || compact.endsWith(':') || compact.endsWith('：')
}

function chunkPreview(content: string): string {
  if (!content.trim()) return '该片段没有返回正文内容。'
  return content
}

function chunkHint(content: string): string | null {
  if (!content.trim()) {
    return '没有匹配到可展示的正文。可以先到“写入”页导入素材，或换一个更具体的问题。'
  }
  if (isThinContent(content)) {
    return '当前只匹配到标题或短句，信息量较少。可以把“匹配片段数”调大，或换成更完整的问题。'
  }
  return null
}

function persist() {
  localStorage.setItem('chat.question', question.value)
  localStorage.setItem('chat.topK', String(topK.value))
  localStorage.setItem('chat.scope', scope.value)
  localStorage.setItem('chat.mode', mode.value)
}

function useExample(value: string) {
  question.value = value
  persist()
}

function clearResults() {
  chat.value = null
  search.value = null
  direct.value = null
}

function onComposerKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && canAsk.value) {
    submit()
  }
}

function setMode(next: ChatMode) {
  mode.value = next
  persist()
}

async function refreshLlmConfig() {
  try {
    llmConfig.value = await apiGet<LlmConfigResponse>('/llm/config')
  } catch {
    llmConfig.value = null
  }
}

async function refreshSuggestions() {
  try {
    mysqlSuggestions.value = await apiGet<string[]>('/demo/me/suggestions')
  } catch {
    mysqlSuggestions.value = []
  }
}

function submit() {
  if (mode.value === 'search') {
    onlySearch()
    return
  }
  if (mode.value === 'direct') {
    askDirect()
    return
  }
  ask()
}

async function ask() {
  error.value = null
  clearResults()
  running.value = true
  persist()
  try {
    chat.value = await apiPost<ChatResponse>('/demo/chat', {
      question: question.value,
      topK: topK.value,
      scope: scope.value,
    })
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function askDirect() {
  error.value = null
  clearResults()
  running.value = true
  persist()
  try {
    await refreshLlmConfig()
    direct.value = await apiPost<AskResponse>('/llm/ask', {
      question: question.value,
    })
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function onlySearch() {
  error.value = null
  clearResults()
  running.value = true
  persist()
  try {
    search.value = await apiPost<SearchResponse>('/demo/search', {
      question: question.value,
      topK: topK.value,
      scope: scope.value,
    })
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

onMounted(() => {
  refreshLlmConfig()
  refreshSuggestions()
})
</script>

<template>
  <div class="chat-workspace">
    <section class="conversation-panel">
      <div v-if="!hasOutput" class="welcome">
        <h2>智能问答</h2>
        <p class="muted">左侧用于查看历史对话、知识库记录和临时知识；这里专注展示本次问题的回答、引用和匹配片段。</p>
        <div class="suggestions">
          <button
            v-for="item in displayQuestions"
            :key="item"
            class="suggestion"
            type="button"
            @click="useExample(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>

      <div v-if="error" class="inline-error">{{ error }}</div>

      <div v-if="hasOutput" class="turn">
        <div class="bubble user-bubble">{{ chat?.question ?? search?.question ?? question }}</div>

        <div v-if="direct" class="response-layout single">
          <article class="answer-card">
            <div class="result-head">
              <span>模型直接回答</span>
              <span class="muted small"
                >本次模式：直接问模型 · 服务模式：{{ direct.mode }} · {{ formatMs(direct.elapsedMs) }}</span
              >
            </div>
            <div class="answer">{{ direct.answer }}</div>
          </article>
        </div>

        <div v-if="chat" class="response-layout">
          <article class="answer-card">
            <div class="result-head">
              <span>知识库回答</span>
              <span class="muted small">
                本次模式：知识库回答 · 检索范围：{{ scopeLabels[scope] }} · 服务模式：{{ chat.mode }} ·
                {{ formatMs(chat.elapsedMs) }}
              </span>
            </div>
            <div class="answer">{{ chat.answer }}</div>
          </article>

          <aside class="evidence-panel">
            <div class="panel-title">引用来源</div>
            <div v-if="!chat.citations.length" class="empty-state small">
              没有找到可引用的素材片段。请先导入素材，或换一个更具体的问题。
            </div>
            <div v-else class="evidence-list">
              <div v-for="(c, idx) in chat.citations" :key="c.chunkId + idx" class="evidence-item">
                <div class="evidence-top">
                  <span>{{ idx + 1 }}. {{ c.documentName }}</span>
                  <span class="mono">{{ formatScore(c.score) }}</span>
                </div>
                <div class="muted small mono">{{ c.chunkId }}</div>
                <div class="muted small">
                  {{ clip(chunkById.get(c.chunkId)?.content ?? '', 96) || '未返回正文预览' }}
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>

      <section v-if="chat?.retrievedChunks.length" class="detail-section">
        <details open>
          <summary>匹配到的原文片段</summary>
          <div class="chunk-list">
            <div v-for="chunk in chat.retrievedChunks" :key="chunk.chunkId" class="chunk-row">
              <div class="chunk-meta">
                <span>{{ chunk.documentName }}</span>
                <span class="pill"
                  >相关度 <span class="mono">{{ formatScore(chunk.score) }}</span></span
                >
              </div>
              <div class="muted small mono">{{ chunk.chunkId }} · {{ chunk.source }}</div>
              <div v-if="chunkHint(chunk.content)" class="empty-state small">{{ chunkHint(chunk.content) }}</div>
              <div class="chunk-content">{{ chunkPreview(chunk.content) }}</div>
            </div>
          </div>
        </details>
      </section>

      <section v-if="search" class="detail-section">
        <div class="result-head">
          <span>匹配片段</span>
          <span class="muted small"
            >本次模式：只看片段 · 检索范围：{{ scopeLabels[scope] }} · {{ formatMs(search.elapsedMs) }}</span
          >
        </div>
        <div v-if="!search.retrievedChunks.length" class="empty-state">
          没有找到匹配片段。请先到“知识录入”导入素材，或把问题换成素材中更可能出现的关键词。
        </div>
        <div v-else class="chunk-list">
          <div v-for="chunk in search.retrievedChunks" :key="chunk.chunkId" class="chunk-row">
            <div class="chunk-meta">
              <span>{{ chunk.documentName }}</span>
              <span class="pill"
                >相关度 <span class="mono">{{ formatScore(chunk.score) }}</span></span
              >
            </div>
            <div class="muted small mono">{{ chunk.chunkId }} · {{ chunk.source }}</div>
            <div v-if="chunkHint(chunk.content)" class="empty-state small">{{ chunkHint(chunk.content) }}</div>
            <div class="chunk-content">{{ chunkPreview(chunk.content) }}</div>
          </div>
        </div>
      </section>
    </section>

    <section class="composer">
      <textarea
        v-model="question"
        :placeholder="`输入问题，Ctrl + Enter 使用“${currentModeLabel}”`"
        @keydown="onComposerKeydown"
      ></textarea>
      <div class="composer-footer">
        <div class="composer-left">
          <div class="mode-picker" aria-label="提问模式">
            <button type="button" :class="{ active: mode === 'search' }" @click="setMode('search')">只看片段</button>
            <button type="button" :class="{ active: mode === 'direct' }" @click="setMode('direct')">直接问模型</button>
            <button type="button" :class="{ active: mode === 'rag' }" @click="setMode('rag')">知识库回答</button>
          </div>
          <div class="composer-options" v-if="mode !== 'direct'">
            <label class="compact-field">
              <span>片段</span>
              <input v-model.number="topK" type="number" min="1" max="20" step="1" @change="persist" />
            </label>
            <label class="compact-field scope-field">
              <span>范围</span>
              <select v-model="scope" @change="persist">
                <option value="persistent">持久知识库</option>
                <option value="temporary">临时知识</option>
                <option value="all">全部知识</option>
              </select>
            </label>
          </div>
          <button
            v-else
            class="model-status"
            type="button"
            :class="{
              ok: Boolean(llmConfig?.externalChatConfigured),
              bad: Boolean(llmConfig && !llmConfig.externalChatConfigured),
            }"
            @click="refreshLlmConfig"
          >
            <span class="status-dot"></span>
            {{ directStatus }}
          </button>
        </div>
        <div class="composer-actions">
          <button class="send-btn" type="button" :disabled="!canAsk" :title="currentModeLabel" @click="submit">
            {{ running ? '…' : '↑' }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-workspace {
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 16px;
}

.conversation-panel {
  min-height: 380px;
}

.welcome {
  max-width: 720px;
  padding: 56px 4px 20px;
}

.welcome h2 {
  margin: 0 0 10px;
  font-size: 30px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.suggestion {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 8px;
  padding: 9px 11px;
  cursor: pointer;
}

.inline-error {
  margin-bottom: 12px;
  color: #b91c1c;
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: 8px;
  padding: 10px 12px;
}

.turn {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bubble {
  width: fit-content;
  max-width: min(720px, 100%);
  border-radius: 14px;
  padding: 12px 14px;
  white-space: pre-wrap;
}

.user-bubble {
  align-self: flex-end;
  background: #e8eef7;
  color: #111827;
}

.response-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 14px;
  align-items: start;
}

.response-layout.single {
  grid-template-columns: minmax(0, 1fr);
}

.answer-card,
.evidence-panel,
.detail-section,
.composer {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.answer-card,
.evidence-panel,
.detail-section {
  padding: 16px;
}

.result-head,
.chunk-meta,
.evidence-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-weight: 700;
}

.answer {
  margin-top: 14px;
}

.panel-title {
  font-weight: 700;
  margin-bottom: 10px;
}

.evidence-list,
.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.evidence-item,
.chunk-row {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
}

.detail-section summary {
  cursor: pointer;
  font-weight: 700;
  user-select: none;
}

.chunk-list {
  margin-top: 12px;
}

.chunk-content {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
  white-space: pre-wrap;
}

.composer {
  position: sticky;
  bottom: 16px;
  padding: 0;
  overflow: hidden;
  border-radius: 20px;
  border-color: #dbe3ef;
  box-shadow:
    0 12px 28px rgba(15, 23, 42, 0.08),
    0 1px 2px rgba(15, 23, 42, 0.05);
}

.composer textarea {
  width: 100%;
  min-height: 116px;
  max-height: 220px;
  padding: 20px 20px 12px;
  resize: none;
  overflow-y: auto;
  scrollbar-gutter: stable;
  border: 0;
  outline: none;
  font: inherit;
  font-size: 16px;
  line-height: 1.6;
  color: inherit;
  display: block;
  background: transparent;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid #eef2f7;
  padding: 12px 14px 14px;
  background: linear-gradient(180deg, #fff 0%, #fbfcfe 100%);
}

.composer-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.mode-picker {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #d5deea;
  border-radius: 14px;
  background: #f3f6fb;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.mode-picker button {
  border: 0;
  background: transparent;
  color: #475569;
  padding: 9px 14px;
  border-radius: 10px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 14px;
  transition:
    background-color 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.mode-picker button.active {
  background: #2563eb;
  color: #fff;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.22);
}

.composer-options,
.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.composer-options label {
  color: #64748b;
  font-size: 12px;
}

.compact-field {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid #d5deea;
  border-radius: 12px;
  background: #fff;
}

.compact-field span {
  color: #64748b;
  font-size: 12px;
}

.composer-options input {
  width: 54px;
  padding: 0;
  border: 0;
  outline: none;
  border-radius: 0;
  font: inherit;
}

.composer-options select {
  min-width: 112px;
  padding: 0 4px 0 0;
  border: 0;
  outline: none;
  border-radius: 0;
  background: #fff;
  color: inherit;
  font: inherit;
}

.model-status {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #d5deea;
  border-radius: 999px;
  padding: 0 13px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
}

.model-status.ok {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.model-status.bad {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.75;
}

.send-btn {
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: 50%;
  background: #0f172a;
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.22);
  transition:
    transform 0.15s ease,
    background-color 0.15s ease,
    opacity 0.15s ease;
}

.send-btn:hover:not(:disabled) {
  background: #020617;
  transform: translateY(-1px);
}

.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 980px) {
  .response-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .composer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .composer-left,
  .mode-picker {
    width: 100%;
  }

  .mode-picker button {
    flex: 1;
  }

  .model-status {
    width: 100%;
    justify-content: center;
    white-space: normal;
  }

  .composer-actions .btn {
    flex: 1;
  }

  .composer-actions {
    justify-content: flex-end;
  }
}
</style>
