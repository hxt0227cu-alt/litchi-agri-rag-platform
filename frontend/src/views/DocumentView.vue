<template>
  <div class="document-page page-shell">
    <section class="hero glass-card">
      <div>
        <h3 class="section-title">知识文档准备区</h3>
        <p class="section-copy">
          这里负责管理答辩样例文档与现场补充资料。文档上传后会自动切块，并写入本地或 Milvus 检索链路。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" :loading="bootstrapping" @click="bootstrapDemo">一键准备答辩样例</el-button>
        <el-button :loading="loading" @click="loadAll">刷新列表</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">文档总数</div>
        <div class="metric-value">{{ overview?.documents.total ?? documents.length }}</div>
        <div class="metric-note">当前知识库内所有可管理文档数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已建索引</div>
        <div class="metric-value">{{ overview?.documents.indexed ?? documents.filter(item => item.indexed).length }}</div>
        <div class="metric-note">这些文档会参与智能问答的检索增强。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">Milvus 状态</div>
        <div class="metric-value status-value">{{ health?.services.milvus === 'connected' ? '在线' : '本地回退' }}</div>
        <div class="metric-note">Milvus 不在线时，系统仍会使用本地向量和切片兜底。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">答辩样例</div>
        <div class="metric-value">{{ overview?.documents.samples.length ?? 0 }}</div>
        <div class="metric-note">可直接支撑图谱、问答和识别讲解的样例资料。</div>
      </article>
    </section>

    <section class="content-grid">
      <div class="left-stack">
        <article class="soft-card block">
          <header class="block-header">
            <div>
              <h3 class="section-title">系统状态</h3>
              <p class="section-copy">这里能快速判断当前问答、图谱和识别是在线增强还是本地演示模式。</p>
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
          <header class="block-header">
            <div>
              <h3 class="section-title">上传补充材料</h3>
              <p class="section-copy">支持 txt、md、csv、json、pdf、docx。答辩现场最稳妥的是 md 或 txt。</p>
            </div>
          </header>

          <el-upload
            drag
            action="#"
            :auto-upload="false"
            :file-list="fileList"
            :limit="1"
            accept=".txt,.md,.csv,.json,.pdf,.docx"
            :on-change="handleFileChange"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到这里，或 <em>点击选择</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">上传后系统会自动保存、切块和建立检索索引。</div>
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
            <el-button type="primary" :loading="uploading" @click="handleUpload">上传并建立索引</el-button>
          </div>
        </article>
      </div>

      <article class="soft-card block table-panel">
        <header class="block-header">
          <div>
            <h3 class="section-title">当前文档列表</h3>
            <p class="section-copy">这里既能展示样例文档已经入库，也方便现场删除或替换材料。</p>
          </div>
        </header>

        <el-table :data="documents" empty-text="暂无文档，可先准备答辩样例。">
          <el-table-column prop="name" label="文档名称" min-width="220" />
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
          <el-table-column label="索引状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.indexed ? 'success' : 'warning'" effect="plain">
                {{ row.indexed ? '已索引' : '仅保存' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="statusMessage" label="处理结果" min-width="220" />
          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.uploadTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'

import {
  documentAPI,
  systemAPI,
  type DocumentRecord,
  type SystemHealthResponse,
  type SystemOverviewResponse
} from '@/api'

type AlertType = 'success' | 'warning' | 'error'

const fileList = ref<UploadUserFile[]>([])
const documents = ref<DocumentRecord[]>([])
const uploading = ref(false)
const loading = ref(false)
const bootstrapping = ref(false)
const resultAlert = ref<{ type: AlertType; message: string } | null>(null)
const health = ref<SystemHealthResponse | null>(null)
const overview = ref<SystemOverviewResponse | null>(null)

const serviceStatuses = computed(() => [
  {
    key: 'neo4j',
    label: 'Neo4j 图谱',
    connected: health.value?.services.neo4j === 'connected',
    onlineText: '在线图谱',
    offlineText: '本地图谱'
  },
  {
    key: 'milvus',
    label: 'Milvus 检索',
    connected: health.value?.services.milvus === 'connected',
    onlineText: '在线向量库',
    offlineText: '本地检索'
  },
  {
    key: 'ollama',
    label: 'Ollama 模型',
    connected: health.value?.services.ollama === 'connected',
    onlineText: '模型回答',
    offlineText: '规则回答'
  },
  {
    key: 'diagnosis',
    label: '识别服务',
    connected: health.value?.services.diagnosis === 'connected',
    onlineText: 'YOLO 在线',
    offlineText: '演示回退'
  }
])

const handleFileChange = (_file: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles.slice(-1) as UploadUserFile[]
  resultAlert.value = null
}

const handleUpload = async () => {
  const rawFile = fileList.value[0]?.raw as File | undefined
  if (!rawFile) {
    ElMessage.warning('请先选择要上传的文档。')
    return
  }

  uploading.value = true
  resultAlert.value = null

  const formData = new FormData()
  formData.append('file', rawFile)

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
  } catch (error) {
    resultAlert.value = {
      type: 'error',
      message: '上传失败，请检查后端服务和文件格式。'
    }
    ElMessage.error('文档上传失败。')
  } finally {
    uploading.value = false
  }
}

const loadDocuments = async () => {
  const response = await documentAPI.list()
  documents.value = response.data
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
  } catch (error) {
    ElMessage.error('加载文档页数据失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const bootstrapDemo = async () => {
  bootstrapping.value = true
  try {
    const response = await systemAPI.bootstrapDemo()
    ElMessage.success(response.data.message)
    await loadAll()
  } catch (error) {
    ElMessage.error('准备答辩样例失败，请检查依赖服务。')
  } finally {
    bootstrapping.value = false
  }
}

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm('删除后该文档将不会继续参与问答检索，是否继续？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await documentAPI.delete(id)
    ElMessage.success(response.data.message)
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败。')
    }
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / Math.pow(1024, index)).toFixed(2)} ${units[index]}`
}

const formatDate = (value: string) => {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.document-page {
  gap: 18px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.content-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
}

.left-stack {
  display: grid;
  gap: 18px;
  align-content: start;
}

.block {
  padding: 22px;
}

.block-header {
  margin-bottom: 18px;
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
  background: rgba(255, 255, 255, 0.72);
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

.table-panel :deep(.el-table) {
  --el-table-border-color: rgba(34, 53, 47, 0.08);
  --el-table-header-bg-color: rgba(245, 244, 237, 0.82);
  background: transparent;
}

.table-panel :deep(.el-table th.el-table__cell) {
  color: var(--ink-soft);
}

.status-value {
  font-size: 20px;
}

@media (max-width: 1180px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
