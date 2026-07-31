<template>
  <div class="page-shell document-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Knowledge Base</span>
        <h3 class="section-title">知识文档</h3>
        <p class="section-copy">
          这里由管理员统一维护平台知识来源。文档上传后会自动切块并准备问答检索，作为智能问答、
          评测和规则优化的重要依据。
        </p>
      </div>

      <div class="hero-actions">
        <el-input
          v-model="keyword"
          clearable
          placeholder="按标题或上传用户过滤文档"
          class="keyword-input"
          @keyup.enter="loadAll"
        />
        <el-button v-if="canManageSystem" type="primary" :loading="bootstrapping" @click="bootstrapDemo">
          初始化平台样例
        </el-button>
        <el-button :loading="loading" @click="loadAll">刷新列表</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">文档总数</div>
        <div class="metric-value">{{ overview?.documents.total ?? documents.length }}</div>
        <div class="metric-note">当前知识库中全部可管理的文档数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">可检索文档</div>
        <div class="metric-value">{{ overview?.documents.indexed ?? documents.filter(item => item.indexed).length }}</div>
        <div class="metric-note">这些文档已经完成切块准备，可直接参与智能问答检索。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">向量检索状态</div>
        <div class="metric-value compact">{{ health?.services.milvus === 'connected' ? '已连接' : '本地兜底' }}</div>
        <div class="metric-note">Milvus 未连接时，系统仍会使用本地切片匹配兜底。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">样例文档</div>
        <div class="metric-value">{{ overview?.documents.samples.length ?? 0 }}</div>
        <div class="metric-note">可直接用于问答、图谱和识别链路讲解的演示资料数量。</div>
      </article>
    </section>

    <section class="content-grid">
      <div class="side-stack">
        <article class="soft-card block">
          <header class="panel-header">
            <div>
              <h3 class="section-title">服务状态</h3>
              <p class="section-copy">快速确认当前问答、图谱和识别是完整可用，还是处于本地兜底模式。</p>
            </div>
          </header>

          <div class="status-list">
            <div v-for="service in serviceStatuses" :key="service.key" class="status-item">
              <span>{{ service.label }}</span>
              <el-tag :type="service.connected ? 'success' : 'warning'" effect="plain">
                {{ service.connected ? service.onlineText : service.offlineText }}
              </el-tag>
            </div>
          </div>
        </article>

        <article class="soft-card block">
          <header class="panel-header">
            <div>
              <h3 class="section-title">上传补充材料</h3>
              <p class="section-copy">支持 txt、md、csv、json、pdf、docx。</p>
            </div>
          </header>

          <el-alert
            v-if="!canManageDocuments"
            type="info"
            show-icon
            :closable="false"
            title="当前角色对知识文档为只读权限，可浏览文档内容与处理结果，但不能上传或删除。"
          />

          <el-upload
            drag
            action="#"
            :disabled="!canManageDocuments"
            :auto-upload="false"
            :file-list="fileList"
            :limit="1"
            accept=".txt,.md,.csv,.json,.pdf,.docx"
            :on-change="handleFileChange"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文档到这里，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">上传后系统会自动保存、切块并准备问答检索。</div>
            </template>
          </el-upload>

          <el-alert
            v-if="resultAlert"
            class="result-alert"
            :type="resultAlert.type"
            :title="resultAlert.message"
            show-icon
            :closable="false"
          />

          <div class="upload-actions">
            <el-button type="primary" :disabled="!canManageDocuments" :loading="uploading" @click="handleUpload">
              上传并准备检索
            </el-button>
          </div>
        </article>
      </div>

      <article class="soft-card block table-panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">当前文档列表</h3>
            <p class="section-copy">这里可以直接看到文档名称、上传用户、切块数和处理结果。</p>
          </div>
        </header>

        <el-table
          class="document-table"
          :data="documents"
          :row-class-name="tableRowClassName"
          empty-text="暂时没有文档，可先初始化平台样例或上传新资料。"
          height="calc(100vh - 420px)"
        >
          <el-table-column label="文档名称" min-width="220">
            <template #default="{ row }">
              {{ row.title || row.name }}
            </template>
          </el-table-column>
          <el-table-column prop="ownerUsername" label="上传用户" width="120" />
          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="切块" width="100">
            <template #default="{ row }">
              {{ row.chunkCount }}
            </template>
          </el-table-column>
          <el-table-column prop="statusMessage" label="处理结果" min-width="240" />
          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.uploadTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canManageDocuments" type="danger" link @click="handleDelete(row.id)">删除</el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="overview?.documents.samples.length" class="sample-section">
          <div class="sample-header">
            <h4 class="section-title">平台样例摘要</h4>
          </div>

          <div class="sample-list">
            <article v-for="sample in overview.documents.samples" :key="sample.name" class="sample-card">
              <div class="sample-top">
                <div class="sample-copy">
                  <strong>{{ sample.title }}</strong>
                  <span>{{ sample.name }}</span>
                </div>
                <el-tag size="small" effect="plain">摘要卡</el-tag>
              </div>

              <p>{{ sample.summary }}</p>

              <div class="sample-actions">
                <el-button size="small" @click="focusDocument(sample)">定位到上方文档</el-button>
                <el-button
                  v-if="canManageDocuments && getLinkedDocument(sample)"
                  size="small"
                  type="danger"
                  link
                  @click="deleteLinkedDocument(sample)"
                >
                  删除同名文档
                </el-button>
              </div>
            </article>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'

import { hasPermission } from '@/auth/access'
import {
  documentAPI,
  systemAPI,
  type DocumentRecord,
  type SystemHealthResponse,
  type SystemOverviewResponse
} from '@/api'
import { PAGE_SIZE } from '@/config/constants'
import { useAuthStore } from '@/stores/auth'

type AlertType = 'success' | 'warning' | 'error'
type SampleDocument = { name: string; title: string }

const fileList = ref<UploadUserFile[]>([])
const documents = ref<DocumentRecord[]>([])
const keyword = ref('')
const uploading = ref(false)
const loading = ref(false)
const bootstrapping = ref(false)
const resultAlert = ref<{ type: AlertType; message: string } | null>(null)
const health = ref<SystemHealthResponse | null>(null)
const overview = ref<SystemOverviewResponse | null>(null)
const focusedDocumentId = ref<string | null>(null)

const authStore = useAuthStore()
const canManageDocuments = computed(() => hasPermission(authStore.user?.role, 'documents.manage'))
const canManageSystem = computed(() => hasPermission(authStore.user?.role, 'system.manage'))

const serviceStatuses = computed(() => [
  {
    key: 'neo4j',
    label: 'Neo4j 图谱',
    connected: health.value?.services.neo4j === 'connected',
    onlineText: '图谱已连接',
    offlineText: '图谱未连接'
  },
  {
    key: 'milvus',
    label: 'Milvus 检索',
    connected: health.value?.services.milvus === 'connected',
    onlineText: '向量库已连接',
    offlineText: '本地兜底'
  },
  {
    key: 'ollama',
    label: 'Ollama 模型',
    connected: health.value?.services.ollama === 'connected',
    onlineText: '本地模型可用',
    offlineText: '规则兜底'
  },
  {
    key: 'diagnosis',
    label: '识别服务',
    connected: health.value?.services.diagnosis === 'connected',
    onlineText: '识别服务可用',
    offlineText: '识别兜底'
  }
])

const ALLOWED_DOC_EXTENSIONS = ['.pdf', '.docx', '.txt', '.md', '.csv', '.json']
const MAX_DOC_SIZE = 50 * 1024 * 1024

const handleFileChange = (_file: UploadFile, uploadFiles: UploadFiles) => {
  const rawFile = uploadFiles[uploadFiles.length - 1]?.raw as File | undefined
  if (!rawFile) {
    fileList.value = []
    return false
  }

  const ext = rawFile.name.slice(rawFile.name.lastIndexOf('.')).toLowerCase()
  if (!ALLOWED_DOC_EXTENSIONS.includes(ext)) {
    ElMessage.warning('仅支持 PDF、DOCX、TXT、MD、CSV、JSON 格式的文档。')
    fileList.value = []
    return false
  }

  if (rawFile.size > MAX_DOC_SIZE) {
    ElMessage.warning('文档大小不能超过 50MB。')
    fileList.value = []
    return false
  }

  fileList.value = uploadFiles.slice(-1) as UploadUserFile[]
  resultAlert.value = null
}

const handleUpload = async () => {
  if (!canManageDocuments.value) {
    ElMessage.warning('当前账号没有上传知识文档的权限。')
    return
  }

  const rawFile = fileList.value[0]?.raw as File | undefined
  if (!rawFile) {
    ElMessage.warning('请先选择要上传的文档。')
    return
  }

  uploading.value = true
  resultAlert.value = null

  const formData = new FormData()
  formData.append('file', rawFile)
  formData.append('title', rawFile.name.replace(/\.[^.]+$/, ''))

  try {
    const response = await documentAPI.upload(formData)
    const document = response.data
    resultAlert.value = {
      type: document.indexed ? 'success' : 'warning',
      message: `${document.name}：${document.statusMessage}`
    }
    ElMessage.success('文档上传完成。')
    fileList.value = []
    await loadAll()
  } catch {
    resultAlert.value = {
      type: 'error',
      message: '上传失败，请检查后端服务和文档格式。'
    }
    ElMessage.error('文档上传失败。')
  } finally {
    uploading.value = false
  }
}

const loadDocuments = async () => {
  const response = await documentAPI.list({
    page: 1,
    size: PAGE_SIZE.large,
    keyword: keyword.value || undefined
  })
  documents.value = response.data.items
}

const normalizeDocumentName = (value?: string | null) =>
  (value || '')
    .toLowerCase()
    .replace(/\.[^.]+$/, '')
    .replace(/[_\-\s]+/g, '')

const getLinkedDocument = (sample: SampleDocument) =>
  documents.value.find(item => {
    const candidates = [normalizeDocumentName(item.title), normalizeDocumentName(item.name)]
    const sampleKeys = [normalizeDocumentName(sample.title), normalizeDocumentName(sample.name)]
    return candidates.some(candidate => sampleKeys.includes(candidate))
  })

const focusDocument = (sample: SampleDocument) => {
  const linked = getLinkedDocument(sample)
  if (!linked) {
    ElMessage.info('上方列表里还没有找到同名文档，可以先刷新列表或重新导入。')
    return
  }

  focusedDocumentId.value = linked.id
  document.querySelector('.table-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const tableRowClassName = ({ row }: { row: DocumentRecord }) => (row.id === focusedDocumentId.value ? 'focused-row' : '')

const deleteLinkedDocument = (sample: SampleDocument) => {
  const linked = getLinkedDocument(sample)
  if (!linked) {
    ElMessage.info('上方列表里没有找到可删除的同名文档。')
    return
  }
  void handleDelete(linked.id)
}

const loadSystemState = async () => {
  const [healthResponse, overviewResponse] = await Promise.all([systemAPI.health(), systemAPI.overview()])
  health.value = healthResponse.data
  overview.value = overviewResponse.data
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadDocuments(), loadSystemState()])
  } catch {
    ElMessage.error('加载知识文档数据失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const bootstrapDemo = async () => {
  if (!canManageSystem.value) {
    ElMessage.warning('当前账号没有初始化平台样例数据的权限。')
    return
  }

  bootstrapping.value = true
  try {
    const response = await systemAPI.bootstrapDemo()
    ElMessage.success(response.data.message)
    await loadAll()
  } catch {
    ElMessage.error('初始化平台样例失败，请检查依赖服务。')
  } finally {
    bootstrapping.value = false
  }
}

const handleDelete = async (id: string) => {
  if (!canManageDocuments.value) {
    ElMessage.warning('当前账号没有删除知识文档的权限。')
    return
  }

  try {
    await ElMessageBox.confirm('删除后该文档将不再参与问答检索，确认继续吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await documentAPI.delete(id)
    ElMessage.success(response.data.message)
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除文档失败。')
    }
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / Math.pow(1024, index)).toFixed(2)} ${units[index]}`
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.document-page {
  gap: 18px;
}

.hero,
.block {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 14px;
}

.hero-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions {
  display: grid;
  gap: 12px;
}

.keyword-input {
  width: 100%;
}

.content-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
}

.side-stack {
  display: grid;
  gap: 18px;
  align-content: start;
}

.panel-header {
  margin-bottom: 18px;
}

.compact {
  font-size: 22px;
  line-height: 1.45;
}

.status-list {
  display: grid;
  gap: 12px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.upload-icon {
  font-size: 30px;
  color: var(--primary-main);
}

.result-alert {
  margin-top: 16px;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.table-panel {
  min-width: 0;
}

.table-panel :deep(.el-table) {
  --el-table-border-color: rgba(34, 53, 47, 0.08);
  --el-table-header-bg-color: rgba(245, 244, 237, 0.82);
  background: transparent;
}

.document-table :deep(.focused-row) {
  --el-table-tr-bg-color: rgba(255, 207, 120, 0.18);
}

.sample-section {
  margin-top: 18px;
  display: grid;
  gap: 12px;
}

.sample-list {
  display: grid;
  gap: 12px;
}

.sample-card {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(248, 246, 239, 0.92);
}

.sample-top,
.sample-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sample-copy {
  display: grid;
  gap: 6px;
}

.sample-card strong {
  color: var(--ink-strong);
}

.sample-card span,
.sample-card p {
  display: block;
  color: var(--ink-soft);
}

.sample-card span {
  margin-top: 6px;
}

.sample-card p {
  margin: 10px 0 0;
  line-height: 1.75;
}

.sample-actions {
  justify-content: flex-start;
  margin-top: 14px;
  flex-wrap: wrap;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .sample-top {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
