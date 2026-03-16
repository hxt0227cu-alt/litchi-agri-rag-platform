<template>
  <div class="page-shell overview-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Platform Ready</span>
        <h3>一套可直接投入使用的荔枝智能诊断与问答平台</h3>
        <p>
          当前页面会集中展示服务状态、样例数据、推荐问答和平台操作流程。你可以先点击“初始化平台样例”，再按下方流程逐页体验核心功能。
        </p>
        <div class="hero-actions">
          <el-button v-if="canManageSystem" type="primary" size="large" :loading="bootstrapping" @click="bootstrapDemo">
            初始化平台样例
          </el-button>
          <el-button size="large" @click="loadOverview">刷新状态</el-button>
        </div>
      </div>

      <div class="hero-panel">
        <div class="hero-status">
          <div>
            <span class="hero-status-label">系统状态</span>
            <strong>{{ overview?.services.status === 'healthy' ? '全链路就绪' : '离线可用' }}</strong>
          </div>
          <el-tag :type="overview?.services.status === 'healthy' ? 'success' : 'warning'" effect="dark">
            {{ overview?.services.status === 'healthy' ? '在线增强模式' : '本地保障模式' }}
          </el-tag>
        </div>

        <div class="service-list">
          <div v-for="service in serviceItems" :key="service.key" class="service-item">
            <span>{{ service.label }}</span>
            <strong :class="service.connected ? 'ok' : 'weak'">
              {{ service.text }}
            </strong>
          </div>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">知识文档</div>
        <div class="metric-value">{{ overview?.documents.total ?? 0 }}</div>
        <div class="metric-note">其中 {{ overview?.documents.indexed ?? 0 }} 份已经完成检索切块。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">图谱节点</div>
        <div class="metric-value">{{ overview?.knowledgeGraph.nodeCount ?? 0 }}</div>
        <div class="metric-note">用于演示品种、病害、药剂与技术之间的关联。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">图谱关系</div>
        <div class="metric-value">{{ overview?.knowledgeGraph.edgeCount ?? 0 }}</div>
        <div class="metric-note">可在图谱页点击节点查看详细属性与相邻关系。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">识别引擎</div>
        <div class="metric-value engine-value">{{ overview?.diagnosis.engine ?? 'loading...' }}</div>
        <div class="metric-note">
          {{ overview?.diagnosis.modelLoaded ? '当前已加载 YOLO 模型。' : '当前会自动降级到数据集特征匹配或规则模式。' }}
        </div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card block">
        <header class="block-header">
          <div>
            <h3 class="section-title">推荐业务问题</h3>
            <p class="section-copy">点击任意问题可直接跳转到智能问答页并自动发起提问。</p>
          </div>
        </header>

        <div class="question-list">
          <button
            v-for="question in overview?.suggestedQuestions ?? []"
            :key="question"
            class="question-card"
            type="button"
            @click="askQuestion(question)"
          >
            <span>推荐问题</span>
            <strong>{{ question }}</strong>
          </button>
        </div>
      </article>

      <article class="soft-card block">
        <header class="block-header">
          <div>
            <h3 class="section-title">平台流程</h3>
            <p class="section-copy">按这个顺序讲，逻辑最完整，也最方便老师理解系统闭环。</p>
          </div>
        </header>

        <ol class="flow-list">
          <li v-for="(step, index) in overview?.demoFlow ?? []" :key="step">
            <span class="flow-index">{{ index + 1 }}</span>
            <span>{{ step }}</span>
          </li>
        </ol>
      </article>
    </section>

    <section class="soft-card block">
      <header class="block-header">
        <div>
          <h3 class="section-title">内置平台文档</h3>
          <p class="section-copy">这些样例文档已经用于支撑问答和知识图谱演示，不够的话还可以在文档页继续上传。</p>
        </div>
      </header>

      <div class="doc-grid">
        <article v-for="sample in overview?.documents.samples ?? []" :key="sample.name" class="doc-card">
          <span class="doc-name">{{ sample.name }}</span>
          <strong>{{ sample.title }}</strong>
          <p>{{ sample.summary }}</p>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { hasPermission } from '@/auth/access'
import { systemAPI, type SystemOverviewResponse } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const overview = ref<SystemOverviewResponse | null>(null)
const bootstrapping = ref(false)
const canManageSystem = computed(() => hasPermission(authStore.user?.role, 'system.manage'))

const serviceItems = computed(() => {
  const services = overview.value?.services.services ?? {}
  return [
    { key: 'neo4j', label: 'Neo4j 图谱', connected: services.neo4j === 'connected', text: services.neo4j === 'connected' ? '已连接' : '离线回退' },
    { key: 'milvus', label: 'Milvus 检索', connected: services.milvus === 'connected', text: services.milvus === 'connected' ? '已连接' : '本地检索' },
    { key: 'ollama', label: 'Ollama 模型', connected: services.ollama === 'connected', text: services.ollama === 'connected' ? '已连接' : '规则回答' },
    { key: 'diagnosis', label: '识别服务', connected: services.diagnosis === 'connected', text: services.diagnosis === 'connected' ? 'YOLO 在线' : '降级演示' }
  ]
})

const loadOverview = async () => {
  try {
    const response = await systemAPI.overview()
    overview.value = response.data
  } catch (error) {
    ElMessage.error('加载系统总览失败，请检查后端服务是否启动。')
  }
}

const bootstrapDemo = async () => {
  if (!canManageSystem.value) {
    ElMessage.warning('当前账号没有初始化系统与重建平台样例数据的权限。')
    return
  }
  bootstrapping.value = true
  try {
    const response = await systemAPI.bootstrapDemo()
    ElMessage.success(response.data.message)
    await loadOverview()
  } catch (error) {
    ElMessage.error('初始化平台样例失败，请检查后端依赖服务。')
  } finally {
    bootstrapping.value = false
  }
}

const askQuestion = (question: string) => {
  router.push({
    path: '/chat',
    query: {
      q: question
    }
  })
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.overview-page {
  gap: 22px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 360px;
  gap: 22px;
  padding: 28px;
}

.hero-copy h3 {
  margin: 10px 0 0;
  font-size: 36px;
  line-height: 1.18;
  color: var(--ink-strong);
}

.hero-copy p {
  max-width: 720px;
  margin: 18px 0 0;
  color: var(--ink-soft);
  line-height: 1.8;
  font-size: 15px;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 26px;
  flex-wrap: wrap;
}

.hero-panel {
  display: grid;
  gap: 16px;
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(31, 74, 63, 0.94), rgba(28, 58, 50, 0.96));
  color: #fff4d4;
}

.hero-status {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.hero-status-label {
  display: block;
  color: rgba(255, 244, 212, 0.7);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-status strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.service-list {
  display: grid;
  gap: 10px;
}

.service-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 248, 235, 0.08);
}

.service-item strong.ok {
  color: #a7f3d0;
}

.service-item strong.weak {
  color: #fde68a;
}

.engine-value {
  font-size: 18px;
  line-height: 1.4;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 18px;
}

.block {
  padding: 22px;
}

.block-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.question-list {
  display: grid;
  gap: 12px;
}

.question-card {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(47, 106, 89, 0.1);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(244, 249, 243, 0.92));
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.question-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(31, 74, 63, 0.08);
}

.question-card span {
  display: block;
  color: var(--ink-soft);
  font-size: 12px;
  margin-bottom: 8px;
}

.question-card strong {
  color: var(--ink-strong);
  line-height: 1.6;
}

.flow-list {
  display: grid;
  gap: 14px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.flow-list li {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
}

.flow-index {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-weight: 800;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.doc-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.doc-name {
  color: var(--ink-soft);
  font-size: 12px;
}

.doc-card strong {
  display: block;
  margin-top: 10px;
  color: var(--ink-strong);
}

.doc-card p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
  font-size: 14px;
}

@media (max-width: 1180px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .content-grid,
  .doc-grid {
    grid-template-columns: 1fr;
  }
}
</style>
