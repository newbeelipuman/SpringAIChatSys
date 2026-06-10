import { createRouter, createWebHistory } from 'vue-router'
import ChatPage from './views/ChatPage.vue'
import AdminPage from './views/AdminPage.vue'
import HistoryPage from './views/HistoryPage.vue'
import IngestPage from './views/IngestPage.vue'
import StatusPage from './views/StatusPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: ChatPage, meta: { title: '提问 / 回答 / 来源' } },
    { path: '/ingest', component: IngestPage, meta: { title: '素材管理 / 知识库写入' } },
    { path: '/history', component: HistoryPage, meta: { title: '历史记录' } },
    { path: '/admin', component: AdminPage, meta: { title: '管理端 / 用户与数据' } },
    { path: '/status', component: StatusPage, meta: { title: '服务状态 / 模型接入' } },
  ],
})
