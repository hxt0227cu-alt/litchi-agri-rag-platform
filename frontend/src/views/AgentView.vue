<template>
  <div class="agent-page">
    <section class="agent-console soft-card">
      <div class="console-heading">
        <div>
          <span class="eyebrow">Agent Run</span>
          <h3>新建任务</h3>
        </div>
        <el-tag effect="plain" type="info">受控工具</el-tag>
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

      <div class="run-settings">
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
            <span class="eyebrow">{{ run.runId }}</span>
            <h3>{{ run.goal }}</h3>
          </div>
          <div class="summary-metrics">
            <span>{{ run.steps.length }} 步</span>
            <span>{{ run.durationMs }} ms</span>
            <el-tag :type="run.degraded ? 'warning' : run.status === 'failed' ? 'danger' : 'success'" effect="dark">
              {{ statusLabel(run.status) }}
            </el-tag>
            <el-tag v-if="run.reviewRequired" type="danger" effect="plain">需人工复核</el-tag>
          </div>
        </header>

        <div class="trace-list">
          <article v-for="step in run.steps" :key="step.sequence" class="trace-row soft-card">
            <div class="step-index">{{ step.sequence }}</div>
            <div class="step-content">
              <div class="step-heading">
                <strong>{{ toolLabel(step.tool) }}</strong>
                <span>{{ step.durationMs }} ms</span>
              </div>
              <p>{{ step.reason }}</p>
              <div class="step-footer">
                <el-tag :type="step.status === 'succeeded' ? 'success' : step.status === 'awaiting_approval' ? 'warning' : 'danger'" size="small" effect="plain">
                  {{ stepStatusLabel(step.status) }}
                </el-tag>
                <span>{{ outputCount(step.output) }}</span>
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
          <p>{{ run.answer }}</p>
        </article>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentChecked, Loading, MagicStick, VideoPlay } from '@element-plus/icons-vue'

import { agentAPI, type AgentRunResponse } from '@/api'

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

const statusLabel = (status: AgentRunResponse['status']) => {
  const labels: Record<AgentRunResponse['status'], string> = {
    created: '已受理',
    planning: '规划中',
    running: '执行中',
    waiting_approval: '等待确认',
    completed: '已完成',
    degraded: '降级完成',
    failed: '执行失败',
    canceled: '已取消'
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
      if (['waiting_approval', 'completed', 'degraded', 'failed', 'canceled'].includes(latest.status)) {
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
