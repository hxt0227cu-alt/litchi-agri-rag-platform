<template>
  <div class="page-shell farmer-page">
    <section class="hero glass-card">
      <div class="hero-grid">
        <div class="hero-copy">
          <span class="hero-kicker">Farmer Workflow</span>
          <h3 class="section-title">先判断病症，再比较方案，最后把求助交给门店跟进</h3>

          <div class="hero-pills">
            <span v-for="item in heroPills" :key="item" class="hero-pill">{{ item }}</span>
          </div>

          <div class="hero-actions">
            <el-button type="primary" size="large" @click="goTo('/training')">进入学习课堂</el-button>
            <el-button size="large" @click="goTo('/diagnosis')">去做病害识别</el-button>
            <el-button size="large" @click="goTo('/chat')">去智能问答</el-button>
          </div>

          <article class="journey-tip">
            <strong>推荐起点</strong>
            <p>如果暂时还拿不准病症，先完成学习课堂或病害识别，再去看门店方案，体验会更顺。</p>
          </article>
        </div>

        <LitchiHero3D interactive @navigate="goTo" />
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">学习模块</div>
        <div class="metric-value">{{ classroomModules.length }}</div>
        <div class="metric-note">固定覆盖拍照识别、雨季管理、安全边界和如何看懂 AI 建议。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">我的求助</div>
        <div class="metric-value">{{ consultations.length }}</div>
        <div class="metric-note">已提交给门店的求助记录会在这里持续显示状态变化。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">推荐提问</div>
        <div class="metric-value">{{ suggestedQuestions.length }}</div>
        <div class="metric-note">可直接带入智能问答页，减少农户临时组织问题的成本。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">待跟进求助</div>
        <div class="metric-value">{{ pendingCount }}</div>
        <div class="metric-note">求助提交后由门店继续跟进，状态会在“我的求助”中同步刷新。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">学习课堂</h3>
            <p class="section-copy">先掌握识别和安全边界，再进入问答和方案选择，链路会更稳。</p>
          </div>
        </header>

        <div class="module-list">
          <article v-for="module in classroomModules" :key="module.title" class="module-card">
            <strong>{{ module.title }}</strong>
            <p>{{ module.copy }}</p>
          </article>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">推荐提问</h3>
            <p class="section-copy">点击任意问题可直接跳到智能问答页并自动带入，适合演示快速开始。</p>
          </div>
        </header>

        <div class="question-list">
          <button
            v-for="question in suggestedQuestions"
            :key="question"
            class="question-card"
            type="button"
            @click="askQuestion(question)"
          >
            {{ question }}
          </button>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">农户协同链路</h3>
            <p class="section-copy">
              这条链路对应答辩时最核心的“农户发起问题，门店提供方案，管理员优化 AI”。
            </p>
          </div>
        </header>

        <ol class="flow-list">
          <li v-for="step in workflow" :key="step">{{ step }}</li>
        </ol>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { consultationAPI, systemAPI } from '@/api'
const LitchiHero3D = defineAsyncComponent(() => import('@/components/LitchiHero3D.vue'))

const router = useRouter()
const consultations = ref<Array<{ status: string }>>([])
const suggestedQuestions = ref<string[]>([
  '荔枝炭疽病在雨季怎么防治？',
  '霜疫霉病和煤烟病有什么区别？',
  '拍照识别时应该拍哪些位置？'
])

const heroPills = ['先学习判断', '识别带病症标签', '方案可直接提交求助']

const classroomModules = [
  { title: '常见病症入门', copy: '认识荔枝常见病害与高发场景，先建立基础判断。' },
  { title: '如何正确拍照识别', copy: '优先拍清果面、叶面和病斑边缘，避免逆光与过远拍摄。' },
  { title: '雨季管理与安全用药边界', copy: '理解排水、通风、复查和安全间隔期的基本边界。' },
  { title: '如何看懂 AI 建议', copy: '区分原因、建议和风险提醒，不把单条建议当成处方。' },
  { title: '如何选择门店方案', copy: '重点看适用病症、风险提醒、推荐原因和门店信息。' }
]

const workflow = [
  '进入学习课堂，先了解病症和拍照识别要点。',
  '在病害识别或智能问答中判断当前问题属于哪一类病症。',
  '进入解决方案页，对比推荐门店方案、风险提醒和推荐原因。',
  '选择合适方案后提交求助意向，由门店继续跟进。',
  '在我的求助和满意度反馈中查看状态并补充使用感受。'
]

const pendingCount = computed(
  () => consultations.value.filter(item => item.status === 'pending' || item.status === 'contacted').length
)

const goTo = (path: string) => {
  router.push(path)
}

const askQuestion = (question: string) => {
  router.push({
    path: '/chat',
    query: {
      q: question
    }
  })
}

const loadData = async () => {
  try {
    const [overviewResponse, consultationsResponse] = await Promise.all([
      systemAPI.overview(),
      consultationAPI.my()
    ])
    consultations.value = consultationsResponse.data.items
    suggestedQuestions.value = overviewResponse.data.suggestedQuestions
  } catch {
    consultations.value = []
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.farmer-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  gap: 18px;
  overflow: hidden;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 540px);
  gap: 28px;
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

.hero-pills {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-pill {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.08);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.hero-actions,
.content-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.journey-tip {
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(34, 53, 47, 0.08);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.36);
}

.journey-tip strong {
  color: var(--ink-strong);
}

.journey-tip p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.panel-header {
  margin-bottom: 18px;
}

.module-list,
.question-list {
  display: grid;
  gap: 12px;
}

.module-card,
.question-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.78);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.module-card:hover,
.question-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(242, 140, 40, 0.18);
}

.module-card p,
.question-card {
  color: var(--ink-soft);
  line-height: 1.7;
}

.question-card {
  cursor: pointer;
  text-align: left;
}

.flow-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding-left: 18px;
  color: var(--ink-strong);
  line-height: 1.8;
}

@media (max-width: 1180px) {
  .hero-grid,
  .hero-actions,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
