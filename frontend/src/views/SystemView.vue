<template>
  <div class="system-page page-shell">
    <section class="hero glass-card">
      <div>
        <h3 class="section-title">系统设置与运维</h3>
        <p class="section-copy">
          集中查看当前平台运行参数、自动初始化配置和核心服务地址，也可以直接执行系统初始化与样例重建。
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
        <div class="metric-value">{{ settings?.environment.profile ?? '-' }}</div>
        <div class="metric-note">后端当前激活的 Spring Profile。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">自动引导</div>
        <div class="metric-value">{{ settings?.environment.autoBootstrap ? '开启' : '关闭' }}</div>
        <div class="metric-note">Docker 启动后是否自动准备图谱、向量和平台样例。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">文档总数</div>
        <div class="metric-value">{{ settings?.platform.documentsTotal ?? 0 }}</div>
        <div class="metric-note">当前平台可用于问答的全部文档数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">索引文档</div>
        <div class="metric-value">{{ settings?.platform.documentsIndexed ?? 0 }}</div>
        <div class="metric-note">已完成分块与索引准备的文档数量。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card block">
        <header class="block-header">
          <div>
            <h3 class="section-title">运行时参数</h3>
            <p class="section-copy">这些参数决定启动引导节奏和自动化准备行为。</p>
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
        <header class="block-header">
          <div>
            <h3 class="section-title">服务配置</h3>
            <p class="section-copy">便于核对当前依赖链路是否与部署配置一致。</p>
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
            <span>Milvus 集合</span>
            <strong>{{ settings?.storage.milvusCollectionName ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>MySQL</span>
            <strong>{{ settings?.storage.mysqlEnabled ? '已启用' : '未启用' }}</strong>
          </div>
        </div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card block">
        <header class="block-header">
          <div>
            <h3 class="section-title">存储路径</h3>
            <p class="section-copy">用于核对持久化目录和本地状态文件位置。</p>
          </div>
        </header>

        <div class="kv-list">
          <div class="kv-item">
            <span>MySQL URL</span>
            <strong>{{ settings?.storage.mysqlUrl ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>文档目录</span>
            <strong>{{ settings?.storage.documentStorageDir ?? '-' }}</strong>
          </div>
          <div class="kv-item">
            <span>状态文件</span>
            <strong>{{ settings?.storage.documentStateFile ?? '-' }}</strong>
          </div>
        </div>
      </article>

      <article class="soft-card block">
        <header class="block-header">
          <div>
            <h3 class="section-title">平台管理摘要</h3>
            <p class="section-copy">展示当前可管理角色和推荐样例问题。</p>
          </div>
        </header>

        <div class="tag-list">
          <el-tag v-for="role in settings?.platform.managedRoles ?? []" :key="role" effect="plain">
            {{ role }}
          </el-tag>
        </div>

        <ul class="question-list">
          <li v-for="question in settings?.platform.sampleQuestions ?? []" :key="question">
            {{ question }}
          </li>
        </ul>
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
const settings = ref<SystemSettingsResponse | null>(null)

const loadSettings = async () => {
  loading.value = true
  try {
    const [{ data: nextSettings }] = await Promise.all([systemAPI.settings()])
    settings.value = nextSettings
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

onMounted(() => {
  void loadSettings()
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.metric-card,
.block {
  padding: 22px;
}

.metric-card {
  border: 1px solid rgba(178, 126, 68, 0.14);
  border-radius: 24px;
  background: rgba(255, 251, 244, 0.9);
}

.metric-label {
  color: #87684a;
  font-size: 13px;
  letter-spacing: 0.08em;
}

.metric-value {
  margin-top: 12px;
  color: #203529;
  font-size: 30px;
  font-weight: 700;
}

.metric-note {
  margin-top: 8px;
  color: #6d665d;
  line-height: 1.7;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.block-header {
  margin-bottom: 18px;
}

.kv-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kv-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 251, 244, 0.75);
  border: 1px solid rgba(178, 126, 68, 0.12);
}

.kv-item span {
  color: #7d6e60;
}

.kv-item strong {
  color: #203529;
  text-align: right;
  word-break: break-all;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.question-list {
  margin: 0;
  padding-left: 18px;
  color: #4b4b4b;
  line-height: 1.8;
}

@media (max-width: 1100px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .kv-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .kv-item strong {
    text-align: left;
  }
}
</style>
