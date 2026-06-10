<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiGet, apiPost } from '../../lib/api'
import type { DemoMaterialDTO, IngestMaterialsResponse, IngestRequest, IngestResponse } from '../../lib/types'
import { formatMs, safeString } from '../../lib/util'

const docId = ref(localStorage.getItem('ingest.docId') ?? 'demo')
const documentName = ref(localStorage.getItem('ingest.documentName') ?? 'Demo Doc')
const source = ref(localStorage.getItem('ingest.source') ?? 'manual')
const content = ref('')
const storageMode = ref<'temporary' | 'persistent'>(
  (localStorage.getItem('ingest.storageMode') as 'temporary' | 'persistent' | null) ?? 'temporary',
)

const running = ref(false)
const error = ref<string | null>(null)
const result = ref<IngestResponse | null>(null)

const materialsRunning = ref(false)
const materialsError = ref<string | null>(null)
const materials = ref<DemoMaterialDTO[] | null>(null)
const materialsIngest = ref<IngestMaterialsResponse | null>(null)

const canSubmit = computed(() => content.value.trim().length > 0 && !running.value)

function formatBytes(bytes: number): string {
  if (bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}

function persist() {
  localStorage.setItem('ingest.docId', docId.value)
  localStorage.setItem('ingest.documentName', documentName.value)
  localStorage.setItem('ingest.source', source.value)
  localStorage.setItem('ingest.storageMode', storageMode.value)
}

async function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  content.value = await file.text()
  input.value = ''
}

async function refreshMaterials() {
  materialsRunning.value = true
  materialsError.value = null
  try {
    materials.value = await apiGet<DemoMaterialDTO[]>('/demo/materials')
  } catch (e) {
    materialsError.value = safeString((e as Error).message ?? e)
  } finally {
    materialsRunning.value = false
  }
}

async function ingestMaterials() {
  materialsRunning.value = true
  materialsError.value = null
  materialsIngest.value = null
  try {
    materialsIngest.value = await apiPost<IngestMaterialsResponse>('/demo/ingest/materials', {})
    await refreshMaterials()
  } catch (e) {
    materialsError.value = safeString((e as Error).message ?? e)
  } finally {
    materialsRunning.value = false
  }
}

async function resetVectorStore() {
  materialsRunning.value = true
  materialsError.value = null
  try {
    await apiPost('/demo/reset', {})
    materialsIngest.value = null
    result.value = null
    await refreshMaterials()
  } catch (e) {
    materialsError.value = safeString((e as Error).message ?? e)
  } finally {
    materialsRunning.value = false
  }
}

async function ingest() {
  error.value = null
  result.value = null
  running.value = true
  persist()
  try {
    const payload: IngestRequest = {
      docId: docId.value.trim() ? docId.value.trim() : null,
      documentName: documentName.value.trim() ? documentName.value.trim() : null,
      source: source.value.trim() ? source.value.trim() : null,
      content: content.value,
    }
    const path = storageMode.value === 'temporary' ? '/demo/ingest/temporary' : '/demo/ingest'
    result.value = await apiPost<IngestResponse>(path, payload)
  } catch (e) {
    error.value = safeString((e as Error).message ?? e)
  } finally {
    running.value = false
  }
}

onMounted(() => {
  refreshMaterials()
})
</script>

<template>
  <div class="stack">
    <section class="card">
      <h2 style="margin: 0 0 10px">内置素材库</h2>
      <div class="muted small">
        这些素材已经整理好，适合直接用于演示知识库问答。后端重启后，内存向量库会清空，需要重新导入一次。
      </div>

      <div class="hr"></div>

      <div class="actions">
        <button class="btn primary" :disabled="materialsRunning" @click="ingestMaterials">
          {{ materialsRunning ? '导入中…' : '导入素材库' }}
        </button>
        <button class="btn" :disabled="materialsRunning" @click="refreshMaterials">刷新素材清单</button>
        <button class="btn danger" :disabled="materialsRunning" @click="resetVectorStore">清空已导入内容</button>
      </div>

      <div v-if="materialsError" class="danger" style="margin-top: 10px">
        {{ materialsError }}
      </div>

      <div v-if="materialsIngest" class="card" style="margin-top: 12px; padding: 12px">
        <div class="row">
          <div>
            <div class="muted small">成功导入</div>
            <div class="mono">{{ materialsIngest.successCount }}</div>
          </div>
          <div>
            <div class="muted small">导入失败</div>
            <div class="mono">{{ materialsIngest.failCount }}</div>
          </div>
          <div>
            <div class="muted small">知识片段</div>
            <div class="mono">{{ materialsIngest.totalChunks }}</div>
          </div>
          <div>
            <div class="muted small">向量库</div>
            <div class="mono">{{ materialsIngest.vectorStoreMode ?? '-' }}</div>
          </div>
          <div>
            <div class="muted small">用时</div>
            <div class="mono">{{ formatMs(materialsIngest.elapsedMs) }}</div>
          </div>
        </div>
        <div class="hr"></div>
        <table class="table">
          <thead>
            <tr>
              <th>素材文件</th>
              <th class="nowrap">文档编号</th>
              <th class="nowrap">片段数</th>
              <th class="nowrap">用时</th>
              <th>失败原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in materialsIngest.items" :key="item.filename">
              <td class="mono">{{ item.filename }}</td>
              <td class="mono nowrap">{{ item.ingest?.docId ?? '-' }}</td>
              <td class="mono nowrap">{{ item.ingest?.chunkCount ?? '-' }}</td>
              <td class="mono nowrap">{{ item.ingest ? formatMs(item.ingest.elapsedMs) : '-' }}</td>
              <td class="muted small">{{ item.error ?? '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="materials?.length" class="card material-list" style="margin-top: 12px; padding: 12px">
        <div class="actions" style="justify-content: space-between; margin-bottom: 8px">
          <div style="font-weight: 600">素材清单</div>
          <div class="muted small">命名方式：<span class="mono">编号-主题.md</span>，编号代表推荐演示顺序。</div>
        </div>

        <div class="stack">
          <div v-for="m in materials" :key="m.filename" class="material-item">
            <div class="actions" style="justify-content: space-between">
              <div>
                <div class="material-title">{{ m.displayName }}</div>
                <div class="muted small">
                  <span class="pill">{{ m.category }}</span>
                  <span class="mono">{{ m.filename }}</span>
                  <span>·</span>
                  <span>{{ formatBytes(m.bytes) }}</span>
                </div>
              </div>
              <div class="mono small nowrap">{{ m.docId }}</div>
            </div>
            <div class="material-desc">{{ m.description }}</div>
            <div v-if="m.sampleQuestions.length" class="actions">
              <span class="muted small">常见提问：</span>
              <span v-for="q in m.sampleQuestions" :key="q" class="question-chip">{{ q }}</span>
            </div>
            <div class="muted small mono">文件位置：{{ m.source }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="card">
      <h2 style="margin: 0 0 10px">自定义素材写入</h2>
      <div class="muted small">
        可以粘贴一段文本，或上传
        <span class="mono">.txt/.md</span>
        文件。临时录入只对当前用户生效，后端重启后消失；保存到知识库会进入持久向量库。
      </div>

      <div class="hr"></div>

      <div class="row3">
        <div class="field">
          <label>文档编号（可选）</label>
          <input v-model="docId" placeholder="例如 demo / thesis / policy" />
        </div>
        <div class="field">
          <label>文档名称（可选）</label>
          <input v-model="documentName" placeholder="例如 报销制度 / 项目说明" />
        </div>
        <div class="field">
          <label>来源说明（可选）</label>
          <input v-model="source" placeholder="例如 手动录入 / 本地文件" />
        </div>
      </div>

      <div style="height: 10px"></div>

      <div class="field">
        <label>写入方式</label>
        <div class="segmented">
          <button
            type="button"
            class="segment"
            :class="{ active: storageMode === 'temporary' }"
            @click="storageMode = 'temporary'"
          >
            临时录入
          </button>
          <button
            type="button"
            class="segment"
            :class="{ active: storageMode === 'persistent' }"
            @click="storageMode = 'persistent'"
          >
            保存到知识库
          </button>
        </div>
      </div>

      <div style="height: 10px"></div>

      <div class="field">
        <label>素材内容</label>
        <textarea v-model="content" placeholder="在这里粘贴要导入知识库的文本"></textarea>
      </div>

      <div style="height: 10px"></div>

      <div class="actions">
        <button class="btn primary" :disabled="!canSubmit" @click="ingest">
          {{ running ? '写入中…' : storageMode === 'temporary' ? '临时录入' : '保存到知识库' }}
        </button>
        <label class="btn" style="display: inline-flex; align-items: center; gap: 8px">
          <input type="file" accept=".txt,.md,text/plain,text/markdown" style="display: none" @change="onPickFile" />
          上传文本文件
        </label>
        <span class="muted small">临时知识可在“提问”页选择“临时知识”或“全部知识”检索。</span>
      </div>

      <div v-if="error" class="danger" style="margin-top: 10px">
        {{ error }}
      </div>
    </section>

    <section v-if="result" class="card">
      <h3 style="margin: 0 0 10px">写入结果</h3>
      <div class="row">
        <div>
          <div class="muted small">文档编号</div>
          <div class="mono">{{ result.docId ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">文档名称</div>
          <div class="mono">{{ result.documentName ?? '-' }}</div>
        </div>
        <div>
          <div class="muted small">知识片段</div>
          <div class="mono">{{ result.chunkCount }}</div>
        </div>
        <div>
          <div class="muted small">向量维度</div>
          <div class="mono">{{ result.embeddingDimensions }}</div>
        </div>
        <div>
          <div class="muted small">存储位置</div>
          <div class="mono">{{ result.vectorStoreMode }}</div>
        </div>
        <div>
          <div class="muted small">用时</div>
          <div class="mono">{{ formatMs(result.elapsedMs) }}</div>
        </div>
      </div>
    </section>
  </div>
</template>
