<template>
  <div class="page-shell overview-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Admin Console</span>
        <h3 class="section-title">管理员工作台</h3>
        <p class="section-copy">
          管理员在这里集中查看服务健康、文档索引、自动评分和协同推荐运行情况，用工程化方式持续优化 AI。
        </p>

        <div class="hero-focus-row">
          <span class="focus-pill">先看低分问题</span>
          <span class="focus-pill">再看知识来源</span>
          <span class="focus-pill">最后核对系统状态</span>
        </div>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goTo('/evaluation')">进入评测中心</el-button>
        <el-button @click="goTo('/document')">管理知识文档</el-button>
        <el-button @click="goTo('/system')">查看系统状态</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">服务健康状态</div>
        <div class="metric-value compact">{{ serviceStatusLabel }}</div>
        <div class="metric-note">聚合 Neo4j、Milvus、Ollama 和识别服务的运行情况。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">文档索引状态</div>
        <div class="metric-value">{{ overview?.documents.indexed ?? 0 }}/{{ overview?.documents.total ?? 0 }}</div>
        <div class="metric-note">知识文档由管理员独占维护，是问答和评测的主要来源。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">自动评分均值</div>
        <div class="metric-value">{{ autoScoreAverageLabel }}</div>
        <div class="metric-note">优先读取量表自动评分，便于快速判断系统回答质量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">低分问题数量</div>
        <div class="metric-value">{{ lowScoreCount }}</div>
        <div class="metric-note">低于阈值的记录会进入管理员复核关注列表。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">推荐服务命中</div>
        <div class="metric-value compact">{{ recommendationHitLabel }}</div>
        <div class="metric-note">帮助判断协同推荐是否已经真正跑起来，而不只是静态页面。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">服务健康</h3>
            <p class="section-copy">服务越稳定，越适合继续做文档更新、规则调优和评测复核。</p>
          </div>
        </header>

        <div class="service-list">
          <div v-for="service in serviceItems" :key="service.key" class="service-card">
            <span>{{ service.label }}</span>
            <strong :class="service.connected ? 'ok' : 'weak'">{{ service.text }}</strong>
          </div>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">低分复核关注</h3>
            <p class="section-copy">优先处理低分问答、缺少安全提醒或可执行性不足的问题。</p>
          </div>
        </header>

        <div class="issue-list">
          <article v-for="item in lowScorePreview" :key="item.id" class="issue-card">
            <strong>{{ item.question }}</strong>
            <p>{{ item.type }}</p>
            <span>{{ item.scoreLabel }}</span>
          </article>
          <el-empty v-if="!lowScorePreview.length" description="当前没有需要重点复核的低分问题。" />
        </div>
      </article>
    </section>

    <section class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">协同推荐概况</h3>
          <p class="section-copy">用于观察方案库、求助量、待处理量和当前最热病症标签。</p>
        </div>
      </header>

      <div class="summary-grid">
        <article class="summary-card">
          <span>活跃方案</span>
          <strong>{{ overview?.collaboration?.activePlans ?? 0 }}</strong>
        </article>
        <article class="summary-card">
          <span>累计求助</span>
          <strong>{{ overview?.collaboration?.consultationCount ?? 0 }}</strong>
        </article>
        <article class="summary-card">
          <span>待处理求助</span>
          <strong>{{ overview?.collaboration?.pendingConsultations ?? 0 }}</strong>
        </article>
        <article class="summary-card">
          <span>当前高频病症</span>
          <strong>{{ overview?.collaboration?.topDisease ?? '暂无' }}</strong>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { evaluationAPI, systemAPI, type EvaluationRecord, type SystemOverviewResponse } from '@/api'
import { LOW_SCORE_THRESHOLD, PAGE_SIZE } from '@/config/constants'

const router = useRouter()
const overview = ref<SystemOverviewResponse | null>(null)
const evaluationRecords = ref<EvaluationRecord[]>([])

const serviceStatusLabel = computed(() =>
  overview.value?.services.status === 'healthy'
    ? '健康'
    : overview.value?.services.status === 'degraded'
      ? '降级'
      : '待检测'
)

const autoScoreValues = computed(() =>
  evaluationRecords.value
    .map(item => item.autoScore)
    .filter((value): value is number => typeof value === 'number')
)

const autoScoreAverageLabel = computed(() => {
  if (!autoScoreValues.value.length) {
    return '待生成'
  }
  const avg = autoScoreValues.value.reduce((total, value) => total + value, 0) / autoScoreValues.value.length
  return avg.toFixed(1)
})

const lowScoreRecords = computed(() =>
  evaluationRecords.value.filter(item => {
    if (typeof item.autoScore === 'number') {
      return item.autoScore < LOW_SCORE_THRESHOLD
    }
    return typeof item.humanScore === 'number' ? item.humanScore <= 2 : false
  })
)

const lowScorePreview = computed(() =>
  lowScoreRecords.value.slice(0, 4).map(item => ({
    id: item.id,
    question: item.question,
    type: item.type,
    scoreLabel:
      typeof item.autoScore === 'number'
        ? `自动评分 ${item.autoScore.toFixed(1)}`
        : typeof item.humanScore === 'number'
          ? `人工评分 ${item.humanScore}`
          : '待评分'
  }))
)

const lowScoreCount = computed(() => lowScoreRecords.value.length)

const recommendationHitLabel = computed(() => {
  const collaboration = overview.value?.collaboration
  if (!collaboration) {
    return '待同步'
  }
  return `${collaboration.activePlans} 方案 / ${collaboration.consultationCount} 求助`
})

const serviceItems = computed(() => {
  const services = overview.value?.services.services ?? {}
  return [
    {
      key: 'neo4j',
      label: 'Neo4j 图谱',
      connected: services.neo4j === 'connected',
      text: services.neo4j === 'connected' ? '已连接' : '不可用'
    },
    {
      key: 'milvus',
      label: 'Milvus 检索',
      connected: services.milvus === 'connected',
      text: services.milvus === 'connected' ? '已连接' : '不可用'
    },
    {
      key: 'ollama',
      label: 'Ollama 模型',
      connected: services.ollama === 'connected',
      text: services.ollama === 'connected' ? '已连接' : '不可用'
    },
    {
      key: 'diagnosis',
      label: '识别服务',
      connected: services.diagnosis === 'connected',
      text: services.diagnosis === 'connected' ? '在线' : '降级'
    }
  ]
})

const loadData = async () => {
  try {
    const [overviewResponse, questionsResponse] = await Promise.all([
      systemAPI.overview(),
      evaluationAPI.questions({
        evaluated: true,
        page: 1,
        size: PAGE_SIZE.large
      })
    ])
    overview.value = overviewResponse.data
    evaluationRecords.value = questionsResponse.data.items
  } catch {
    ElMessage.error('加载管理员工作台数据失败，请检查后端服务。')
  }
}

const goTo = (path: string) => {
  router.push(path)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.overview-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 18px;
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

.hero-focus-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.focus-pill {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.hero-actions,
.content-grid,
.summary-grid {
  display: grid;
  gap: 14px;
}

.content-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.compact {
  font-size: 22px;
}

.panel-header {
  margin-bottom: 18px;
}

.service-list,
.issue-list {
  display: grid;
  gap: 12px;
}

.service-card,
.issue-card,
.summary-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.82);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.service-card:hover,
.issue-card:hover,
.summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(47, 106, 89, 0.16);
}

.service-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.service-card strong.ok {
  color: #0f766e;
}

.service-card strong.weak {
  color: #b45309;
}

.issue-card p,
.issue-card span,
.summary-card span {
  color: var(--ink-soft);
}

.issue-card p,
.summary-card strong {
  margin: 8px 0 0;
}

.summary-card strong {
  display: block;
  color: var(--ink-strong);
  font-size: 24px;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
