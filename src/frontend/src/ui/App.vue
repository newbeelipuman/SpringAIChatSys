<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiDelete, apiGet, apiPost, setAuthToken } from '../lib/api'
import type { AuthResponse, AuthUserDTO, KnowledgeRecordDTO, QuestionRecordDTO } from '../lib/types'
import { clip } from '../lib/util'

const route = useRoute()
const title = computed(() => (route.meta.title as string | undefined) ?? '知识库问答演示')
const authReady = ref(false)
const authUser = ref<AuthUserDTO | null>(null)
const authUsername = ref('')
const authPassword = ref('')
const authConfirmPassword = ref('')
const authMode = ref<'login' | 'register'>('login')
const authError = ref<string | null>(null)
const authNotice = ref<string | null>(null)
const authRunning = ref(false)
const passwordDialogOpen = ref(false)
const changeCurrentPassword = ref('')
const changeNewPassword = ref('')
const changeConfirmPassword = ref('')
const changePasswordError = ref<string | null>(null)
const changePasswordRunning = ref(false)
const questions = ref<QuestionRecordDTO[]>([])
const knowledge = ref<KnowledgeRecordDTO[]>([])
const temporaryKnowledge = ref<KnowledgeRecordDTO[]>([])
const historyError = ref<string | null>(null)
const refreshing = ref(false)
const isLoggedIn = computed(() => Boolean(authUser.value?.authenticated))
const accountRoleText = computed(() => roleText(authUser.value?.role))
const canSubmitAuth = computed(() => {
  if (!authUsername.value || !authPassword.value) {
    return false
  }
  return authMode.value === 'login' || Boolean(authConfirmPassword.value)
})
const handleAuditUpdated = () => {
  if (isLoggedIn.value) {
    void refreshHistory()
  }
}
const handleAuthUpdated = () => {
  void refreshMe().then(() => {
    if (isLoggedIn.value) {
      return refreshHistory()
    }
    clearHistoryState()
    return undefined
  })
}

async function refreshMe() {
  try {
    authUser.value = await apiGet<AuthUserDTO>('/auth/me')
  } catch {
    authUser.value = null
  } finally {
    authReady.value = true
  }
}

async function submitAuth() {
  if (authMode.value === 'register' && authPassword.value !== authConfirmPassword.value) {
    authError.value = '两次输入的密码不一致。'
    return
  }
  authRunning.value = true
  authError.value = null
  authNotice.value = null
  try {
    const response = await apiPost<AuthResponse>(authMode.value === 'login' ? '/auth/login' : '/auth/register', {
      username: authUsername.value,
      password: authPassword.value,
    })
    setAuthToken(response.token)
    authUser.value = response.user
    authUsername.value = ''
    authPassword.value = ''
    authConfirmPassword.value = ''
    await refreshHistory()
  } catch (e) {
    authError.value = friendlyAuthError((e as Error).message ?? String(e))
  } finally {
    authRunning.value = false
  }
}

function setAuthMode(mode: 'login' | 'register') {
  authMode.value = mode
  authError.value = null
  authNotice.value = null
  authConfirmPassword.value = ''
}

function openPasswordDialog() {
  changeCurrentPassword.value = ''
  changeNewPassword.value = ''
  changeConfirmPassword.value = ''
  changePasswordError.value = null
  passwordDialogOpen.value = true
}

function closePasswordDialog() {
  if (changePasswordRunning.value) {
    return
  }
  passwordDialogOpen.value = false
}

async function submitPasswordChange() {
  changePasswordError.value = null
  if (!changeCurrentPassword.value || !changeNewPassword.value || !changeConfirmPassword.value) {
    changePasswordError.value = '请填写当前密码、新密码和确认密码。'
    return
  }
  if (changeNewPassword.value !== changeConfirmPassword.value) {
    changePasswordError.value = '两次输入的新密码不一致。'
    return
  }
  changePasswordRunning.value = true
  try {
    const username = authUser.value?.username ?? ''
    await apiPost('/auth/password/change', {
      currentPassword: changeCurrentPassword.value,
      newPassword: changeNewPassword.value,
    })
    passwordDialogOpen.value = false
    setAuthToken('')
    authUser.value = null
    authUsername.value = username
    authPassword.value = ''
    authConfirmPassword.value = ''
    authMode.value = 'login'
    authNotice.value = '密码已修改，请使用新密码重新登录。'
    clearHistoryState()
  } catch (e) {
    changePasswordError.value = friendlyAuthError((e as Error).message ?? String(e))
  } finally {
    changePasswordRunning.value = false
  }
}

function friendlyAuthError(message: string): string {
  if (message.includes('No static resource auth/')) {
    return '后端不是最新版本或未启动 auth 接口，请重启作品后端后再试。'
  }
  if (message.includes('Auth requires MYSQL_ENABLED=true')) {
    return '注册登录需要启用 MySQL，请用 MYSQL_ENABLED=true 启动后端。'
  }
  return message
}

async function logout() {
  try {
    await apiPost('/auth/logout', {})
  } catch {
    // Local token removal is enough for the demo fallback path.
  }
  setAuthToken('')
  await refreshMe()
  clearHistoryState()
}

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function refreshHistory() {
  if (!isLoggedIn.value) {
    clearHistoryState()
    return
  }
  refreshing.value = true
  historyError.value = null
  try {
    const [questionResponse, knowledgeResponse, temporaryKnowledgeResponse] = await Promise.all([
      apiGet<QuestionRecordDTO[]>('/demo/me/questions'),
      apiGet<KnowledgeRecordDTO[]>('/demo/me/knowledge'),
      apiGet<KnowledgeRecordDTO[]>('/demo/me/temporary-knowledge'),
    ])
    questions.value = questionResponse.slice(0, 8)
    knowledge.value = knowledgeResponse.slice(0, 6)
    temporaryKnowledge.value = temporaryKnowledgeResponse.slice(0, 6)
  } catch (e) {
    historyError.value = friendlyHistoryError((e as Error).message ?? String(e))
  } finally {
    refreshing.value = false
  }
}

function clearHistoryState() {
  questions.value = []
  knowledge.value = []
  temporaryKnowledge.value = []
  historyError.value = null
  refreshing.value = false
}

function friendlyHistoryError(message: string): string {
  if (
    message.includes('/demo/me/questions') ||
    message.includes('/demo/me/knowledge') ||
    message.includes('/demo/me/temporary-knowledge') ||
    message.includes('No static resource')
  ) {
    return '历史接口暂不可用，请重启后端后刷新。'
  }
  return message
}

function roleText(role: string | undefined): string {
  if (role === 'ADMIN') return '管理员'
  if (role === 'USER') return '普通用户'
  return role ?? '-'
}

async function clearTemporaryKnowledge() {
  refreshing.value = true
  historyError.value = null
  try {
    await apiDelete('/demo/me/temporary-knowledge')
    temporaryKnowledge.value = []
    await refreshHistory()
  } catch (e) {
    historyError.value = friendlyHistoryError((e as Error).message ?? String(e))
  } finally {
    refreshing.value = false
  }
}

async function clearQuestionHistory() {
  if (!questions.value.length || !window.confirm('确认清空当前用户的提问历史？')) {
    return
  }
  refreshing.value = true
  historyError.value = null
  try {
    await apiDelete('/demo/me/questions')
    questions.value = []
    await refreshHistory()
  } catch (e) {
    historyError.value = friendlyHistoryError((e as Error).message ?? String(e))
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  refreshMe().then(() => {
    if (isLoggedIn.value) {
      void refreshHistory()
    }
  })
  window.addEventListener('demo-audit-updated', handleAuditUpdated)
  window.addEventListener('auth-updated', handleAuthUpdated)
})

onUnmounted(() => {
  window.removeEventListener('demo-audit-updated', handleAuditUpdated)
  window.removeEventListener('auth-updated', handleAuthUpdated)
})
</script>

<template>
  <div v-if="!authReady" class="auth-page">
    <div class="auth-panel loading-panel">
      <div class="brand-title">Spring AI Chat Sys</div>
      <div class="muted small">正在检查登录状态...</div>
    </div>
  </div>

  <div v-else-if="!isLoggedIn" class="auth-page">
    <section class="auth-hero">
      <div class="auth-mark">AI</div>
      <h1>Spring AI Chat Sys</h1>
      <p>登录后进入 RAG 知识库问答工作台，查看个人历史、录入知识并进行问答检索。</p>
      <div class="auth-feature-grid">
        <span>个人历史隔离</span>
        <span>临时知识隔离</span>
        <span>知识库问答</span>
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-panel-head">
        <div>
          <h2>{{ authMode === 'login' ? '账号登录' : '创建账号' }}</h2>
          <p>{{ authMode === 'login' ? '使用已注册账号进入工作台。' : '注册后自动登录，默认角色为普通用户。' }}</p>
        </div>
      </div>

      <div class="segmented auth-entry-segmented">
        <button type="button" class="segment" :class="{ active: authMode === 'login' }" @click="setAuthMode('login')">
          登录
        </button>
        <button
          type="button"
          class="segment"
          :class="{ active: authMode === 'register' }"
          @click="setAuthMode('register')"
        >
          注册
        </button>
      </div>

      <div class="auth-entry-form">
        <label>
          <span>用户名</span>
          <input v-model="authUsername" autocomplete="username" placeholder="username" />
        </label>
        <label>
          <span>密码</span>
          <el-input
            v-model="authPassword"
            class="auth-password-input"
            :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
            placeholder="password"
            show-password
            @keyup.enter="submitAuth"
          />
        </label>
        <label v-if="authMode === 'register'">
          <span>确认密码</span>
          <el-input
            v-model="authConfirmPassword"
            class="auth-password-input"
            autocomplete="new-password"
            placeholder="confirm password"
            show-password
            @keyup.enter="submitAuth"
          />
        </label>
        <button class="auth-entry-submit" :disabled="authRunning || !canSubmitAuth" @click="submitAuth">
          {{ authRunning ? '处理中...' : authMode === 'login' ? '登录并进入' : '注册并进入' }}
        </button>
        <div v-if="authNotice" class="auth-entry-notice">{{ authNotice }}</div>
        <div v-if="authError" class="auth-entry-error">{{ authError }}</div>
      </div>
    </section>
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-title">Spring AI Chat Sys</div>
        <div class="brand-sub muted small">RAG 知识库问答工作台</div>
      </div>

      <div class="sidebar-account">
        <div>
          <div class="account-name">{{ authUser?.username }}</div>
          <div class="account-meta">{{ accountRoleText }} · {{ authUser?.userKey }}</div>
        </div>
        <div class="account-actions">
          <button class="ghost-btn" :disabled="authRunning" @click="openPasswordDialog">改密</button>
          <button class="ghost-btn" :disabled="authRunning" @click="logout">退出</button>
        </div>
      </div>

      <nav class="sidebar-nav">
        <RouterLink class="navlink" to="/chat">新对话</RouterLink>
        <RouterLink class="navlink" to="/ingest">知识录入</RouterLink>
        <RouterLink class="navlink" to="/history">历史总览</RouterLink>
        <RouterLink v-if="authUser?.role === 'ADMIN'" class="navlink" to="/admin">管理端</RouterLink>
        <RouterLink class="navlink" to="/status">运行状态</RouterLink>
      </nav>

      <div class="sidebar-section">
        <div class="sidebar-section-head">
          <span>历史对话</span>
          <div class="sidebar-head-actions">
            <button class="ghost-btn" :disabled="refreshing || !questions.length" @click="clearQuestionHistory">
              清空
            </button>
            <button class="ghost-btn" :disabled="refreshing" @click="refreshHistory">刷新</button>
          </div>
        </div>
        <div v-if="historyError" class="sidebar-error">{{ historyError }}</div>
        <div v-else-if="!questions.length" class="sidebar-empty">暂无提问记录</div>
        <RouterLink
          v-for="item in questions"
          :key="item.id"
          class="history-link"
          :to="{ path: '/chat', query: { q: item.question } }"
        >
          <span>{{ clip(item.question, 34) }}</span>
          <small>{{ formatTime(item.createdAt) }}</small>
        </RouterLink>
      </div>

      <div class="sidebar-section">
        <div class="sidebar-section-head">
          <span>我的知识库</span>
          <RouterLink class="ghost-link" to="/history">全部</RouterLink>
        </div>
        <div v-if="!knowledge.length" class="sidebar-empty">暂无录入记录</div>
        <RouterLink v-for="item in knowledge" :key="item.id" class="history-link" to="/history">
          <span>{{ clip(item.documentName || item.docId, 34) }}</span>
          <small>{{ item.vectorStoreMode }} · {{ item.chunkCount }} 片段</small>
        </RouterLink>
      </div>

      <div class="sidebar-section">
        <div class="sidebar-section-head">
          <span>临时知识</span>
          <button
            class="ghost-btn"
            :disabled="refreshing || !temporaryKnowledge.length"
            @click="clearTemporaryKnowledge"
          >
            一键清空
          </button>
        </div>
        <div v-if="!temporaryKnowledge.length" class="sidebar-empty">暂无临时知识</div>
        <RouterLink v-for="item in temporaryKnowledge" :key="item.id" class="history-link" to="/chat">
          <span>{{ clip(item.documentName || item.docId, 34) }}</span>
          <small>{{ item.chunkCount }} 片段 · 重启后消失</small>
        </RouterLink>
      </div>
    </aside>

    <section class="workspace">
      <header class="workspace-header">
        <div>
          <div class="workspace-title">{{ title }}</div>
          <div class="muted small">中间区域展示回答、引用来源、匹配片段和运行细节。</div>
        </div>
      </header>

      <main class="workspace-main">
        <RouterView />
      </main>
    </section>

    <div v-if="passwordDialogOpen" class="modal-backdrop" @click.self="closePasswordDialog">
      <section class="password-modal" role="dialog" aria-modal="true" aria-labelledby="change-password-title">
        <div class="modal-head">
          <div>
            <h2 id="change-password-title">修改密码</h2>
            <p>修改后将退出当前会话，需要用新密码重新登录。</p>
          </div>
          <button
            class="modal-close"
            type="button"
            :disabled="changePasswordRunning"
            aria-label="关闭"
            @click="closePasswordDialog"
          >
            ×
          </button>
        </div>

        <div class="modal-form">
          <label>
            <span>当前密码</span>
            <el-input
              v-model="changeCurrentPassword"
              class="auth-password-input"
              autocomplete="current-password"
              show-password
              @keyup.enter="submitPasswordChange"
            />
          </label>
          <label>
            <span>新密码</span>
            <el-input
              v-model="changeNewPassword"
              class="auth-password-input"
              autocomplete="new-password"
              show-password
              @keyup.enter="submitPasswordChange"
            />
          </label>
          <label>
            <span>确认新密码</span>
            <el-input
              v-model="changeConfirmPassword"
              class="auth-password-input"
              autocomplete="new-password"
              show-password
              @keyup.enter="submitPasswordChange"
            />
          </label>
          <button class="modal-submit" type="button" :disabled="changePasswordRunning" @click="submitPasswordChange">
            {{ changePasswordRunning ? '修改中...' : '确认修改' }}
          </button>
          <div v-if="changePasswordError" class="modal-error">{{ changePasswordError }}</div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: stretch;
  background: #f1f3f5;
}

.auth-hero {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
  padding: 48px clamp(40px, 8vw, 120px);
  border-right: 1px solid #e5e7eb;
  background:
    linear-gradient(90deg, rgba(241, 243, 245, 0.92), rgba(241, 243, 245, 0.76)),
    url('https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1800&q=80');
  background-position: center;
  background-size: cover;
  color: #0f172a;
}

.auth-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
  color: #2563eb;
  font-weight: 800;
}

.auth-hero h1 {
  max-width: 620px;
  margin: 0;
  font-size: 42px;
  line-height: 1.12;
}

.auth-hero p {
  max-width: 620px;
  margin: 0;
  color: #475569;
  font-size: 17px;
  line-height: 1.7;
}

.auth-feature-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.auth-feature-grid span {
  padding: 7px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  font-size: 12px;
}

.auth-panel {
  align-self: center;
  width: min(100% - 40px, 380px);
  margin: 0 auto;
  padding: 28px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  box-shadow: 0 18px 60px rgba(15, 23, 42, 0.1);
}

.loading-panel {
  justify-self: center;
}

.auth-panel-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
}

.auth-panel-head p {
  margin: 0 0 18px;
  color: #64748b;
}

.auth-entry-segmented {
  width: 100%;
  margin-bottom: 16px;
  border-radius: 8px;
  border-color: #cbd5e1;
  background: #f8fafc;
}

.auth-entry-segmented .segment {
  flex: 1;
  border-radius: 6px;
  color: #475569;
}

.auth-entry-segmented .segment.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.08);
}

.auth-entry-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.auth-entry-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #475569;
  font-size: 12px;
}

.auth-entry-form > label > input {
  width: 100%;
  height: 42px;
  padding: 9px 11px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  outline: none;
}

.auth-entry-form > label > input::placeholder {
  color: #64748b;
}

.auth-entry-form > label > input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.auth-password-input {
  width: 100%;
}

:deep(.auth-password-input .el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

:deep(.auth-password-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #94a3b8 inset;
}

:deep(.auth-password-input.is-focus .el-input__wrapper) {
  box-shadow:
    0 0 0 1px #2563eb inset,
    0 0 0 3px rgba(37, 99, 235, 0.12);
}

:deep(.auth-password-input .el-input__inner) {
  height: 42px;
  color: #0f172a;
}

:deep(.auth-password-input .el-input__inner::placeholder) {
  color: #64748b;
}

:deep(.auth-password-input .el-input__password) {
  color: #0f172a;
}

.auth-entry-submit {
  height: 42px;
  border: 1px solid #60a5fa;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.auth-entry-submit:disabled {
  border-color: #cbd5e1;
  background: #e2e8f0;
  color: #64748b;
  cursor: not-allowed;
}

.auth-entry-error {
  padding: 9px 10px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
}

.auth-entry-notice {
  padding: 9px 10px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  font-size: 12px;
}

.app-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  background: #f6f7f9;
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 12px;
  overflow: auto;
  border-right: 1px solid #e5e7eb;
  background: #f1f3f5;
}

.brand {
  padding: 4px 8px;
}

.brand-title {
  font-weight: 700;
}

.sidebar-account {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px;
  border-top: 1px solid #dbe3ee;
  border-bottom: 1px solid #dbe3ee;
}

.account-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.account-name {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.account-meta {
  max-width: 190px;
  color: #64748b;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: grayscale(0.65) blur(2px);
}

.password-modal {
  width: min(420px, 100%);
  padding: 20px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.26);
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.modal-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.modal-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.modal-close {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #475569;
  font-size: 12px;
}

.modal-submit {
  height: 40px;
  border: 1px solid #2563eb;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.modal-submit:disabled {
  border-color: #cbd5e1;
  background: #e2e8f0;
  color: #64748b;
  cursor: not-allowed;
}

.modal-error {
  padding: 9px 10px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.navlink {
  padding: 9px 10px;
  border-radius: 8px;
  text-decoration: none;
  color: #1f2937;
}

.navlink.router-link-active {
  background: #e5e7eb;
  font-weight: 600;
}

.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 8px 4px;
  color: #6b7280;
  font-size: 12px;
}

.sidebar-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.history-link {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  border-radius: 8px;
  text-decoration: none;
  color: #1f2937;
}

.history-link:hover,
.history-link.router-link-active {
  background: #e5e7eb;
}

.history-link small,
.sidebar-empty,
.sidebar-error {
  color: #6b7280;
  font-size: 12px;
}

.sidebar-error {
  color: #b91c1c;
  padding: 0 8px;
}

.sidebar-empty {
  padding: 8px;
}

.ghost-btn,
.ghost-link {
  border: 0;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 12px;
  text-decoration: none;
}

.workspace {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.workspace-header {
  position: sticky;
  top: 0;
  z-index: 5;
  padding: 18px 28px;
  border-bottom: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
}

.workspace-title {
  font-weight: 700;
}

.workspace-main {
  width: min(980px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0 40px;
}

@media (max-width: 820px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    height: auto;
    max-height: none;
  }

  .workspace-main {
    width: min(100% - 24px, 980px);
  }
}
</style>
