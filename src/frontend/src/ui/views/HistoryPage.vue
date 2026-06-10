<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { apiDelete, apiGet, apiPost, getDemoUserId } from '../../lib/api'
import type {
  AdminUserDTO,
  AdminUserDataDeleteResponse,
  AuthUserDTO,
  HistoryClearResponse,
  IngestResponse,
  KnowledgeRecordDTO,
  PageResponse,
  QuestionRecordDTO,
} from '../../lib/types'
import { clip, formatMs, safeString } from '../../lib/util'

const props = withDefaults(
  defineProps<{
    initialView?: 'me' | 'admin'
    lockedView?: boolean
  }>(),
  {
    initialView: 'me',
    lockedView: false,
  },
)

const ADMIN_USERS_PAGE_SIZE = 8
const ADMIN_DETAIL_PAGE_SIZE = 5
const PERSONAL_PAGE_SIZE = 8

const view = ref<'me' | 'admin'>(props.initialView)
const adminDataView = ref<'list' | 'detail'>('list')
const knowledge = ref<KnowledgeRecordDTO[]>([])
const questions = ref<QuestionRecordDTO[]>([])
const adminUsers = ref<AdminUserDTO[]>([])
const adminUsersPage = ref(1)
const selectedAdminUser = ref<AdminUserDTO | null>(null)
const selectedUserKnowledge = ref<KnowledgeRecordDTO[]>([])
const selectedUserQuestions = ref<QuestionRecordDTO[]>([])
const selectedUserTemporaryKnowledge = ref<KnowledgeRecordDTO[]>([])
const selectedUserKnowledgeTotal = ref(0)
const selectedUserQuestionsTotal = ref(0)
const selectedUserTemporaryKnowledgeTotal = ref(0)
const selectedUserKnowledgePage = ref(1)
const selectedUserQuestionsPage = ref(1)
const selectedUserTemporaryKnowledgePage = ref(1)
const detailEditMode = ref(false)
const detailSelectedKnowledgeIds = ref<string[]>([])
const detailSelectedQuestionIds = ref<string[]>([])
const detailSelectedTemporaryKnowledgeIds = ref<string[]>([])
const rolePermissions = ref<Record<string, string[]>>({})
const running = ref(false)
const adminRunning = ref<string | null>(null)
const detailRunning = ref(false)
const error = ref<string | null>(null)
const adminMessage = ref<string | null>(null)
const selectedKnowledgeIds = ref<string[]>([])
const selectedQuestionIds = ref<string[]>([])
const personalKnowledgePage = ref(1)
const personalQuestionsPage = ref(1)
const mergeResult = ref<IngestResponse | null>(null)
const authUser = ref<AuthUserDTO | null>(null)
const adminDetailSection = ref<HTMLElement | null>(null)
const roleDrafts = reactive<Record<string, string>>({})
const passwordDrafts = reactive<Record<string, string>>({})

const userId = computed(() => getDemoUserId())
const isAdmin = computed(() => view.value === 'admin')
const isAdminUser = computed(() => authUser.value?.role === 'ADMIN')
const selectedCount = computed(() => selectedQuestionIds.value.length)
const selectedKnowledgeCount = computed(() => selectedKnowledgeIds.value.length)
const currentUserLabel = computed(() =>
  authUser.value?.username ? `${authUser.value.username} (${authUser.value.userKey})` : userId.value,
)
const pageTitle = computed(() => {
  if (!isAdmin.value) return '个人历史 / 当前账号'
  return adminDataView.value === 'detail' ? '删除数据 / 记录详情' : '管理员总览'
})
const pageDescription = computed(() =>
  isAdmin.value
    ? '管理员可以查看账号与 RBAC 权限，并进入用户详情后清理展示历史和临时知识。'
    : '普通用户只能查看和清理自己的知识录入记录、提问历史与临时知识。',
)
const knowledgeTitle = computed(() => (isAdmin.value ? '全局知识录入历史' : '我的知识录入历史'))
const questionTitle = computed(() => (isAdmin.value ? '全局提问历史' : '我的提问历史'))
const knowledgeEmptyText = computed(() =>
  isAdmin.value
    ? '暂无全局知识录入记录。用户导入内置素材或自定义文本后，这里会显示全部录入历史。'
    : '暂无个人录入记录。先到“知识录入”页导入内置素材或自定义文本。',
)
const questionEmptyText = computed(() =>
  isAdmin.value
    ? '暂无全局提问历史。用户完成知识库问答、片段检索或直接问模型后，这里会显示全部提问记录。'
    : '暂无个人提问记录。先到“提问”页进行知识库问答、片段检索或直接问模型。',
)
const friendlyErrorText = computed(() => friendlyError(error.value))
const selectedUserTotalRecords = computed(
  () => selectedUserKnowledgeTotal.value + selectedUserQuestionsTotal.value + selectedUserTemporaryKnowledgeTotal.value,
)
const selectedDetailCount = computed(
  () =>
    detailSelectedKnowledgeIds.value.length +
    detailSelectedQuestionIds.value.length +
    detailSelectedTemporaryKnowledgeIds.value.length,
)
const adminUsersPageCount = computed(() => pageCount(adminUsers.value.length, ADMIN_USERS_PAGE_SIZE))
const adminUserPageItems = computed(() => paginate(adminUsers.value, adminUsersPage.value, ADMIN_USERS_PAGE_SIZE))
const personalKnowledgePageCount = computed(() => pageCount(knowledge.value.length, PERSONAL_PAGE_SIZE))
const personalQuestionsPageCount = computed(() => pageCount(questions.value.length, PERSONAL_PAGE_SIZE))
const personalKnowledgePageItems = computed(() =>
  paginate(knowledge.value, personalKnowledgePage.value, PERSONAL_PAGE_SIZE),
)
const personalQuestionsPageItems = computed(() =>
  paginate(questions.value, personalQuestionsPage.value, PERSONAL_PAGE_SIZE),
)
const selectedUserKnowledgePageCount = computed(() =>
  pageCount(selectedUserKnowledgeTotal.value, ADMIN_DETAIL_PAGE_SIZE),
)
const selectedUserQuestionsPageCount = computed(() =>
  pageCount(selectedUserQuestionsTotal.value, ADMIN_DETAIL_PAGE_SIZE),
)
const selectedUserTemporaryKnowledgePageCount = computed(() =>
  pageCount(selectedUserTemporaryKnowledgeTotal.value, ADMIN_DETAIL_PAGE_SIZE),
)

const handleAuthUpdated = () => {
  void refresh()
}

function pageCount(total: number, size: number): number {
  return total <= 0 ? 0 : Math.ceil(total / size)
}

function paginate<T>(items: T[], page: number, size: number): T[] {
  const safePage = Math.max(1, page)
  const start = (safePage - 1) * size
  return items.slice(start, start + size)
}

function clampPage(page: number, total: number, size: number): number {
  const count = pageCount(total, size)
  return count ? Math.min(Math.max(1, page), count) : 1
}

function pageRangeText(page: number, total: number, size: number): string {
  if (!total) return '0 / 0'
  return `${clampPage(page, total, size)} / ${pageCount(total, size)}`
}

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function requestTypeLabel(type: string): string {
  if (type === 'rag-chat') return '知识库问答'
  if (type === 'search') return '片段检索'
  if (type === 'direct-ask') return '直接问模型'
  return type
}

function roleText(role: string): string {
  if (role === 'ADMIN') return '管理员'
  if (role === 'USER') return '普通用户'
  return role
}

function permissionText(permission: string): string {
  const labels: Record<string, string> = {
    VIEW_OWN_HISTORY: '查看个人历史',
    CLEAR_OWN_HISTORY: '清理个人历史',
    VIEW_ADMIN_HISTORY: '查看全局历史',
    MANAGE_USERS: '查看用户列表',
    ASSIGN_ROLES: '分配角色',
    RESET_USER_PASSWORD: '重置他人密码',
    DELETE_USER_DATA: '删除他人数据',
  }
  return labels[permission] ?? permission
}

function friendlyError(message: string | null): string {
  if (!message) return ''
  if (
    message.includes('Admin role required') ||
    message.includes('Permission required') ||
    message.includes('FORBIDDEN') ||
    message.includes('HTTP 403')
  ) {
    return '当前账号没有管理员权限，不能执行该操作。'
  }
  if (message.includes('/demo/admin') || message.includes('/demo/me') || message.includes('No static resource')) {
    return '历史接口暂不可用，请确认后端已启动并刷新页面。'
  }
  return message
}

async function refresh() {
  running.value = true
  error.value = null
  try {
    authUser.value = await apiGet<AuthUserDTO>('/auth/me')
    if (!isAdminUser.value && view.value === 'admin' && !props.lockedView) {
      view.value = 'me'
      adminDataView.value = 'list'
      closeUserDataDetail()
    }
    if (!isAdminUser.value && view.value === 'admin') {
      throw new Error('Admin role required.')
    }
    if (isAdmin.value) {
      await refreshAdminUsers()
      if (selectedAdminUser.value) {
        const matched = adminUsers.value.find((user) => user.userKey === selectedAdminUser.value?.userKey)
        selectedAdminUser.value = matched ?? selectedAdminUser.value
        if (adminDataView.value === 'detail') {
          await refreshUserDataDetail(selectedAdminUser.value)
        }
      }
      return
    }
    const [knowledgeResponse, questionResponse] = await Promise.all([
      apiGet<KnowledgeRecordDTO[]>('/demo/me/knowledge'),
      apiGet<QuestionRecordDTO[]>('/demo/me/questions'),
    ])
    knowledge.value = knowledgeResponse
    questions.value = questionResponse
    personalKnowledgePage.value = clampPage(personalKnowledgePage.value, knowledgeResponse.length, PERSONAL_PAGE_SIZE)
    personalQuestionsPage.value = clampPage(personalQuestionsPage.value, questionResponse.length, PERSONAL_PAGE_SIZE)
    selectedKnowledgeIds.value = selectedKnowledgeIds.value.filter((id) =>
      knowledgeResponse.some((item) => item.id === id),
    )
    selectedQuestionIds.value = selectedQuestionIds.value.filter((id) =>
      questionResponse.some((item) => item.id === id),
    )
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function refreshAdminUsers() {
  if (!isAdminUser.value) return
  const [users, permissions] = await Promise.all([
    apiGet<AdminUserDTO[]>('/auth/admin/users'),
    apiGet<Record<string, string[]>>('/auth/admin/permissions'),
  ])
  adminUsers.value = users
  adminUsersPage.value = clampPage(adminUsersPage.value, users.length, ADMIN_USERS_PAGE_SIZE)
  rolePermissions.value = permissions
  for (const user of users) {
    roleDrafts[user.username] = user.role
  }
}

function switchView(next: 'me' | 'admin') {
  if (next === 'admin' && !isAdminUser.value) {
    return
  }
  view.value = next
  adminDataView.value = 'list'
  selectedKnowledgeIds.value = []
  selectedQuestionIds.value = []
  personalKnowledgePage.value = 1
  personalQuestionsPage.value = 1
  mergeResult.value = null
  adminMessage.value = null
  selectedAdminUser.value = null
  void refresh()
}

function toggleAllQuestions(checked: boolean) {
  const pageIds = personalQuestionsPageItems.value.map((item) => item.id)
  if (checked) {
    selectedQuestionIds.value = Array.from(new Set([...selectedQuestionIds.value, ...pageIds]))
    return
  }
  selectedQuestionIds.value = selectedQuestionIds.value.filter((id) => !pageIds.includes(id))
}

function onToggleAllQuestions(event: Event) {
  toggleAllQuestions((event.target as HTMLInputElement).checked)
}

function toggleAllKnowledge(checked: boolean) {
  const pageIds = personalKnowledgePageItems.value.map((item) => item.id)
  if (checked) {
    selectedKnowledgeIds.value = Array.from(new Set([...selectedKnowledgeIds.value, ...pageIds]))
    return
  }
  selectedKnowledgeIds.value = selectedKnowledgeIds.value.filter((id) => !pageIds.includes(id))
}

function onToggleAllKnowledge(event: Event) {
  toggleAllKnowledge((event.target as HTMLInputElement).checked)
}

function setPersonalKnowledgePage(page: number) {
  personalKnowledgePage.value = clampPage(page, knowledge.value.length, PERSONAL_PAGE_SIZE)
}

function setPersonalQuestionsPage(page: number) {
  personalQuestionsPage.value = clampPage(page, questions.value.length, PERSONAL_PAGE_SIZE)
}

function setAdminUsersPage(page: number) {
  adminUsersPage.value = clampPage(page, adminUsers.value.length, ADMIN_USERS_PAGE_SIZE)
}

async function clearMyQuestions() {
  if (isAdmin.value || !window.confirm('确认清空当前用户的提问历史？此操作不删除知识库内容。')) {
    return
  }
  running.value = true
  error.value = null
  mergeResult.value = null
  try {
    await apiDelete<HistoryClearResponse>('/demo/me/questions')
    selectedQuestionIds.value = []
    await refresh()
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function clearMyKnowledgeRecords() {
  if (isAdmin.value || !window.confirm('确认清空当前用户的录入记录？此操作只清理展示历史，不删除 Milvus 持久知识。')) {
    return
  }
  running.value = true
  error.value = null
  try {
    await apiDelete<HistoryClearResponse>('/demo/me/knowledge-records')
    await refresh()
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function clearSelectedMyKnowledgeRecords() {
  if (isAdmin.value || !selectedKnowledgeIds.value.length) {
    return
  }
  if (
    !window.confirm(`确认删除选中的 ${selectedKnowledgeIds.value.length} 条知识记录？对应向量也会从当前知识库中移除。`)
  ) {
    return
  }
  running.value = true
  error.value = null
  try {
    await apiPost<HistoryClearResponse>('/demo/me/knowledge-records/delete-selected', {
      ids: selectedKnowledgeIds.value,
    })
    selectedKnowledgeIds.value = []
    await refresh()
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function mergeSelectedToTemporaryKnowledge() {
  if (isAdmin.value || !selectedQuestionIds.value.length) {
    return
  }
  running.value = true
  error.value = null
  mergeResult.value = null
  try {
    mergeResult.value = await apiPost<IngestResponse>('/demo/me/questions/merge-temporary', {
      questionIds: selectedQuestionIds.value,
      documentName: `合并的提问历史（${selectedQuestionIds.value.length} 条）`,
      source: '提问历史合并',
    })
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

async function updateRole(user: AdminUserDTO) {
  const nextRole = roleDrafts[user.username] ?? user.role
  if (nextRole === user.role) return
  adminRunning.value = `role:${user.username}`
  error.value = null
  adminMessage.value = null
  try {
    await apiPost<AdminUserDTO>(`/auth/admin/users/${encodeURIComponent(user.username)}/role`, { role: nextRole })
    adminMessage.value = `已将 ${user.username} 调整为${roleText(nextRole)}。`
    await refresh()
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    adminRunning.value = null
  }
}

async function resetPassword(user: AdminUserDTO) {
  const newPassword = (passwordDrafts[user.username] ?? '').trim()
  if (!newPassword) {
    error.value = '请输入新密码。'
    return
  }
  if (!window.confirm(`确认重置 ${user.username} 的密码？该用户现有会话会失效。`)) {
    return
  }
  adminRunning.value = `password:${user.username}`
  error.value = null
  adminMessage.value = null
  try {
    const result = await apiPost<{ reset: boolean; revokedSessions: number }>(
      `/auth/admin/users/${encodeURIComponent(user.username)}/password/reset`,
      { newPassword },
    )
    passwordDrafts[user.username] = ''
    adminMessage.value = `已重置 ${user.username} 的密码，并撤销 ${result.revokedSessions} 个会话。`
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    adminRunning.value = null
  }
}

type AdminDataScope = 'knowledge-records' | 'questions' | 'temporary-knowledge' | 'data'
type DetailRecordScope = 'knowledge-records' | 'questions' | 'temporary-knowledge'

async function openUserDataDetail(user: AdminUserDTO) {
  selectedAdminUser.value = user
  selectedUserKnowledge.value = []
  selectedUserQuestions.value = []
  selectedUserTemporaryKnowledge.value = []
  selectedUserKnowledgeTotal.value = 0
  selectedUserQuestionsTotal.value = 0
  selectedUserTemporaryKnowledgeTotal.value = 0
  selectedUserKnowledgePage.value = 1
  selectedUserQuestionsPage.value = 1
  selectedUserTemporaryKnowledgePage.value = 1
  detailEditMode.value = false
  clearDetailSelection()
  adminDataView.value = 'detail'
  detailRunning.value = true
  adminMessage.value = null
  error.value = null
  await nextTick()
  adminDetailSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  await refreshUserDataDetail(user)
}

async function refreshUserDataDetail(user = selectedAdminUser.value) {
  if (!user) return
  detailRunning.value = true
  error.value = null
  try {
    const userKey = encodeURIComponent(user.userKey)
    const [knowledgeRecords, questionRecords, temporaryRecords] = await Promise.all([
      apiGet<PageResponse<KnowledgeRecordDTO>>(
        `/demo/admin/users/${userKey}/knowledge/page?page=${selectedUserKnowledgePage.value - 1}&size=${ADMIN_DETAIL_PAGE_SIZE}`,
      ),
      apiGet<PageResponse<QuestionRecordDTO>>(
        `/demo/admin/users/${userKey}/questions/page?page=${selectedUserQuestionsPage.value - 1}&size=${ADMIN_DETAIL_PAGE_SIZE}`,
      ),
      apiGet<PageResponse<KnowledgeRecordDTO>>(
        `/demo/admin/users/${userKey}/temporary-knowledge/page?page=${selectedUserTemporaryKnowledgePage.value - 1}&size=${ADMIN_DETAIL_PAGE_SIZE}`,
      ),
    ])
    const nextKnowledgePage = clampPage(selectedUserKnowledgePage.value, knowledgeRecords.total, ADMIN_DETAIL_PAGE_SIZE)
    const nextQuestionsPage = clampPage(selectedUserQuestionsPage.value, questionRecords.total, ADMIN_DETAIL_PAGE_SIZE)
    const nextTemporaryPage = clampPage(
      selectedUserTemporaryKnowledgePage.value,
      temporaryRecords.total,
      ADMIN_DETAIL_PAGE_SIZE,
    )
    if (
      nextKnowledgePage !== selectedUserKnowledgePage.value ||
      nextQuestionsPage !== selectedUserQuestionsPage.value ||
      nextTemporaryPage !== selectedUserTemporaryKnowledgePage.value
    ) {
      selectedUserKnowledgePage.value = nextKnowledgePage
      selectedUserQuestionsPage.value = nextQuestionsPage
      selectedUserTemporaryKnowledgePage.value = nextTemporaryPage
      await refreshUserDataDetail(user)
      return
    }
    selectedUserKnowledge.value = knowledgeRecords.items
    selectedUserQuestions.value = questionRecords.items
    selectedUserTemporaryKnowledge.value = temporaryRecords.items
    selectedUserKnowledgeTotal.value = knowledgeRecords.total
    selectedUserQuestionsTotal.value = questionRecords.total
    selectedUserTemporaryKnowledgeTotal.value = temporaryRecords.total
    detailSelectedKnowledgeIds.value = detailSelectedKnowledgeIds.value.filter((id) =>
      selectedUserKnowledge.value.some((item) => item.id === id),
    )
    detailSelectedQuestionIds.value = detailSelectedQuestionIds.value.filter((id) =>
      selectedUserQuestions.value.some((item) => item.id === id),
    )
    detailSelectedTemporaryKnowledgeIds.value = detailSelectedTemporaryKnowledgeIds.value.filter((id) =>
      selectedUserTemporaryKnowledge.value.some((item) => item.id === id),
    )
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    detailRunning.value = false
  }
}

function closeUserDataDetail() {
  adminDataView.value = 'list'
  selectedAdminUser.value = null
  selectedUserKnowledge.value = []
  selectedUserQuestions.value = []
  selectedUserTemporaryKnowledge.value = []
  selectedUserKnowledgeTotal.value = 0
  selectedUserQuestionsTotal.value = 0
  selectedUserTemporaryKnowledgeTotal.value = 0
  detailEditMode.value = false
  clearDetailSelection()
}

function setDetailPage(scope: DetailRecordScope, page: number) {
  if (scope === 'knowledge-records') {
    selectedUserKnowledgePage.value = clampPage(page, selectedUserKnowledgeTotal.value, ADMIN_DETAIL_PAGE_SIZE)
  } else if (scope === 'questions') {
    selectedUserQuestionsPage.value = clampPage(page, selectedUserQuestionsTotal.value, ADMIN_DETAIL_PAGE_SIZE)
  } else {
    selectedUserTemporaryKnowledgePage.value = clampPage(
      page,
      selectedUserTemporaryKnowledgeTotal.value,
      ADMIN_DETAIL_PAGE_SIZE,
    )
  }
  void refreshUserDataDetail()
}

function clearDetailSelection() {
  detailSelectedKnowledgeIds.value = []
  detailSelectedQuestionIds.value = []
  detailSelectedTemporaryKnowledgeIds.value = []
}

function toggleDetailEditMode() {
  detailEditMode.value = !detailEditMode.value
  if (!detailEditMode.value) {
    clearDetailSelection()
  }
}

function pageSelectionChecked(records: { id: string }[], ids: string[]): boolean {
  return Boolean(records.length) && records.every((item) => ids.includes(item.id))
}

function toggleDetailPageSelection(scope: DetailRecordScope, checked: boolean) {
  const records =
    scope === 'questions'
      ? selectedUserQuestions.value
      : scope === 'knowledge-records'
        ? selectedUserKnowledge.value
        : selectedUserTemporaryKnowledge.value
  const pageIds = records.map((item) => item.id)
  const target =
    scope === 'questions'
      ? detailSelectedQuestionIds
      : scope === 'knowledge-records'
        ? detailSelectedKnowledgeIds
        : detailSelectedTemporaryKnowledgeIds
  if (checked) {
    target.value = Array.from(new Set([...target.value, ...pageIds]))
    return
  }
  target.value = target.value.filter((id) => !pageIds.includes(id))
}

async function clearUserData(user: AdminUserDTO, scope: AdminDataScope) {
  const labels: Record<AdminDataScope, string> = {
    'knowledge-records': '知识录入记录',
    questions: '提问历史',
    'temporary-knowledge': '临时知识',
    data: '全部可清理数据',
  }
  if (!selectedAdminUser.value || selectedAdminUser.value.userKey !== user.userKey) {
    await openUserDataDetail(user)
    return
  }
  if (!window.confirm(`确认删除 ${user.username} 的${labels[scope]}？请确认你已经检查过详情中的相关记录。`)) {
    return
  }
  adminRunning.value = `${scope}:${user.username}`
  error.value = null
  adminMessage.value = null
  try {
    const result = await apiDelete<AdminUserDataDeleteResponse>(
      `/demo/admin/users/${encodeURIComponent(user.userKey)}/${scope}`,
    )
    adminMessage.value = `已清理 ${result.userKey}：知识记录 ${result.clearedKnowledgeRecords} 条，提问历史 ${result.clearedQuestionRecords} 条，临时知识片段 ${result.clearedTemporaryChunks} 个。`
    await refreshAdminUsers()
    await refreshUserDataDetail(user)
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    adminRunning.value = null
  }
}

async function clearAllSelectedUserData() {
  const user = selectedAdminUser.value
  if (!user || !selectedDetailCount.value) return
  if (!window.confirm(`确认删除 ${user.username} 已勾选的 ${selectedDetailCount.value} 条记录？`)) {
    return
  }
  adminRunning.value = `selected:data:${user.username}`
  error.value = null
  adminMessage.value = null
  try {
    const [knowledgeResult, questionResult, temporaryResult] = await Promise.all([
      detailSelectedKnowledgeIds.value.length
        ? apiPost<AdminUserDataDeleteResponse>(
            `/demo/admin/users/${encodeURIComponent(user.userKey)}/knowledge-records/delete-selected`,
            { ids: detailSelectedKnowledgeIds.value },
          )
        : Promise.resolve<AdminUserDataDeleteResponse>({
            userKey: user.userKey,
            clearedKnowledgeRecords: 0,
            clearedQuestionRecords: 0,
            clearedTemporaryChunks: 0,
          }),
      detailSelectedQuestionIds.value.length
        ? apiPost<AdminUserDataDeleteResponse>(
            `/demo/admin/users/${encodeURIComponent(user.userKey)}/questions/delete-selected`,
            { ids: detailSelectedQuestionIds.value },
          )
        : Promise.resolve<AdminUserDataDeleteResponse>({
            userKey: user.userKey,
            clearedKnowledgeRecords: 0,
            clearedQuestionRecords: 0,
            clearedTemporaryChunks: 0,
          }),
      detailSelectedTemporaryKnowledgeIds.value.length
        ? apiPost<AdminUserDataDeleteResponse>(
            `/demo/admin/users/${encodeURIComponent(user.userKey)}/temporary-knowledge/delete-selected`,
            { ids: detailSelectedTemporaryKnowledgeIds.value },
          )
        : Promise.resolve<AdminUserDataDeleteResponse>({
            userKey: user.userKey,
            clearedKnowledgeRecords: 0,
            clearedQuestionRecords: 0,
            clearedTemporaryChunks: 0,
          }),
    ])
    const clearedKnowledge =
      knowledgeResult.clearedKnowledgeRecords +
      questionResult.clearedKnowledgeRecords +
      temporaryResult.clearedKnowledgeRecords
    const clearedQuestions =
      knowledgeResult.clearedQuestionRecords +
      questionResult.clearedQuestionRecords +
      temporaryResult.clearedQuestionRecords
    const clearedTemporary =
      knowledgeResult.clearedTemporaryChunks +
      questionResult.clearedTemporaryChunks +
      temporaryResult.clearedTemporaryChunks
    adminMessage.value = `已清理 ${user.userKey}：知识记录 ${clearedKnowledge} 条，提问历史 ${clearedQuestions} 条，临时知识片段 ${clearedTemporary} 个。`
    clearDetailSelection()
    await refreshAdminUsers()
    await refreshUserDataDetail(user)
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    adminRunning.value = null
  }
}

onMounted(() => {
  void refresh()
  window.addEventListener('auth-updated', handleAuthUpdated)
})

onUnmounted(() => {
  window.removeEventListener('auth-updated', handleAuthUpdated)
})
</script>

<template>
  <div class="stack">
    <section class="card">
      <div class="actions" style="justify-content: space-between">
        <div>
          <h2 style="margin: 0 0 6px">{{ pageTitle }}</h2>
          <div class="muted small">
            {{ pageDescription }}
          </div>
          <div class="muted small" style="margin-top: 4px">
            当前账号：<span class="mono">{{ currentUserLabel }}</span>
          </div>
        </div>
        <div class="actions">
          <button v-if="!props.lockedView" class="btn" :class="{ primary: view === 'me' }" @click="switchView('me')">
            个人历史
          </button>
          <button
            v-if="!props.lockedView && isAdminUser"
            class="btn"
            :class="{ primary: view === 'admin' }"
            @click="switchView('admin')"
          >
            管理员总览
          </button>
          <button class="btn" :disabled="running" @click="refresh">{{ running ? '刷新中...' : '刷新' }}</button>
        </div>
      </div>

      <div v-if="isAdmin" class="empty-state" style="margin-top: 10px">
        <div>
          管理员能力已接入后端权限校验：用户管理、角色分配、重置他人密码。删除数据需要先进入用户详情查看记录，再选择清理范围。
        </div>
        <div class="muted small" style="margin-top: 4px">
          持久向量内容当前仍按 Milvus 全局集合维护，本页清理的是演示历史、MySQL 镜像记录和临时知识。
        </div>
      </div>

      <div v-if="error" class="danger" style="margin-top: 10px">{{ friendlyErrorText }}</div>
      <div v-if="adminMessage" class="empty-state" style="margin-top: 10px">{{ adminMessage }}</div>
    </section>

    <section v-if="isAdmin && adminDataView === 'list'" class="card">
      <div class="actions" style="justify-content: space-between">
        <h3 style="margin: 0">用户与 RBAC 权限</h3>
        <div class="pill">{{ adminUsers.length }} 个账号</div>
      </div>
      <div class="hr"></div>

      <div class="actions" style="align-items: flex-start; margin-bottom: 12px">
        <div v-for="(permissions, role) in rolePermissions" :key="role" class="empty-state" style="flex: 1 1 240px">
          <div>
            <strong>{{ roleText(role) }}</strong>
          </div>
          <div class="muted small">{{ permissions.map(permissionText).join('，') }}</div>
        </div>
      </div>

      <div v-if="!adminUsers.length" class="empty-state">暂无可管理账号。</div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>账号</th>
            <th class="nowrap">角色</th>
            <th>重置密码</th>
            <th>数据详情</th>
            <th class="nowrap">更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in adminUserPageItems" :key="user.userKey">
            <td>
              <div>{{ user.username }}</div>
              <div class="muted small mono">{{ user.userKey }}</div>
            </td>
            <td class="nowrap">
              <div class="actions">
                <select v-model="roleDrafts[user.username]" :disabled="user.userKey === authUser?.userKey">
                  <option value="USER">普通用户</option>
                  <option value="ADMIN">管理员</option>
                </select>
                <button
                  class="btn compact"
                  :disabled="
                    user.userKey === authUser?.userKey ||
                    adminRunning === `role:${user.username}` ||
                    roleDrafts[user.username] === user.role
                  "
                  @click="updateRole(user)"
                >
                  保存
                </button>
              </div>
            </td>
            <td>
              <div class="actions">
                <input
                  v-model="passwordDrafts[user.username]"
                  type="password"
                  placeholder="新密码"
                  :disabled="user.userKey === authUser?.userKey"
                />
                <button
                  class="btn compact"
                  :disabled="user.userKey === authUser?.userKey || adminRunning === `password:${user.username}`"
                  @click="resetPassword(user)"
                >
                  重置
                </button>
              </div>
            </td>
            <td>
              <div class="actions">
                <button class="btn compact" :disabled="detailRunning" @click="openUserDataDetail(user)">
                  查看记录
                </button>
              </div>
            </td>
            <td class="muted small nowrap">{{ formatTime(user.updatedAt || user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div
        v-if="adminUsers.length > ADMIN_USERS_PAGE_SIZE"
        class="actions"
        style="justify-content: flex-end; margin-top: 12px"
      >
        <button class="btn compact" :disabled="adminUsersPage <= 1" @click="setAdminUsersPage(adminUsersPage - 1)">
          上一页
        </button>
        <div class="muted small">
          第 {{ pageRangeText(adminUsersPage, adminUsers.length, ADMIN_USERS_PAGE_SIZE) }} 页，共
          {{ adminUsers.length }} 个账号
        </div>
        <button
          class="btn compact"
          :disabled="adminUsersPage >= adminUsersPageCount"
          @click="setAdminUsersPage(adminUsersPage + 1)"
        >
          下一页
        </button>
      </div>
    </section>

    <section v-if="isAdmin && adminDataView === 'detail' && selectedAdminUser" ref="adminDetailSection" class="card">
      <div class="actions" style="justify-content: space-between">
        <div>
          <h3 style="margin: 0">删除数据 / 记录详情</h3>
          <div class="muted small" style="margin-top: 4px">
            账号：<span class="mono">{{ selectedAdminUser.username }}</span> /
            <span class="mono">{{ selectedAdminUser.userKey }}</span>
          </div>
        </div>
        <div class="actions">
          <button class="btn compact" :disabled="detailRunning" @click="refreshUserDataDetail()">
            {{ detailRunning ? '加载中...' : '刷新详情' }}
          </button>
          <button
            class="btn compact"
            :class="{ primary: detailEditMode }"
            :disabled="detailRunning"
            @click="toggleDetailEditMode"
          >
            {{ detailEditMode ? '退出编辑' : '编辑' }}
          </button>
          <button class="btn compact" @click="closeUserDataDetail">返回用户列表</button>
        </div>
      </div>
      <div class="hr"></div>

      <div class="actions" style="margin-bottom: 12px">
        <div class="pill">提问 {{ selectedUserQuestionsTotal }} 条</div>
        <div class="pill">录入 {{ selectedUserKnowledgeTotal }} 条</div>
        <div class="pill">临时 {{ selectedUserTemporaryKnowledgeTotal }} 条</div>
        <div class="pill">合计 {{ selectedUserTotalRecords }} 条</div>
        <div v-if="detailEditMode" class="pill">已选 {{ selectedDetailCount }} 条</div>
      </div>

      <div v-if="detailRunning" class="empty-state">正在加载该用户记录...</div>
      <div v-else-if="!selectedUserTotalRecords" class="empty-state">该用户当前没有可清理的展示记录或临时知识。</div>

      <div v-else class="stack">
        <div>
          <div class="actions" style="justify-content: space-between; margin-bottom: 8px">
            <h4 style="margin: 0">提问历史</h4>
            <div class="pill">{{ selectedUserQuestionsTotal }} 条</div>
          </div>
          <table v-if="selectedUserQuestions.length" class="table">
            <thead>
              <tr>
                <th v-if="detailEditMode" class="nowrap">
                  <input
                    type="checkbox"
                    :checked="pageSelectionChecked(selectedUserQuestions, detailSelectedQuestionIds)"
                    @change="toggleDetailPageSelection('questions', ($event.target as HTMLInputElement).checked)"
                  />
                </th>
                <th>问题</th>
                <th class="nowrap">类型</th>
                <th>回答预览 / 引用</th>
                <th class="nowrap">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedUserQuestions" :key="item.id">
                <td v-if="detailEditMode" class="nowrap">
                  <input v-model="detailSelectedQuestionIds" type="checkbox" :value="item.id" />
                </td>
                <td>{{ item.question }}</td>
                <td class="nowrap">{{ requestTypeLabel(item.requestType) }}</td>
                <td class="muted small">
                  <div v-if="item.answerPreview">{{ clip(item.answerPreview, 100) }}</div>
                  <div v-if="item.citationChunkIds.length" class="mono">{{ item.citationChunkIds.join(', ') }}</div>
                  <div v-if="!item.answerPreview && !item.citationChunkIds.length">-</div>
                </td>
                <td class="muted small nowrap">{{ formatTime(item.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">暂无提问历史。</div>
          <div v-if="selectedUserQuestionsTotal > 0" class="actions" style="justify-content: flex-end; margin-top: 8px">
            <button
              class="btn compact"
              :disabled="selectedUserQuestionsPage <= 1 || detailRunning"
              @click="setDetailPage('questions', selectedUserQuestionsPage - 1)"
            >
              上一页
            </button>
            <div class="muted small">
              第
              {{ pageRangeText(selectedUserQuestionsPage, selectedUserQuestionsTotal, ADMIN_DETAIL_PAGE_SIZE) }} 页，共
              {{ selectedUserQuestionsTotal }} 条
            </div>
            <button
              class="btn compact"
              :disabled="selectedUserQuestionsPage >= selectedUserQuestionsPageCount || detailRunning"
              @click="setDetailPage('questions', selectedUserQuestionsPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>

        <div>
          <div class="actions" style="justify-content: space-between; margin-bottom: 8px">
            <h4 style="margin: 0">录入记录</h4>
            <div class="pill">{{ selectedUserKnowledgeTotal }} 条</div>
          </div>
          <table v-if="selectedUserKnowledge.length" class="table">
            <thead>
              <tr>
                <th v-if="detailEditMode" class="nowrap">
                  <input
                    type="checkbox"
                    :checked="pageSelectionChecked(selectedUserKnowledge, detailSelectedKnowledgeIds)"
                    @change="
                      toggleDetailPageSelection('knowledge-records', ($event.target as HTMLInputElement).checked)
                    "
                  />
                </th>
                <th>文档</th>
                <th class="nowrap">片段</th>
                <th>内容预览</th>
                <th class="nowrap">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedUserKnowledge" :key="item.id">
                <td v-if="detailEditMode" class="nowrap">
                  <input v-model="detailSelectedKnowledgeIds" type="checkbox" :value="item.id" />
                </td>
                <td>
                  <div>{{ item.documentName }}</div>
                  <div class="muted small mono">{{ item.docId }} / {{ item.source }}</div>
                </td>
                <td class="mono nowrap">{{ item.chunkCount }}</td>
                <td class="muted small">{{ clip(item.contentPreview, 100) }}</td>
                <td class="muted small nowrap">{{ formatTime(item.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">暂无录入记录。</div>
          <div v-if="selectedUserKnowledgeTotal > 0" class="actions" style="justify-content: flex-end; margin-top: 8px">
            <button
              class="btn compact"
              :disabled="selectedUserKnowledgePage <= 1 || detailRunning"
              @click="setDetailPage('knowledge-records', selectedUserKnowledgePage - 1)"
            >
              上一页
            </button>
            <div class="muted small">
              第
              {{ pageRangeText(selectedUserKnowledgePage, selectedUserKnowledgeTotal, ADMIN_DETAIL_PAGE_SIZE) }} 页，共
              {{ selectedUserKnowledgeTotal }} 条
            </div>
            <button
              class="btn compact"
              :disabled="selectedUserKnowledgePage >= selectedUserKnowledgePageCount || detailRunning"
              @click="setDetailPage('knowledge-records', selectedUserKnowledgePage + 1)"
            >
              下一页
            </button>
          </div>
        </div>

        <div>
          <div class="actions" style="justify-content: space-between; margin-bottom: 8px">
            <h4 style="margin: 0">临时知识</h4>
            <div class="pill">{{ selectedUserTemporaryKnowledgeTotal }} 条</div>
          </div>
          <table v-if="selectedUserTemporaryKnowledge.length" class="table">
            <thead>
              <tr>
                <th v-if="detailEditMode" class="nowrap">
                  <input
                    type="checkbox"
                    :checked="pageSelectionChecked(selectedUserTemporaryKnowledge, detailSelectedTemporaryKnowledgeIds)"
                    @change="
                      toggleDetailPageSelection('temporary-knowledge', ($event.target as HTMLInputElement).checked)
                    "
                  />
                </th>
                <th>文档</th>
                <th class="nowrap">片段</th>
                <th>内容预览</th>
                <th class="nowrap">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedUserTemporaryKnowledge" :key="item.id">
                <td v-if="detailEditMode" class="nowrap">
                  <input v-model="detailSelectedTemporaryKnowledgeIds" type="checkbox" :value="item.id" />
                </td>
                <td>
                  <div>{{ item.documentName }}</div>
                  <div class="muted small mono">{{ item.docId }} / {{ item.source }}</div>
                </td>
                <td class="mono nowrap">{{ item.chunkCount }}</td>
                <td class="muted small">{{ clip(item.contentPreview, 100) }}</td>
                <td class="muted small nowrap">{{ formatTime(item.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">暂无临时知识。</div>
          <div
            v-if="selectedUserTemporaryKnowledgeTotal > 0"
            class="actions"
            style="justify-content: flex-end; margin-top: 8px"
          >
            <button
              class="btn compact"
              :disabled="selectedUserTemporaryKnowledgePage <= 1 || detailRunning"
              @click="setDetailPage('temporary-knowledge', selectedUserTemporaryKnowledgePage - 1)"
            >
              上一页
            </button>
            <div class="muted small">
              第
              {{
                pageRangeText(
                  selectedUserTemporaryKnowledgePage,
                  selectedUserTemporaryKnowledgeTotal,
                  ADMIN_DETAIL_PAGE_SIZE,
                )
              }}
              页，共 {{ selectedUserTemporaryKnowledgeTotal }} 条
            </div>
            <button
              class="btn compact"
              :disabled="selectedUserTemporaryKnowledgePage >= selectedUserTemporaryKnowledgePageCount || detailRunning"
              @click="setDetailPage('temporary-knowledge', selectedUserTemporaryKnowledgePage + 1)"
            >
              下一页
            </button>
          </div>
        </div>

        <div class="hr"></div>
        <div v-if="detailEditMode" class="actions" style="justify-content: flex-end">
          <button
            class="btn compact danger"
            :disabled="detailRunning || adminRunning !== null || !selectedDetailCount"
            @click="clearAllSelectedUserData"
          >
            删除选中
          </button>
        </div>
        <div v-else class="actions" style="justify-content: flex-end">
          <button
            class="btn compact danger"
            :disabled="detailRunning || adminRunning !== null || !selectedUserQuestions.length"
            @click="clearUserData(selectedAdminUser, 'questions')"
          >
            删除提问历史
          </button>
          <button
            class="btn compact danger"
            :disabled="detailRunning || adminRunning !== null || !selectedUserKnowledge.length"
            @click="clearUserData(selectedAdminUser, 'knowledge-records')"
          >
            删除录入记录
          </button>
          <button
            class="btn compact danger"
            :disabled="detailRunning || adminRunning !== null || !selectedUserTemporaryKnowledge.length"
            @click="clearUserData(selectedAdminUser, 'temporary-knowledge')"
          >
            删除临时知识
          </button>
          <button
            class="btn compact danger"
            :disabled="detailRunning || adminRunning !== null || !selectedUserTotalRecords"
            @click="clearUserData(selectedAdminUser, 'data')"
          >
            删除全部
          </button>
        </div>
      </div>
    </section>

    <section v-if="!isAdmin" class="card">
      <div class="actions" style="justify-content: space-between">
        <h3 style="margin: 0">{{ knowledgeTitle }}</h3>
        <div class="actions">
          <button
            v-if="!isAdmin"
            class="btn compact danger"
            :disabled="running || !selectedKnowledgeCount"
            @click="clearSelectedMyKnowledgeRecords"
          >
            删除选中
          </button>
          <button
            v-if="!isAdmin"
            class="btn compact danger"
            :disabled="running || !knowledge.length"
            @click="clearMyKnowledgeRecords"
          >
            清空录入记录
          </button>
          <div v-if="selectedKnowledgeCount" class="pill">已选 {{ selectedKnowledgeCount }} 条</div>
          <div class="pill">{{ knowledge.length }} 条</div>
        </div>
      </div>
      <div class="hr"></div>

      <div v-if="running && !knowledge.length" class="empty-state">正在加载知识录入历史...</div>

      <div v-else-if="!knowledge.length" class="empty-state">
        {{ knowledgeEmptyText }}
      </div>

      <table v-else class="table">
        <thead>
          <tr>
            <th v-if="!isAdmin" class="nowrap">
              <input
                type="checkbox"
                :checked="
                  Boolean(personalKnowledgePageItems.length) &&
                  personalKnowledgePageItems.every((item) => selectedKnowledgeIds.includes(item.id))
                "
                :disabled="!personalKnowledgePageItems.length"
                @change="onToggleAllKnowledge"
              />
            </th>
            <th v-if="isAdmin" class="nowrap">用户</th>
            <th>文档</th>
            <th class="nowrap">向量库</th>
            <th class="nowrap">片段</th>
            <th>内容预览</th>
            <th class="nowrap">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in personalKnowledgePageItems" :key="item.id">
            <td v-if="!isAdmin" class="nowrap">
              <input v-model="selectedKnowledgeIds" type="checkbox" :value="item.id" />
            </td>
            <td v-if="isAdmin" class="mono nowrap">{{ item.userId }}</td>
            <td>
              <div>{{ item.documentName }}</div>
              <div class="muted small mono">{{ item.docId }} / {{ item.source }}</div>
            </td>
            <td class="mono nowrap">{{ item.vectorStoreMode }}</td>
            <td class="mono nowrap">{{ item.chunkCount }}</td>
            <td class="muted small">{{ clip(item.contentPreview, 120) }}</td>
            <td class="muted small nowrap">{{ formatTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div
        v-if="knowledge.length > PERSONAL_PAGE_SIZE"
        class="actions"
        style="justify-content: flex-end; margin-top: 12px"
      >
        <button
          class="btn compact"
          :disabled="personalKnowledgePage <= 1"
          @click="setPersonalKnowledgePage(personalKnowledgePage - 1)"
        >
          上一页
        </button>
        <div class="muted small">
          第 {{ pageRangeText(personalKnowledgePage, knowledge.length, PERSONAL_PAGE_SIZE) }} 页，共
          {{ knowledge.length }} 条
        </div>
        <button
          class="btn compact"
          :disabled="personalKnowledgePage >= personalKnowledgePageCount"
          @click="setPersonalKnowledgePage(personalKnowledgePage + 1)"
        >
          下一页
        </button>
      </div>
    </section>

    <section v-if="!isAdmin" class="card">
      <div class="actions" style="justify-content: space-between">
        <h3 style="margin: 0">{{ questionTitle }}</h3>
        <div class="actions">
          <button
            v-if="!isAdmin"
            class="btn compact"
            :disabled="running || !selectedCount"
            @click="mergeSelectedToTemporaryKnowledge"
          >
            合并为临时知识
          </button>
          <button
            v-if="!isAdmin"
            class="btn compact danger"
            :disabled="running || !questions.length"
            @click="clearMyQuestions"
          >
            清空提问历史
          </button>
          <div class="pill">{{ questions.length }} 条</div>
        </div>
      </div>
      <div class="hr"></div>

      <div v-if="mergeResult" class="empty-state" style="margin-bottom: 10px">
        已合并为临时知识：<span class="mono">{{ mergeResult.documentName }}</span
        >， {{ mergeResult.chunkCount }} 个片段。可在“提问”页选择“临时知识”或“全部知识”检索。
      </div>

      <div v-if="running && !questions.length" class="empty-state">正在加载提问历史...</div>

      <div v-else-if="!questions.length" class="empty-state">
        {{ questionEmptyText }}
      </div>

      <table v-else class="table">
        <thead>
          <tr>
            <th v-if="!isAdmin" class="nowrap">
              <input
                type="checkbox"
                :checked="
                  Boolean(personalQuestionsPageItems.length) &&
                  personalQuestionsPageItems.every((item) => selectedQuestionIds.includes(item.id))
                "
                :disabled="!personalQuestionsPageItems.length"
                @change="onToggleAllQuestions"
              />
            </th>
            <th v-if="isAdmin" class="nowrap">用户</th>
            <th>问题</th>
            <th class="nowrap">类型</th>
            <th class="nowrap">命中</th>
            <th>回答预览 / 引用</th>
            <th class="nowrap">用时</th>
            <th class="nowrap">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in personalQuestionsPageItems" :key="item.id">
            <td v-if="!isAdmin" class="nowrap">
              <input v-model="selectedQuestionIds" type="checkbox" :value="item.id" />
            </td>
            <td v-if="isAdmin" class="mono nowrap">{{ item.userId }}</td>
            <td>{{ item.question }}</td>
            <td class="nowrap">{{ requestTypeLabel(item.requestType) }}</td>
            <td class="mono nowrap">{{ item.retrievedChunkCount }}</td>
            <td class="muted small">
              <div v-if="item.answerPreview">{{ clip(item.answerPreview, 120) }}</div>
              <div v-if="item.citationChunkIds.length" class="mono">
                {{ item.citationChunkIds.join(', ') }}
              </div>
              <div v-if="!item.answerPreview && !item.citationChunkIds.length">-</div>
            </td>
            <td class="mono nowrap">{{ formatMs(item.elapsedMs) }}</td>
            <td class="muted small nowrap">{{ formatTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div
        v-if="questions.length > PERSONAL_PAGE_SIZE"
        class="actions"
        style="justify-content: flex-end; margin-top: 12px"
      >
        <button
          class="btn compact"
          :disabled="personalQuestionsPage <= 1"
          @click="setPersonalQuestionsPage(personalQuestionsPage - 1)"
        >
          上一页
        </button>
        <div class="muted small">
          第 {{ pageRangeText(personalQuestionsPage, questions.length, PERSONAL_PAGE_SIZE) }} 页，共
          {{ questions.length }} 条
        </div>
        <button
          class="btn compact"
          :disabled="personalQuestionsPage >= personalQuestionsPageCount"
          @click="setPersonalQuestionsPage(personalQuestionsPage + 1)"
        >
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
