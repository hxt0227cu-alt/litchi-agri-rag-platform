<template>
  <div class="feedback-page page-shell">
    <section class="hero soft-card">
      <div>
        <h3 class="section-title">满意度问卷</h3>
        <p class="section-copy">收集用户对平台问答、图谱、识别、文档与扩展功能的满意度反馈，帮助持续优化演示与交付效果。</p>
      </div>
    </section>

    <section class="content-grid">
      <article class="soft-card form-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">提交反馈</h3>
            <p class="section-copy">请按 1 到 5 分评价当前使用体验，5 分表示最满意。</p>
          </div>
        </header>

        <el-form label-position="top" class="feedback-form">
          <el-form-item label="反馈模块">
            <el-select v-model="form.module" placeholder="请选择反馈模块">
              <el-option v-for="option in moduleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>

          <div class="score-grid">
            <el-form-item label="总体满意度">
              <el-rate v-model="form.overallScore" :max="5" />
            </el-form-item>
            <el-form-item label="准确性">
              <el-rate v-model="form.accuracyScore" :max="5" />
            </el-form-item>
            <el-form-item label="实用性">
              <el-rate v-model="form.practicalityScore" :max="5" />
            </el-form-item>
            <el-form-item label="流畅性">
              <el-rate v-model="form.fluencyScore" :max="5" />
            </el-form-item>
          </div>

          <el-form-item label="补充意见">
            <el-input
              v-model="form.comment"
              type="textarea"
              :rows="5"
              maxlength="500"
              show-word-limit
              placeholder="可以补充说明哪里最好用、哪里还需要改进。"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="submitFeedback">提交问卷</el-button>
            <span>建议在完成一次完整体验后再评分。</span>
          </div>
        </el-form>
      </article>

      <article v-if="canViewStats" class="soft-card stats-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">问卷汇总</h3>
            <p class="section-copy">技术员可以查看当前版本的满意度概况与最近反馈。</p>
          </div>
        </header>

        <section class="metric-grid compact">
          <article class="metric-card">
            <div class="metric-label">问卷总数</div>
            <div class="metric-value">{{ stats?.total ?? 0 }}</div>
          </article>
          <article class="metric-card">
            <div class="metric-label">总体满意度</div>
            <div class="metric-value">{{ stats?.avgOverallScore ?? '-' }}</div>
          </article>
          <article class="metric-card">
            <div class="metric-label">准确性</div>
            <div class="metric-value">{{ stats?.avgAccuracyScore ?? '-' }}</div>
          </article>
          <article class="metric-card">
            <div class="metric-label">实用性</div>
            <div class="metric-value">{{ stats?.avgPracticalityScore ?? '-' }}</div>
          </article>
        </section>

        <div class="module-stats">
          <div v-for="item in stats?.byModule ?? []" :key="item.module" class="module-stat">
            <strong>{{ item.module }}</strong>
            <span>{{ item.count }} 份</span>
            <span>平均 {{ item.avgOverallScore ?? '-' }} 分</span>
          </div>
        </div>

        <div class="recent-list">
          <article v-for="item in stats?.recent ?? []" :key="item.id" class="recent-card">
            <div class="recent-head">
              <strong>{{ item.username }}</strong>
              <span>{{ item.module }}</span>
            </div>
            <div class="recent-scores">
              <span>总分 {{ item.overallScore }}</span>
              <span>准 {{ item.accuracyScore }}</span>
              <span>实 {{ item.practicalityScore }}</span>
              <span>流 {{ item.fluencyScore }}</span>
            </div>
            <p>{{ item.comment || '用户未填写补充意见。' }}</p>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { hasPermission } from '@/auth/access'
import { feedbackAPI, type FeedbackStats } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const canViewStats = computed(() => hasPermission(authStore.user?.role, 'evaluation.access'))
const submitting = ref(false)
const stats = ref<FeedbackStats | null>(null)

const moduleOptions = [
  { label: '整体体验', value: '整体体验' },
  { label: '智能问答', value: '智能问答' },
  { label: '知识图谱', value: '知识图谱' },
  { label: '知识文档', value: '知识文档' },
  { label: '病害识别', value: '病害识别' },
  { label: '培训课堂', value: '培训课堂' },
  { label: '用药指南', value: '用药指南' }
]

const createDefaultForm = () => ({
  module: '整体体验',
  overallScore: 5,
  accuracyScore: 5,
  practicalityScore: 5,
  fluencyScore: 5,
  comment: ''
})

const form = reactive(createDefaultForm())

const resetForm = () => {
  Object.assign(form, createDefaultForm())
}

const loadStats = async () => {
  if (!canViewStats.value) {
    return
  }

  try {
    const response = await feedbackAPI.stats()
    stats.value = response.data
  } catch (error) {
    ElMessage.error('加载问卷统计失败。')
  }
}

const submitFeedback = async () => {
  submitting.value = true
  try {
    await feedbackAPI.submit({
      module: form.module,
      overallScore: form.overallScore,
      accuracyScore: form.accuracyScore,
      practicalityScore: form.practicalityScore,
      fluencyScore: form.fluencyScore,
      comment: form.comment.trim()
    })
    ElMessage.success('满意度问卷已提交，感谢你的反馈。')
    resetForm()
    await loadStats()
  } catch (error) {
    ElMessage.error('提交问卷失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.feedback-page {
  gap: 18px;
}

.hero,
.form-card,
.stats-card {
  padding: 22px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 18px;
}

.feedback-form {
  display: grid;
  gap: 6px;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.form-actions span {
  color: var(--ink-soft);
  font-size: 13px;
}

.compact {
  margin-bottom: 18px;
}

.module-stats,
.recent-list {
  display: grid;
  gap: 12px;
}

.module-stat,
.recent-card {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(47, 106, 89, 0.08);
}

.module-stat {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.recent-head,
.recent-scores {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.recent-head strong {
  color: var(--ink-strong);
}

.recent-head span,
.recent-scores span,
.recent-card p {
  color: var(--ink-soft);
}

.recent-card p {
  margin: 10px 0 0;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .content-grid,
  .score-grid {
    grid-template-columns: 1fr;
  }
}
</style>
