<template>
  <div class="agent-page">
    <section class="agent-console soft-card">
      <div class="console-heading">
        <div>
          <span class="eyebrow">Agent Run</span>
          <h3>新建任务</h3>
        </div>
        <el-tag v-if="!isFarmer" effect="plain" type="info">受控工具</el-tag>
      </div>

      <div class="agent-howto">
        <strong>它能做什么？</strong>
        <span>输入一个复杂的农技问题，系统会<em>自动分步调查</em>：读取你的果园档案 → 检索知识库 → 核对图谱 → 结合门店方案，最后汇总成带依据的结论。每一步用了什么证据都清晰可见。</span>
      </div>

      <el-input
        v-model="goal"
        type="textarea"
        :rows="6"
        maxlength="1000"
        show-word-limit
        resize="none"
        placeholder="例如：连续降雨后荔枝叶片出现褐色病斑，请综合研判原因、处理顺序和可选方案。"
      />

      <div v-if="!isFarmer" class="run-settings">
        <span>步骤上限</span>
        <el-radio-group v-model="maxSteps" size="small">
          <el-radio-button :value="2">2</el-radio-button>
          <el-radio-button :value="3">3</el-radio-button>
          <el-radio-button :value="4">4</el-radio-button>
        </el-radio-group>
      </div>

      <div class="examples">
        <button v-for="item in examples" :key="item" type="button" @click="goal = item">
          {{ item }}
        </button>
      </div>

      <el-button type="primary" :loading="loading" :disabled="!goal.trim()" @click="runAgent">
        <el-icon><VideoPlay /></el-icon>
        执行任务
      </el-button>
      <el-button v-if="activeRunId" type="danger" plain :disabled="!loading" @click="cancelAgent">
        取消任务
      </el-button>
    </section>

    <section class="run-panel">
      <div v-if="!run && !loading" class="empty-run soft-card">
        <el-icon><MagicStick /></el-icon>
        <strong>等待任务</strong>
      </div>

      <div v-if="loading" class="loading-run soft-card">
        <el-icon class="is-loading"><Loading /></el-icon>
        <strong>正在规划并执行工具</strong>
      </div>

      <template v-if="run">
        <header class="run-summary soft-card">
          <div>
            <span class="eyebrow">{{ isFarmer ? '任务结果' : run.runId }}</span>
            <h3>{{ run.goal }}</h3>
          </div>
          <div class="summary-metrics">
            <span>{{ run.steps.length }} 步</span>
            <span v-if="!isFarmer">{{ run.durationMs }} ms</span>
            <el-tag :type="run.degraded ? 'warning' : run.status === 'failed' ? 'danger' : 'success'" effect="dark">
              {{ statusLabel(run.status) }}
            </el-tag>
            <el-tag v-if="run.reviewRequired && !isFarmer" type="danger" effect="plain">需人工复核</el-tag>
          </div>
        </header>

        <div class="trace-list">
          <article v-for="step in run.steps" :key="step.sequence" class="trace-row soft-card">
            <div class="step-index">{{ step.sequence }}</div>
            <div class="step-content" :class="{ clickable: hasEvidence(step) }" @click="toggleStep(step.sequence)">
              <div class="step-heading">
                <strong>{{ toolLabel(step.tool) }}</strong>
                <span class="step-meta">
                  <span v-if="!isFarmer">{{ step.durationMs }} ms</span>
                  <span v-if="hasEvidence(step)" class="step-toggle">{{ expandedSteps.has(step.sequence) ? '收起证据' : '查看证据' }}</span>
                </span>
              </div>
              <p>{{ step.reason }}</p>
              <div class="step-footer">
                <el-tag :type="step.status === 'succeeded' ? 'success' : step.status === 'awaiting_approval' ? 'warning' : 'danger'" size="small" effect="plain">
                  {{ stepStatusLabel(step.status) }}
                </el-tag>
                <span>{{ outputCount(step.output) }}</span>
              </div>
              <div v-if="expandedSteps.has(step.sequence) && evidenceItems(step)" class="step-evidence">
                <div class="evidence-heading">{{ evidenceItems(step)?.heading }}</div>
                <div v-if="evidenceItems(step)?.items.length" class="evidence-list">
                  <div v-for="(item, idx) in evidenceItems(step)?.items ?? []" :key="idx" class="evidence-item">
                    <div class="evidence-title">{{ item.title }}</div>
                    <div v-if="item.meta" class="evidence-meta">{{ item.meta }}</div>
                    <div v-if="item.snippet" class="evidence-snippet">{{ item.snippet }}</div>
                  </div>
                </div>
                <div v-else class="evidence-empty">该步骤未返回具体内容</div>
              </div>
            </div>
          </article>
        </div>

        <article v-if="run.status === 'waiting_approval'" class="approval-panel soft-card">
          <div>
            <strong>待技术员审批</strong>
            <p>审批前不会执行写操作。请核对动作预览后决定是否继续。</p>
          </div>
          <pre v-if="run.pendingAction">{{ JSON.stringify(run.pendingAction, null, 2) }}</pre>
          <div class="approval-actions">
            <el-button type="danger" plain :loading="approvalLoading" @click="reviewAgent('reject')">拒绝</el-button>
            <el-button type="primary" :loading="approvalLoading" @click="reviewAgent('approve')">批准并执行</el-button>
          </div>
        </article>

        <article class="answer-panel soft-card">
          <div class="answer-heading">
            <el-icon><DocumentChecked /></el-icon>
            <strong>综合结论</strong>
          </div>
          <div class="md-body" v-html="renderMarkdown(run.answer)"></div>
        </article>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentChecked, Loading, MagicStick, VideoPlay } from '@element-plus/icons-vue'
import { marked } from 'marked'

import { agentAPI, type AgentRunResponse } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const isFarmer = computed(() => authStore.user?.role === 'farmer')

const goal = ref('')
const maxSteps = ref(3)
const loading = ref(false)
const run = ref<AgentRunResponse | null>(null)
const activeRunId = ref('')
const approvalLoading = ref(false)

const examples = [
  '连续降雨后荔枝叶片出现褐色病斑，请给出研判和处理顺序。',
  '桂味荔枝花果期落果增多，请结合资料和图谱分析管理重点。',
  '蒂蛀虫进入高发期，整理监测依据并推荐可执行方案。'
]

const labels: Record<string, string> = {
  orchard_context: '果园档案',
  knowledge_search: '知识库检索',
  knowledge_graph: '知识图谱研判',
  plan_recommendation: '业务方案推荐',
  pending_remedy_plan: '待审核处置方案'
}

const toolLabel = (tool: string) => labels[tool] ?? tool

const outputCount = (output: Record<string, unknown>) => {
  const count = output?.count
  if (typeof count === 'number') return `${count} 条结果`
  const entities = output?.entities
  if (Array.isArray(entities)) return `${entities.length} 个实体`
  return '已记录证据'
}

const expandedSteps = ref<Set<number>>(new Set())

const toggleStep = (seq: number) => {
  const next = new Set(expandedSteps.value)
  if (next.has(seq)) next.delete(seq)
  else next.add(seq)
  expandedSteps.value = next
}

const renderMarkdown = (text?: string) => {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text
  }
}

interface EvidenceItem { title: string; meta?: string; snippet?: string }
interface EvidenceBlock { heading: string; items: EvidenceItem[] }

const stripMd = (s: string, max = 140) => {
  const plain = (s || '').replace(/[#*>`_\-]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length > max ? plain.slice(0, max) + '…' : plain
}

const evidenceItems = (step: AgentRunResponse['steps'][number]): EvidenceBlock | null => {
  const out = step.output as Record<string, unknown>
  if (!out) return null
  if (step.tool === 'knowledge_search') {
    const matches = (out.matches as Array<Record<string, unknown>>) || []
    return {
      heading: '命中的知识库资料',
      items: matches.map((m) => ({
        title: String(m.title || m.source || '资料').replace(/\.md$/i, ''),
        meta: `相似度 ${Number(m.score || 0).toFixed(2)}`,
        snippet: stripMd(String(m.content || ''))
      }))
    }
  }
  if (step.tool === 'plan_recommendation') {
    const plans = (out.plans as Array<Record<string, unknown>>) || []
    return {
      heading: '推荐的门店方案',
      items: plans.map((p) => ({
        title: String(p.title || '方案'),
        meta: `${p.shopName || ''} · 匹配度 ${Number(p.score || 0).toFixed(0)}`,
        snippet: String(p.summary || '')
      }))
    }
  }
  if (step.tool === 'orchard_context') {
    const orchards = (out.orchards as Array<Record<string, unknown>>) || []
    return {
      heading: '读取的果园档案',
      items: orchards.map((o) => ({
        title: String(o.name || o.variety || '果园'),
        meta: [o.location, o.stage].filter(Boolean).join(' · '),
        snippet: ''
      }))
    }
  }
  if (step.tool === 'knowledge_graph') {
    const entities = (out.entities as Array<Record<string, unknown> | string>) || []
    return {
      heading: '图谱关联实体',
      items: entities.map((e) => {
        if (typeof e === 'string') return { title: e }
        return { title: String(e.name || e.label || '实体'), meta: String(e.type || '') }
      })
    }
  }
  return null
}

const hasEvidence = (step: AgentRunResponse['steps'][number]) => !!evidenceItems(step)

const statusLabel = (status: AgentRunResponse['status']) => {
  const labels: Record<AgentRunResponse['status'], string> = {
    created: '已受理',
    planning: '规划中',
    running: '执行中',
    waiting_approval: '等待确认',
    completed: '已完成',
    degraded: '降级完成',
    failed: '执行失败',
    canceled: '已取消',
    refused: '已拒绝'
  }
  return labels[status]
}

const stepStatusLabel = (status: AgentRunResponse['steps'][number]['status']) => {
  if (status === 'succeeded') return '执行成功'
  if (status === 'awaiting_approval') return '等待审批'
  return '执行失败'
}

const runAgent = async () => {
  const currentGoal = goal.value.trim()
  if (!currentGoal || loading.value) return

  loading.value = true
  run.value = null
  try {
    const accepted = await agentAPI.start({
      goal: currentGoal,
      sessionId: `agent-${Date.now()}`,
      maxSteps: maxSteps.value
    })
    activeRunId.value = accepted.data.runId
    let latest = accepted.data
    for (let attempt = 0; attempt < 240; attempt += 1) {
      await new Promise(resolve => window.setTimeout(resolve, 500))
      latest = (await agentAPI.getV1(latest.runId)).data
      if (['waiting_approval', 'completed', 'degraded', 'failed', 'canceled', 'refused'].includes(latest.status)) {
        break
      }
    }
    run.value = latest
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? 'Agent 任务执行失败。')
  } finally {
    loading.value = false
    activeRunId.value = ''
  }
}

const reviewAgent = async (decision: 'approve' | 'reject') => {
  if (!run.value || approvalLoading.value) return
  approvalLoading.value = true
  try {
    run.value = (await agentAPI.confirm(run.value.runId, decision)).data
    ElMessage.success(decision === 'approve' ? '审批通过，写操作已执行。' : '已拒绝该写操作。')
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? '审批失败。')
  } finally {
    approvalLoading.value = false
  }
}

const cancelAgent = async () => {
  if (!activeRunId.value) return
  try {
    run.value = (await agentAPI.cancel(activeRunId.value)).data
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? '任务取消失败。')
  }
}
</script>

<style scoped>
.agent-page {
  display: grid;
  grid-template-columns: minmax(300px, 0.72fr) minmax(0, 1.28fr);
  gap: 18px;
  align-items: start;
}

.agent-console,
.run-summary,
.trace-row,
.answer-panel,
.empty-run,
.loading-run {
  padding: 22px;
}

.approval-panel {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  border-color: var(--el-color-warning-light-5);
}

.approval-panel p {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.approval-panel pre {
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--surface-muted);
  font-size: 12px;
  white-space: pre-wrap;
}

.approval-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.agent-console {
  position: sticky;
  top: 22px;
  display: grid;
  gap: 18px;
}

.console-heading,
.run-summary,
.step-heading,
.step-footer,
.answer-heading,
.run-settings {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.eyebrow {
  color: var(--accent-main);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}

h3 {
  margin: 6px 0 0;
  color: var(--ink-strong);
  font-size: 20px;
}

.agent-howto {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(47, 106, 89, 0.08);
  border: 1px solid rgba(47, 106, 89, 0.18);
  line-height: 1.7;
}

.agent-howto strong {
  color: var(--primary-deep);
  font-size: 13px;
}

.agent-howto span {
  color: var(--ink-soft);
  font-size: 13px;
}

.agent-howto em {
  color: var(--primary-deep);
  font-style: normal;
  font-weight: 700;
}

.run-settings {
  color: var(--ink-soft);
  font-size: 14px;
}

.examples {
  display: grid;
  gap: 8px;
}

.examples button {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--ink-soft);
  text-align: left;
  line-height: 1.55;
  cursor: pointer;
}

.run-panel,
.trace-list {
  display: grid;
  gap: 14px;
}

.empty-run,
.loading-run {
  min-height: 260px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--ink-soft);
}

.empty-run .el-icon,
.loading-run .el-icon {
  font-size: 32px;
  color: var(--primary-main);
}

.run-summary {
  align-items: flex-start;
}

.run-summary h3 {
  line-height: 1.55;
}

.summary-metrics {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  color: var(--ink-soft);
  font-size: 13px;
}

.trace-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 14px;
}

.step-index {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary-deep);
  color: white;
  font-weight: 800;
}

.step-content p,
.answer-panel p {
  margin: 10px 0;
  color: var(--ink-soft);
  line-height: 1.75;
  white-space: pre-wrap;
}

.step-heading span,
.step-footer span {
  color: var(--ink-soft);
  font-size: 12px;
}

.answer-heading {
  justify-content: flex-start;
  color: var(--primary-deep);
}

.step-content.clickable {
  cursor: pointer;
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ink-soft);
}

.step-toggle {
  color: var(--primary-deep);
  font-weight: 600;
}

.step-evidence {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(47, 106, 89, 0.2);
}

.evidence-heading {
  font-weight: 700;
  font-size: 13px;
  color: var(--primary-deep);
  margin-bottom: 8px;
}

.evidence-list {
  display: grid;
  gap: 6px;
}

.evidence-item {
  padding: 8px 12px;
  background: rgba(47, 106, 89, 0.06);
  border-radius: 8px;
  border-left: 3px solid var(--primary-deep);
}

.evidence-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--ink);
}

.evidence-meta {
  font-size: 11px;
  color: var(--ink-soft);
  margin-top: 2px;
}

.evidence-snippet {
  font-size: 12px;
  color: var(--ink-soft);
  margin-top: 4px;
  line-height: 1.6;
}

.evidence-empty {
  font-size: 12px;
  color: var(--ink-soft);
  font-style: italic;
}

.md-body {
  line-height: 1.85;
  font-size: 14px;
  color: var(--ink);
}

.md-body h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 18px 0 8px;
  color: var(--primary-deep);
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(47, 106, 89, 0.15);
}

.md-body h3 {
  font-size: 15px;
  font-weight: 600;
  margin: 14px 0 6px;
  color: var(--primary-deep);
}

.md-body p {
  margin: 8px 0;
}

.md-body ul,
.md-body ol {
  padding-left: 22px;
  margin: 8px 0;
}

.md-body li {
  margin: 4px 0;
}

.md-body strong {
  color: var(--primary-deep);
  font-weight: 700;
}

.md-body code {
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.md-body hr {
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  margin: 14px 0;
}

@media (max-width: 900px) {
  .agent-page {
    grid-template-columns: 1fr;
  }

  .agent-console {
    position: static;
  }

  .run-summary {
    flex-direction: column;
  }

  .summary-metrics {
    justify-content: flex-start;
  }
}
</style>
