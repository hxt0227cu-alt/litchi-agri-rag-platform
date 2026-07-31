<template>
  <div class="page-shell evaluation-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Evaluation Center</span>
        <h3 class="section-title">真实农户问答评测中心</h3>
        <p class="section-copy">
          这里评测的不是预置题库，而是平台里已经发生过的真实问答记录。系统会先按统一标准做自动评分，
          再把低分记录推给管理员人工复核，最后沉淀成“补知识库、调检索与提示词、补安全规则”的优化依据。
        </p>

        <div class="hero-pills">
          <span class="hero-pill">自动评分先筛查</span>
          <span class="hero-pill">人工复核做兜底</span>
          <span class="hero-pill">质量趋势看演进</span>
        </div>
      </div>

      <div class="hero-side">
        <article class="hero-stat-card">
          <span>待人工复核</span>
          <strong>{{ stats?.reviewPending ?? 0 }}</strong>
        </article>
        <article class="hero-stat-card">
          <span>近 7 天平均自动分</span>
          <strong>{{ formatMetricScore(stats?.recentAvgAutoScore) }}</strong>
        </article>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">真实问答数</div>
        <div class="metric-value">{{ stats?.total ?? 0 }}</div>
        <div class="metric-note">智能问答页里已经产生并被记录下来的真实对话数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已人工复核</div>
        <div class="metric-value">{{ stats?.reviewed ?? 0 }}</div>
        <div class="metric-note">管理员已经补过人工分和复核意见的记录数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">平均自动分</div>
        <div class="metric-value">{{ formatMetricScore(stats?.avgAutoScore) }}</div>
        <div class="metric-note">按准确性、安全性、完整性、可执行性四项合成的 100 分制结果。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">质量趋势</div>
        <div :class="['metric-value', trendClass]">{{ trendLabel }}</div>
        <div class="metric-note">用最近 7 天和前 7 天的自动评分均值做对比，观察模型是否在变好。</div>
      </article>
    </section>

    <section class="soft-card toolbar-card">
      <div class="toolbar">
        <el-input
          v-model="filters.type"
          clearable
          placeholder="按问题类型过滤，例如：病害总览 / 病害区分 / 花果期管理"
        />
        <el-select v-model="reviewFilter" placeholder="复核状态">
          <el-option label="全部状态" value="all" />
          <el-option label="仅待复核" value="pending" />
          <el-option label="仅已复核" value="reviewed" />
          <el-option label="仅无需复核" value="not_needed" />
        </el-select>
        <el-button type="primary" @click="loadAll">刷新列表</el-button>
      </div>
    </section>

    <section class="soft-card usage-guide">
      <header class="guide-header">
        <h3 class="section-title">管理员怎么用</h3>
        <p class="section-copy">这页已经按“真实问答质控台”的思路来组织，不再是预置题库打分页。</p>
      </header>

      <div class="usage-list">
        <article v-for="item in usageSteps" :key="item.title" class="guide-card">
          <strong>{{ item.title }}</strong>
          <span>{{ item.copy }}</span>
        </article>
      </div>
    </section>

    <section class="soft-card rubric-guide">
      <header class="guide-header">
        <h3 class="section-title">自动评分标准</h3>
        <p class="section-copy">这是当前 V1 版评分标准。它先帮你筛查，人工复核负责兜底和纠偏。</p>
      </header>

      <div class="guide-list">
        <article class="guide-card">
          <strong>准确性</strong>
          <span>有没有真正答到农户的问题核心，而不是跑去回答另一个问题。</span>
        </article>
        <article class="guide-card">
          <strong>安全性</strong>
          <span>有没有给出资料里没有支撑的药剂、倍数、动作，或者缺少风险提醒。</span>
        </article>
        <article class="guide-card">
          <strong>完整性</strong>
          <span>有没有把症状判断、建议动作和注意事项讲完整，而不是只答半截。</span>
        </article>
        <article class="guide-card">
          <strong>可执行性</strong>
          <span>农户看完之后是否知道下一步该巡园、复查、补图，还是进入方案页。</span>
        </article>
      </div>
    </section>

    <section class="soft-card feedback-rule-panel">
      <header class="guide-header">
        <h3 class="section-title">当前自动反哺规则</h3>
        <p class="section-copy">这些规则会在下一次智能问答时自动追加到模型提示词里，用来收紧低分问题。</p>
      </header>

      <div v-if="activeFeedbackRules.length" class="feedback-rule-grid">
        <article v-for="rule in activeFeedbackRules" :key="rule.id" class="feedback-rule-card">
          <div class="rule-card-head">
            <strong>{{ rule.title }}</strong>
            <el-tag size="small" effect="plain">{{ ruleSourceLabel(rule.sourceType) }}</el-tag>
          </div>
          <p>{{ rule.instruction }}</p>
          <small>证据 {{ rule.evidenceCount }} 条 · 优先级 {{ rule.priority }}</small>
        </article>
      </div>
      <el-empty v-else description="暂无低分反馈规则，先完成问答和复核后自动生成。" />
    </section>

    <section v-if="topTypes.length" class="soft-card type-panel">
      <header class="guide-header">
        <h3 class="section-title">当前高频问题类型</h3>
        <p class="section-copy">优先盯住量大且低分的类型，优化收益最高。</p>
      </header>

      <div class="type-grid">
        <article v-for="item in topTypes" :key="item.type" class="type-card">
          <strong>{{ item.type }}</strong>
          <span>{{ item.count }} 条</span>
          <small>平均自动分 {{ formatMetricScore(item.avgAutoScore) }}</small>
        </article>
      </div>
    </section>

    <section class="soft-card table-card">
      <header class="table-header">
        <div>
          <h3 class="section-title">评测记录</h3>
          <p class="section-copy">这里列出来的都是真实问答。右侧的人工分、状态和操作都保留在表内，不需要整页横向拖动。</p>
        </div>
      </header>

      <div class="table-scroll">
        <el-table
          class="evaluation-table"
          :data="filteredRecords"
          :fit="false"
          empty-text="暂时还没有真实问答记录，先去智能问答页产生一些问答再回来。"
          height="calc(100vh - 430px)"
        >
          <el-table-column label="时间" width="170">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="140" />
          <el-table-column prop="question" label="农户问题" width="320" show-overflow-tooltip />
          <el-table-column label="自动总分" width="110">
            <template #default="{ row }">
              {{ formatMetricScore(row.autoScore) }}
            </template>
          </el-table-column>
          <el-table-column label="四项明细" width="300">
            <template #default="{ row }">
              <div class="score-breakdown">
                <span>准 {{ formatMetricScore(row.scoreBreakdown?.accuracyScore) }}</span>
                <span>安 {{ formatMetricScore(row.scoreBreakdown?.safetyScore) }}</span>
                <span>完 {{ formatMetricScore(row.scoreBreakdown?.completenessScore) }}</span>
                <span>行 {{ formatMetricScore(row.scoreBreakdown?.actionabilityScore) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源片段" width="100">
            <template #default="{ row }">
              {{ row.sourceCount ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="建议动作" width="150">
            <template #default="{ row }">
              <el-tag :type="actionTagType(row.suggestedAction)" effect="plain">
                {{ row.suggestedAction || '持续观察' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="人工分" width="90">
            <template #default="{ row }">
              {{ row.humanScore ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="复核状态" width="120">
            <template #default="{ row }">
              <el-tag :type="reviewTagType(row.reviewStatus)" effect="plain">
                {{ reviewStatusLabel(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button link type="primary" @click="openRecord(row)">查看详情</el-button>
                <el-button link type="success" @click="scoreAnswer(row)">人工复核</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-drawer v-model="detailVisible" size="560px" :with-header="false">
      <div v-if="activeRecord" class="detail-panel">
        <div class="detail-header">
          <div>
            <span class="hero-kicker">Record Detail</span>
            <h3 class="section-title">问答详情</h3>
          </div>
          <el-tag :type="reviewTagType(activeRecord.reviewStatus)" effect="plain">
            {{ reviewStatusLabel(activeRecord.reviewStatus) }}
          </el-tag>
        </div>

        <section class="detail-block">
          <h4>农户问题</h4>
          <p>{{ activeRecord.question }}</p>
        </section>

        <section class="detail-block">
          <h4>模型回答</h4>
          <p>{{ activeRecord.systemAnswer || '暂无回答' }}</p>
        </section>

        <section class="detail-grid">
          <article class="detail-card">
            <span>自动总分</span>
            <strong>{{ formatMetricScore(activeRecord.autoScore) }}</strong>
          </article>
          <article class="detail-card">
            <span>建议动作</span>
            <strong>{{ activeRecord.suggestedAction || '持续观察' }}</strong>
          </article>
          <article class="detail-card">
            <span>人工分</span>
            <strong>{{ activeRecord.humanScore ?? '-' }}</strong>
          </article>
          <article class="detail-card">
            <span>来源片段</span>
            <strong>{{ activeRecord.sourceCount ?? 0 }}</strong>
          </article>
        </section>

        <section class="detail-block">
          <h4>优化建议</h4>
          <p>{{ activeRecord.improvementHint || '当前记录暂无额外建议。' }}</p>
        </section>

        <section class="detail-block">
          <h4>复核备注</h4>
          <p>{{ activeRecord.reviewNote || '尚未填写复核备注。' }}</p>
        </section>

        <section class="detail-block">
          <h4>来源依据</h4>
          <div v-if="activeRecord.sources?.length" class="source-list">
            <article v-for="(source, index) in activeRecord.sources" :key="`${source.source}-${index}`" class="source-card">
              <strong>{{ source.title || source.source }}</strong>
              <span>{{ source.source }}</span>
              <p>{{ source.content }}</p>
            </article>
          </div>
          <el-empty v-else description="这条记录没有命中来源片段。" />
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { evaluationAPI, type EvaluationRecord, type EvaluationStats } from '@/api'
import { PAGE_SIZE } from '@/config/constants'

const filters = ref({
  type: ''
})
const reviewFilter = ref<'all' | 'pending' | 'reviewed' | 'not_needed'>('all')
const records = ref<EvaluationRecord[]>([])
const stats = ref<EvaluationStats | null>(null)
const detailVisible = ref(false)
const activeRecord = ref<EvaluationRecord | null>(null)

const usageSteps = [
  {
    title: '先看真实问答，不再看预置题库',
    copy: '这里的每一条记录都来自智能问答页已经发生过的真实对话，所以它更接近农户真实会怎么问、模型真实会怎么答。'
  },
  {
    title: '先用自动分筛低分和待复核',
    copy: '自动分的作用是先帮你筛出明显有问题的回答，管理员不用从头翻全部记录。'
  },
  {
    title: '人工复核补判断和备注',
    copy: '人工分不是替代自动分，而是补充“这条回答到底是知识不够、检索不准，还是规则边界没收紧”。'
  },
  {
    title: '把低分原因转成优化动作',
    copy: '如果准确性低就补知识库或调检索；如果安全性低就补规则；如果完整性和可执行性低就改回答结构。'
  }
]

const filteredRecords = computed(() =>
  records.value.filter(record => {
    if (reviewFilter.value === 'all') {
      return true
    }
    return record.reviewStatus === reviewFilter.value
  })
)

const topTypes = computed(() => (stats.value?.byType ?? []).slice(0, 4))
const activeFeedbackRules = computed(() => stats.value?.activeFeedbackRules ?? [])

const trendLabel = computed(() => {
  const delta = stats.value?.scoreTrendDelta
  if (typeof delta !== 'number') {
    return '待观察'
  }
  if (delta > 0) {
    return `+${delta.toFixed(1)}`
  }
  if (delta < 0) {
    return delta.toFixed(1)
  }
  return '持平'
})

const trendClass = computed(() => {
  const delta = stats.value?.scoreTrendDelta
  if (typeof delta !== 'number') {
    return 'trend-neutral'
  }
  if (delta > 0) {
    return 'trend-up'
  }
  if (delta < 0) {
    return 'trend-down'
  }
  return 'trend-neutral'
})

const formatMetricScore = (value?: number | null) => (typeof value === 'number' ? value.toFixed(1) : '-')

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

const ruleSourceLabel = (sourceType?: string | null) => {
  switch (sourceType) {
    case 'human_review':
      return '人工复核'
    case 'auto_score':
      return '自动低分'
    case 'mixed':
      return '混合来源'
    default:
      return '评测反哺'
  }
}

const loadAll = async () => {
  try {
    const [questionsResponse, statsResponse] = await Promise.all([
      evaluationAPI.questions({
        type: filters.value.type || undefined,
        evaluated: true,
        page: 1,
        size: PAGE_SIZE.large
      }),
      evaluationAPI.stats()
    ])
    records.value = questionsResponse.data.items
    stats.value = statsResponse.data
  } catch {
    ElMessage.error('加载评测数据失败。')
  }
}

const openRecord = (record: EvaluationRecord) => {
  activeRecord.value = record
  detailVisible.value = true
}

const scoreAnswer = async (record: EvaluationRecord) => {
  try {
    const scorePrompt = await ElMessageBox.prompt('请输入 1 到 5 分的人工复核分数', '人工复核', {
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputValidator: input => {
        const score = Number(input)
        return Number.isInteger(score) && score >= 1 && score <= 5 ? true : '请输入 1 到 5 的整数分值'
      }
    })

    const notePrompt = await ElMessageBox.prompt(
      '请写清楚这条回答为什么高分或低分，以及下一步建议做什么优化。',
      '复核备注',
      {
        confirmButtonText: '提交复核',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：资料命中不足，建议补充荔枝病害总览文档，并收紧“病害总览类问题不反问”的提示词约束。'
      }
    )

    await evaluationAPI.submitScore({
      id: record.id,
      humanScore: Number(scorePrompt.value),
      reviewNote: notePrompt.value?.trim() || undefined
    })
    ElMessage.success('人工复核已提交。')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交人工复核失败。')
    }
  }
}

const reviewStatusLabel = (status?: string | null) => {
  switch (status) {
    case 'pending':
      return '待复核'
    case 'reviewed':
      return '已复核'
    case 'not_needed':
      return '无需复核'
    default:
      return '未标记'
  }
}

const reviewTagType = (status?: string | null) => {
  switch (status) {
    case 'pending':
      return 'warning'
    case 'reviewed':
      return 'success'
    case 'not_needed':
      return 'info'
    default:
      return 'info'
  }
}

const actionTagType = (action?: string | null) => {
  switch (action) {
    case '补知识库':
      return 'danger'
    case '调检索与提示词':
      return 'warning'
    case '补安全规则':
      return 'danger'
    case '优化回答结构':
      return 'success'
    default:
      return 'info'
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.evaluation-page {
  gap: 18px;
}

.hero,
.toolbar-card,
.usage-guide,
.rubric-guide,
.feedback-rule-panel,
.type-panel,
.table-card {
  padding: 22px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 18px;
  align-items: center;
}

.hero > *,
.metric-grid > *,
.table-card {
  min-width: 0;
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

.hero-pills {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-pill {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.hero-side {
  display: grid;
  gap: 12px;
}

.hero-stat-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(34, 53, 47, 0.08);
}

.hero-stat-card span {
  color: var(--ink-soft);
  font-size: 13px;
}

.hero-stat-card strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-strong);
  font-size: 28px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px 120px;
  gap: 12px;
}

.guide-header,
.table-header {
  margin-bottom: 18px;
}

.usage-list,
.guide-list,
.feedback-rule-grid,
.type-grid {
  display: grid;
  gap: 12px;
}

.usage-list,
.guide-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.guide-card,
.feedback-rule-card,
.type-card,
.detail-card,
.source-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.8);
}

.guide-card strong,
.feedback-rule-card strong,
.type-card strong,
.detail-card strong,
.source-card strong {
  display: block;
  color: var(--ink-strong);
}

.guide-card span,
.feedback-rule-card p,
.feedback-rule-card small,
.type-card span,
.type-card small,
.detail-card span,
.source-card span,
.source-card p {
  display: block;
  color: var(--ink-soft);
}

.guide-card span,
.feedback-rule-card p,
.source-card p {
  margin-top: 8px;
  line-height: 1.75;
}

.feedback-rule-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.rule-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feedback-rule-card small {
  margin-top: 10px;
}

.type-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.type-card small {
  margin-top: 8px;
}

.table-scroll {
  min-width: 0;
  overflow-x: auto;
  padding-bottom: 4px;
}

.evaluation-table {
  min-width: 1380px;
}

.evaluation-table :deep(.el-table__body-wrapper),
.evaluation-table :deep(.el-scrollbar__wrap) {
  overflow: auto !important;
}

.score-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.score-breakdown span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--ink-soft);
  font-size: 12px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trend-up {
  color: #1f8f5f;
}

.trend-down {
  color: #d65d41;
}

.trend-neutral {
  color: var(--ink-soft);
}

.detail-panel {
  display: grid;
  gap: 18px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-block {
  display: grid;
  gap: 10px;
}

.detail-block h4 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 16px;
}

.detail-block p {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.8;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card strong {
  margin-top: 8px;
  font-size: 22px;
}

.source-list {
  display: grid;
  gap: 12px;
}

.source-card span {
  margin-top: 6px;
}

.source-card p {
  margin-top: 10px;
}

@media (max-width: 1180px) {
  .hero,
  .toolbar,
  .usage-list,
  .guide-list,
  .feedback-rule-grid,
  .type-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
