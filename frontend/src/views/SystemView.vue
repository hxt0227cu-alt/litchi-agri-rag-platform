<template>
  <div class="page-shell system-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">System Status</span>
        <h3 class="section-title">系统状态与文档存储配置</h3>
        <p class="section-copy">
          这里集中查看平台环境、依赖服务、知识文档数量和文档落盘路径。管理员不仅能确认系统是不是健康，
          还可以直接修改知识库文件和状态文件的存储位置。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" :loading="initializing" @click="initializeAll">初始化图谱与向量</el-button>
        <el-button :loading="bootstrapping" @click="bootstrapDemo">重建平台样例</el-button>
        <el-button :loading="loading" @click="loadSettings">刷新参数</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">当前环境</div>
        <div class="metric-value compact">{{ settings?.environment.profile ?? '-' }}</div>
        <div class="metric-note">用于区分本地、演示和部署环境。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">状态存储</div>
        <div class="metric-value">本地文件</div>
        <div class="metric-note">当前版本的聊天与评测状态保存在本地文件，不依赖 MySQL。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">文档总数</div>
        <div class="metric-value">{{ settings?.platform.documentsTotal ?? 0 }}</div>
        <div class="metric-note">当前知识库中可用于问答与评测的全部文档数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">可检索文档</div>
        <div class="metric-value">{{ settings?.platform.documentsIndexed ?? 0 }}</div>
        <div class="metric-note">已经完成切块准备、可直接参与问答检索的文档数量。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card block">
        <header class="panel-header">
          <div>
            <h3 class="section-title">运行时参数</h3>
            <p class="section-copy">用于说明系统启动策略和重试设置。</p>
          </div>
        </header>

        <div class="kv-list">
          <div class="kv-item">
            <span>自动初始化</span>
            <strong>{{ settings?.environment.autoBootstrap ? 'true' : 'false' }}</strong>
          </div>
          <div class="kv-item">
            <span>最大重试次数</span>
            <strong>{{ settings?.environment.startupMaxAttempts ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>重试间隔</span>
            <strong>{{ settings?.environment.startupRetryDelayMs ?? '-' }} ms</strong>
          </div>
        </div>
      </article>

      <article class="soft-card block">
        <header class="panel-header">
          <div>
            <h3 class="section-title">服务配置</h3>
            <p class="section-copy">便于核对本地模型、识别服务和底层存储地址。</p>
          </div>
        </header>

        <div class="kv-list">
          <div class="kv-item">
            <span>Ollama 地址</span>
            <strong>{{ settings?.services.ollamaBaseUrl ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>默认模型</span>
            <strong>{{ settings?.services.ollamaModel ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>识别服务</span>
            <strong>{{ settings?.services.diagnosisServiceUrl ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>Neo4j</span>
            <strong>{{ settings?.storage.neo4jUri ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>Milvus 集合名</span>
            <strong>{{ settings?.storage.milvusCollectionName ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>状态存储方式</span>
            <strong>{{ settings?.storage.mysqlEnabled ? 'MySQL + 本地文件' : '本地文件' }}</strong>
          </div>
        </div>
      </article>
    </section>

    <section class="content-grid single-grid">
      <article class="soft-card block">
        <header class="panel-header">
          <div>
            <h3 class="section-title">修改存储路径</h3>
            <p class="section-copy">修改后，新上传文档会写入新目录，已有知识库文件也会一起迁移。</p>
          </div>
        </header>

        <el-form label-position="top" class="storage-form">
          <el-form-item label="文档目录">
            <el-input v-model="storageForm.documentStorageDir" placeholder="例如：D:\\litchi-data\\documents" />
          </el-form-item>
          <el-form-item label="状态文件">
            <el-input v-model="storageForm.documentStateFile" placeholder="例如：D:\\litchi-data\\document-state.json" />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button type="primary" :loading="savingStorage" @click="saveStorageSettings">保存路径</el-button>
          <el-button @click="resetStorageForm">恢复当前值</el-button>
        </div>

        <div class="kv-list current-paths">
          <div class="kv-item">
            <span>当前文档目录</span>
            <strong>{{ settings?.storage.documentStorageDir ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>当前状态文件</span>
            <strong>{{ settings?.storage.documentStateFile ?? '-' }}</strong>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { systemAPI, type SystemSettingsResponse } from '@/api'

const loading = ref(false)
const initializing = ref(false)
const bootstrapping = ref(false)
const savingStorage = ref(false)
const settings = ref<SystemSettingsResponse | null>(null)
const storageForm = ref({
  documentStorageDir: '',
  documentStateFile: ''
})

const syncStorageForm = (nextSettings: SystemSettingsResponse | null) => {
  storageForm.value = {
    documentStorageDir: nextSettings?.storage.documentStorageDir ?? '',
    documentStateFile: nextSettings?.storage.documentStateFile ?? ''
  }
}

const loadSettings = async () => {
  loading.value = true
  try {
    const { data } = await systemAPI.settings()
    settings.value = data
    syncStorageForm(data)
  } finally {
    loading.value = false
  }
}

const initializeAll = async () => {
  initializing.value = true
  try {
    const { data } = await systemAPI.initialize('all')
    ElMessage.success(data.message)
    await loadSettings()
  } finally {
    initializing.value = false
  }
}

const bootstrapDemo = async () => {
  bootstrapping.value = true
  try {
    const { data } = await systemAPI.bootstrapDemo()
    ElMessage.success(data.message)
    await loadSettings()
  } finally {
    bootstrapping.value = false
  }
}

const saveStorageSettings = async () => {
  if (!storageForm.value.documentStorageDir.trim() || !storageForm.value.documentStateFile.trim()) {
    ElMessage.warning('请先填写完整的文档目录和状态文件路径。')
    return
  }

  savingStorage.value = true
  try {
    const { data } = await systemAPI.updateStorage({
      documentStorageDir: storageForm.value.documentStorageDir.trim(),
      documentStateFile: storageForm.value.documentStateFile.trim()
    })
    settings.value = data.settings
    syncStorageForm(data.settings)
    ElMessage.success(data.message)
  } catch {
    ElMessage.error('保存存储路径失败，请检查目录是否可写。')
  } finally {
    savingStorage.value = false
  }
}

const resetStorageForm = () => {
  syncStorageForm(settings.value)
}

onMounted(() => {
  void loadSettings()
})
</script>

<style scoped>
.system-page {
  gap: 18px;
}

.hero,
.block {
  padding: 24px;
}

.hero,
.content-grid {
  display: grid;
  gap: 18px;
}

.hero {
  grid-template-columns: minmax(0, 1fr) 260px;
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
  gap: 14px;
}

.content-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.single-grid {
  grid-template-columns: minmax(0, 1fr);
}

.panel-header {
  margin-bottom: 18px;
}

.compact {
  font-size: 22px;
  line-height: 1.45;
}

.storage-form {
  display: grid;
  gap: 4px;
}

.form-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
  margin-bottom: 18px;
}

.current-paths {
  margin-top: 8px;
}

.kv-list {
  display: grid;
  gap: 12px;
}

.kv-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.kv-item span {
  color: var(--ink-soft);
}

.kv-item strong {
  color: var(--ink-strong);
  text-align: right;
  word-break: break-all;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .kv-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .kv-item strong {
    text-align: left;
  }
}
</style>
