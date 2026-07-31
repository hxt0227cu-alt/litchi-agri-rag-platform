<template>
  <div class="page-shell solutions-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Recommended Plans</span>
        <h3 class="section-title">先确认病症标签，再查看推荐门店方案</h3>
        <p class="section-copy">
          解决方案页统一展示适用病症、方案摘要、风险提醒、门店基础信息和推荐原因，并支持农户直接提交求助意向。
        </p>

        <div class="hero-signals">
          <article class="hero-signal-card">
            <span>当前病症</span>
            <strong>{{ selectedDiseaseTag || '待确认' }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>问题描述</span>
            <strong>{{ question || '未提供' }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>推荐数量</span>
            <strong>{{ loading ? '加载中' : recommendations.length }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-actions">
        <el-button v-if="needsConfirmation" type="primary" @click="jumpToDiagnosis">先去病害识别</el-button>
        <el-button v-if="needsConfirmation" @click="jumpToChat">先去智能问答</el-button>
        <template v-else>
          <el-button type="primary" @click="loadRecommendations" :loading="loading">刷新推荐</el-button>
          <el-button @click="resetConfirmation">重新确认标签</el-button>
        </template>
      </div>
    </section>

    <section v-if="needsConfirmation" class="soft-card confirm-panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">确认病症标签</h3>
          <p class="section-copy">从智能问答进入时，先确认病症标签，再进入推荐列表，解释会更完整。</p>
        </div>
      </header>

      <div class="pill-row">
        <button
          v-for="tag in diseaseTagOptions"
          :key="tag"
          class="chip-button"
          type="button"
          @click="confirmDiseaseTag(tag)"
        >
          {{ tag }}
        </button>
      </div>
    </section>

    <section v-else class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">推荐门店方案</h3>
          <p class="section-copy">
            病症标签确认后，将按病症匹配度、方案质量、门店响应率和历史满意度综合排序。
          </p>
        </div>
      </header>

      <el-empty
        v-if="!loading && !recommendations.length"
        description="暂时没有匹配方案，可以稍后再试，或先补充病症信息后重新进入。"
      />

      <div v-else class="recommendation-list">
        <article v-for="plan in recommendations" :key="plan.planId" class="plan-card">
          <div class="plan-head">
            <div>
              <strong>{{ plan.title }}</strong>
              <p>{{ plan.diseaseTag }} · {{ plan.stageTag }} · {{ plan.inventoryStatus }}</p>
            </div>
            <div class="score-badge">{{ plan.score.toFixed(1) }}</div>
          </div>

          <p class="summary">{{ plan.summary }}</p>

          <div class="section-block">
            <span class="section-label">推荐原因</span>
            <div class="pill-row">
              <span v-for="tag in plan.reasonTags" :key="tag" class="reason-pill">{{ tag }}</span>
            </div>
          </div>

          <div class="section-block">
            <span class="section-label">建议用品</span>
            <div class="pill-row">
              <span v-for="product in plan.products" :key="product" class="neutral-pill">{{ product }}</span>
            </div>
          </div>

          <div class="section-block">
            <span class="section-label">使用提示</span>
            <ul class="detail-list">
              <li v-for="tip in plan.usageTips" :key="tip">{{ tip }}</li>
            </ul>
          </div>

          <div class="section-block">
            <span class="section-label">风险提醒</span>
            <ul class="detail-list warning-list">
              <li v-for="risk in plan.riskNotes" :key="risk">{{ risk }}</li>
            </ul>
          </div>

          <div class="section-block">
            <span class="section-label">门店信息</span>
            <dl class="shop-grid">
              <div>
                <dt>门店</dt>
                <dd>{{ plan.shopName }}</dd>
              </div>
              <div>
                <dt>联系人</dt>
                <dd>{{ plan.contactName }}</dd>
              </div>
              <div>
                <dt>联系电话</dt>
                <dd>{{ plan.phone || '未填写' }}</dd>
              </div>
              <div>
                <dt>微信</dt>
                <dd>{{ plan.wechat || '未填写' }}</dd>
              </div>
              <div>
                <dt>服务区域</dt>
                <dd>{{ plan.serviceArea || '未填写' }}</dd>
              </div>
              <div>
                <dt>门店评分</dt>
                <dd>{{ typeof plan.rating === 'number' ? plan.rating.toFixed(1) : '暂无' }}</dd>
              </div>
            </dl>
          </div>

          <div class="plan-actions">
            <el-button v-if="canSubmitConsultation" type="primary" @click="submitConsultation(plan)">
              提交求助
            </el-button>
            <span v-else>当前账号可以查看推荐结果，但只有农户可以提交求助意向。</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { consultationAPI, recommendationAPI, type RecommendedPlan } from '@/api'
import { DISEASE_TAG_OPTIONS } from '@/config/platform'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const recommendations = ref<RecommendedPlan[]>([])
const selectedDiseaseTag = ref('')
const stageTag = ref('')
const question = ref('')
const diseaseTagOptions = DISEASE_TAG_OPTIONS
let lastRequestId = 0

const canSubmitConsultation = computed(() => authStore.user?.role === 'farmer')
const needsConfirmation = computed(() => !selectedDiseaseTag.value.trim())

const loadFromRoute = () => {
  selectedDiseaseTag.value = typeof route.query.diseaseTag === 'string' ? route.query.diseaseTag.trim() : ''
  stageTag.value = typeof route.query.stageTag === 'string' ? route.query.stageTag.trim() : ''
  question.value =
    typeof route.query.question === 'string'
      ? route.query.question.trim()
      : typeof route.query.q === 'string'
        ? route.query.q.trim()
        : ''
}

const loadRecommendations = async () => {
  if (!selectedDiseaseTag.value && !question.value) {
    recommendations.value = []
    return
  }

  const requestId = ++lastRequestId
  loading.value = true
  try {
    const response = await recommendationAPI.list({
      diseaseTag: selectedDiseaseTag.value || undefined,
      stageTag: stageTag.value || undefined,
      query: question.value || undefined
    })
    if (requestId !== lastRequestId) return
    recommendations.value = response.data
  } catch (error: any) {
    if (requestId !== lastRequestId) return
    recommendations.value = []
    ElMessage.error(error?.response?.data?.message ?? '加载解决方案失败，请稍后重试。')
  } finally {
    if (requestId === lastRequestId) {
      loading.value = false
    }
  }
}

const confirmDiseaseTag = async (tag: string) => {
  selectedDiseaseTag.value = tag
  await router.replace({
    path: '/solutions',
    query: {
      ...(question.value ? { question: question.value } : {}),
      diseaseTag: selectedDiseaseTag.value,
      ...(stageTag.value ? { stageTag: stageTag.value } : {})
    }
  })
  loadRecommendations()
}

const resetConfirmation = async () => {
  selectedDiseaseTag.value = ''
  recommendations.value = []
  await router.replace({
    path: '/solutions',
    query: {
      ...(question.value ? { question: question.value } : {}),
      ...(stageTag.value ? { stageTag: stageTag.value } : {})
    }
  })
}

const submitConsultation = async (plan: RecommendedPlan) => {
  if (!canSubmitConsultation.value) {
    ElMessage.warning('当前账号只能查看推荐结果，只有农户可以提交求助。')
    return
  }

  try {
    await consultationAPI.create({
      planId: plan.planId,
      diseaseTag: selectedDiseaseTag.value || plan.diseaseTag,
      stageTag: stageTag.value || plan.stageTag,
      question: question.value,
      reasonTags: plan.reasonTags
    })
    ElMessage.success('求助意向已提交，正在跳转到“我的求助”。')
    router.push('/consultations/my')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '提交求助失败，请稍后重试。')
  }
}

const jumpToDiagnosis = () => {
  router.push('/diagnosis')
}

const jumpToChat = () => {
  router.push({
    path: '/chat',
    query: question.value ? { q: question.value } : undefined
  })
}

watch(
  () => route.fullPath,
  () => {
    loadFromRoute()
    if (!needsConfirmation.value) {
      loadRecommendations()
    } else {
      recommendations.value = []
    }
  }
)

onMounted(() => {
  loadFromRoute()
  if (!needsConfirmation.value) {
    loadRecommendations()
  }
})
</script>

<style scoped>
.solutions-page {
  gap: 18px;
}

.hero,
.panel,
.confirm-panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
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
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-signals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-signal-card {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(34, 53, 47, 0.08);
}

.hero-signal-card span,
.section-label,
.shop-grid dt {
  color: var(--ink-soft);
  font-size: 13px;
}

.hero-signal-card strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-strong);
  font-size: 22px;
  line-height: 1.5;
}

.hero-actions {
  display: grid;
  gap: 14px;
}

.panel-header {
  margin-bottom: 18px;
}

.recommendation-list {
  display: grid;
  gap: 16px;
}

.plan-card {
  padding: 22px;
  border-radius: 22px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.82);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(242, 140, 40, 0.18);
}

.plan-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.plan-head strong {
  color: var(--ink-strong);
  font-size: 20px;
}

.plan-head p,
.summary {
  color: var(--ink-soft);
}

.score-badge {
  display: grid;
  place-items: center;
  min-width: 64px;
  height: 64px;
  border-radius: 20px;
  background: linear-gradient(145deg, #2f6a59, #1f4a3f);
  color: #fff8ec;
  font-size: 22px;
  font-weight: 800;
  box-shadow: 0 16px 28px rgba(31, 74, 63, 0.18);
}

.summary {
  margin: 14px 0 0;
  line-height: 1.8;
}

.section-block {
  margin-top: 18px;
  display: grid;
  gap: 10px;
}

.detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 18px;
  color: var(--ink-strong);
  line-height: 1.8;
}

.warning-list li {
  color: #9c4e0c;
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}

.shop-grid dd {
  margin: 8px 0 0;
  color: var(--ink-strong);
  line-height: 1.7;
}

.reason-pill,
.neutral-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.reason-pill {
  background: rgba(47, 106, 89, 0.08);
  color: var(--primary-deep);
}

.neutral-pill {
  background: rgba(34, 53, 47, 0.06);
  color: var(--ink-strong);
}

.plan-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 20px;
}

.plan-actions span {
  color: var(--ink-soft);
  font-size: 13px;
}

@media (max-width: 1180px) {
  .hero,
  .hero-signals,
  .shop-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .plan-head,
  .plan-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
