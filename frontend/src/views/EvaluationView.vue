<template>
  <div class="evaluation-page page-shell">
    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">题目总数</div>
        <div class="metric-value">{{ stats?.total ?? 0 }}</div>
        <div class="metric-note">当前评测题库中的问题数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已评测</div>
        <div class="metric-value">{{ stats?.evaluated ?? 0 }}</div>
        <div class="metric-note">至少提交过系统答案或人工评分的题目数量。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">平均 BLEU</div>
        <div class="metric-value">{{ stats?.avgBleuScore ?? '-' }}</div>
        <div class="metric-note">系统答案与参考答案之间的自动评价结果。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">平均人工分</div>
        <div class="metric-value">{{ stats?.avgHumanScore ?? '-' }}</div>
        <div class="metric-note">当前人工评分平均值。</div>
      </article>
    </section>

    <section class="soft-card toolbar">
      <el-input v-model="filters.type" clearable placeholder="按类型过滤，例如：病害识别" />
      <el-select v-model="evaluatedValue" placeholder="评测状态">
        <el-option label="全部状态" value="all" />
        <el-option label="仅未评测" value="false" />
        <el-option label="仅已评测" value="true" />
      </el-select>
      <el-button type="primary" @click="loadAll">刷新列表</el-button>
    </section>

    <section class="soft-card table-card">
      <el-table :data="records" empty-text="暂无评测题目。">
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="question" label="问题" min-width="220" />
        <el-table-column prop="referenceAnswer" label="参考答案" min-width="260" />
        <el-table-column label="系统答案" min-width="260">
          <template #default="{ row }">
            {{ row.systemAnswer || '未提交' }}
          </template>
        </el-table-column>
        <el-table-column label="BLEU" width="90">
          <template #default="{ row }">
            {{ row.bleuScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="人工分" width="90">
          <template #default="{ row }">
            {{ row.humanScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.evaluated ? 'success' : 'warning'" effect="plain">
              {{ row.evaluated ? '已评测' : '未评测' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click="editAnswer(row.id)">提交答案</el-button>
              <el-button link type="success" @click="scoreAnswer(row.id)">人工评分</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { evaluationAPI, type EvaluationRecord, type EvaluationStats } from '@/api'

const filters = ref({
  type: ''
})
const evaluatedValue = ref<'all' | 'true' | 'false'>('all')
const records = ref<EvaluationRecord[]>([])
const stats = ref<EvaluationStats | null>(null)

const evaluatedFilter = computed<boolean | undefined>(() => {
  if (evaluatedValue.value === 'all') {
    return undefined
  }
  return evaluatedValue.value === 'true'
})

const loadAll = async () => {
  try {
    const [questionsResponse, statsResponse] = await Promise.all([
      evaluationAPI.questions({
        type: filters.value.type || undefined,
        evaluated: evaluatedFilter.value,
        page: 1,
        size: 100
      }),
      evaluationAPI.stats()
    ])
    records.value = questionsResponse.data.items
    stats.value = statsResponse.data
  } catch (error) {
    ElMessage.error('加载评测数据失败。')
  }
}

const editAnswer = async (id: number) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入系统生成答案', '提交系统答案', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: input => (input.trim() ? true : '系统答案不能为空')
    })
    await evaluationAPI.submitAnswer({
      id,
      systemAnswer: value
    })
    ElMessage.success('系统答案已提交。')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交系统答案失败。')
    }
  }
}

const scoreAnswer = async (id: number) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入 1 到 5 分', '人工评分', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputValidator: input => {
        const score = Number(input)
        return Number.isInteger(score) && score >= 1 && score <= 5 ? true : '请输入 1 到 5 的整数分值'
      }
    })
    await evaluationAPI.submitScore({
      id,
      humanScore: Number(value)
    })
    ElMessage.success('人工评分已提交。')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交人工评分失败。')
    }
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.toolbar,
.table-card {
  padding: 22px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px auto;
  gap: 14px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

@media (max-width: 1080px) {
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
