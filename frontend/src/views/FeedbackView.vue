<template>
  <div class="page-shell feedback-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Feedback Loop</span>
        <h3 class="section-title">满意度反馈</h3>
        <p class="section-copy">
          反馈页主要承担“农户闭环”的最后一步，用来记录对学习、识别、问答和方案推荐链路的使用感受。
          管理员仍可查看汇总统计，把满意度变化和自动评分结果结合起来看。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
        <el-button @click="resetForm">重置表单</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">当前模块</div>
        <div class="metric-value compact">{{ form.module }}</div>
        <div class="metric-note">建议围绕刚刚体验过的链路给出更具体的反馈。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">整体满意度</div>
        <div class="metric-value">{{ form.overallScore || '—' }}</div>
        <div class="metric-note">1 到 5 分，5 分表示当前体验非常满意。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">准确性</div>
        <div class="metric-value">{{ form.accuracyScore || '—' }}</div>
        <div class="metric-note">用于辅助判断识别、问答和推荐结果是否足够可信。</div>
      </article>
      <article v-if="canViewStats" class="metric-card">
        <div class="metric-label">累计反馈</div>
        <div class="metric-value">{{ stats?.total ?? 0 }}</div>
        <div class="metric-note">管理员可结合最新反馈和评测结果一起看平台体验走势。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card form-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">提交本次体验反馈</h3>
            <p class="section-copy">建议先完整走一遍农户链路或门店链路，再按模块给出评分和补充说明。</p>
          </div>
        </header>

        <el-form label-position="top" class="feedback-form">
          <el-form-item label="反馈模块">
            <el-select v-model="form.module" placeholder="请选择反馈模块">
              <el-option v-for="option in moduleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>

          <div class="score-grid">
            <el-form-item label="整体满意度">
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
              placeholder="可以补充说明哪里最顺、哪里最需要改进，或者哪一步最适合答辩展示。"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
            <span>建议在一次完整体验结束后填写，这样反馈会更有参考价值。</span>
          </div>
        </el-form>
      </article>

      <article v-if="canViewStats" class="soft-card stats-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">反馈汇总</h3>
            <p class="section-copy">管理员可在这里快速查看整体满意度走势、模块分布和最近反馈内容。</p>
          </div>
        </header>

        <section class="metric-grid compact-grid">
          <article class="metric-card compact-card">
            <div class="metric-label">问卷总数</div>
            <div class="metric-value">{{ stats?.total ?? 0 }}</div>
          </article>
          <article class="metric-card compact-card">
            <div class="metric-label">整体均分</div>
            <div class="metric-value">{{ stats?.avgOverallScore ?? '-' }}</div>
          </article>
          <article class="metric-card compact-card">
            <div class="metric-label">准确性均分</div>
            <div class="metric-value">{{ stats?.avgAccuracyScore ?? '-' }}</div>
          </article>
          <article class="metric-card compact-card">
            <div class="metric-label">实用性均分</div>
            <div class="metric-value">{{ stats?.avgPracticalityScore ?? '-' }}</div>
          </article>
        </section>

        <div class="module-stats">
          <div v-for="item in stats?.byModule ?? []" :key="item.module" class="module-stat">
            <strong>{{ item.module }}</strong>
            <span>{{ item.count }} 份反馈</span>
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
              <span>总体 {{ item.overallScore }}</span>
              <span>准 {{ item.accuracyScore }}</span>
              <span>实 {{ item.practicalityScore }}</span>
              <span>流 {{ item.fluencyScore }}</span>
            </div>
            <p>{{ item.comment || '本条反馈未填写补充意见。' }}</p>
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
  { label: '整体验收', value: '整体验收' },
  { label: '学习课堂', value: '学习课堂' },
  { label: '病害识别', value: '病害识别' },
  { label: '智能问答', value: '智能问答' },
  { label: '解决方案', value: '解决方案' },
  { label: '我的求助', value: '我的求助' }
]

const createDefaultForm = () => ({
  module: '',
  overallScore: 0,
  accuracyScore: 0,
  practicalityScore: 0,
  fluencyScore: 0,
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
  } catch {
    ElMessage.error('加载反馈统计失败。')
  }
}

const submitFeedback = async () => {
  if (!form.module) {
    ElMessage.warning('请先选择本次反馈的模块。')
    return
  }
  const scores = [form.overallScore, form.accuracyScore, form.practicalityScore, form.fluencyScore]
  if (scores.some((score) => !score || score <= 0)) {
    ElMessage.warning('请为每个维度完成评分（1 到 5 分）。')
    return
  }
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
    ElMessage.success('满意度反馈已提交，感谢你的补充。')
    resetForm()
    await loadStats()
  } catch {
    ElMessage.error('提交反馈失败，请稍后再试。')
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
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 18px;
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
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions,
.content-grid {
  display: grid;
  gap: 18px;
}

.content-grid {
  grid-template-columns: minmax(0, 1fr) 420px;
}

.panel-header {
  margin-bottom: 18px;
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

.form-actions span,
.recent-head span,
.recent-scores span,
.recent-card p {
  color: var(--ink-soft);
}

.compact {
  font-size: 20px;
  line-height: 1.45;
}

.compact-grid {
  margin-bottom: 18px;
}

.compact-card {
  padding: 16px;
}

.module-stats,
.recent-list {
  display: grid;
  gap: 12px;
}

.module-stat,
.recent-card {
  padding: 16px 18px;
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

.recent-card p {
  margin: 10px 0 0;
  line-height: 1.75;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid,
  .score-grid {
    grid-template-columns: 1fr;
  }
}
</style>
