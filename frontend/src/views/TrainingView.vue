<template>
  <div class="training-page page-shell">
    <section class="hero soft-card">
      <div>
        <h3 class="section-title">技术员培训课堂</h3>
        <p class="section-copy">把平台样例文档、推荐问题和演示流程整合成可直接讲解的课堂脚本，方便培训与答辩演示。</p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goToOverview">回到系统总览</el-button>
        <el-button @click="loadTrainingAssets">刷新课堂资料</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">课堂模块</div>
        <div class="metric-value">{{ modules.length }}</div>
        <div class="metric-note">覆盖巡园、防病、图谱讲解与现场演示。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">参考文档</div>
        <div class="metric-value">{{ overview?.documents.samples.length ?? 0 }}</div>
        <div class="metric-note">直接引用平台样例知识文档作为课堂材料。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">推荐问题</div>
        <div class="metric-value">{{ overview?.suggestedQuestions.length ?? 0 }}</div>
        <div class="metric-note">课堂上可直接跳转到问答页进行演示。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">讲解流程</div>
        <div class="metric-value">{{ overview?.demoFlow.length ?? 0 }}</div>
        <div class="metric-note">适合培训和答辩的标准讲解顺序。</div>
      </article>
    </section>

    <section class="module-grid">
      <article v-for="module in modules" :key="module.title" class="soft-card module-card">
        <div class="module-head">
          <div>
            <span class="module-kicker">{{ module.kicker }}</span>
            <h3>{{ module.title }}</h3>
          </div>
          <el-tag effect="plain">{{ module.audience }}</el-tag>
        </div>

        <p class="module-copy">{{ module.objective }}</p>

        <ul class="bullet-list">
          <li v-for="item in module.highlights" :key="item">{{ item }}</li>
        </ul>

        <div class="module-actions">
          <el-button type="primary" @click="openRoute(module.route, module.question)">进入模块</el-button>
          <span>{{ module.actionHint }}</span>
        </div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">推荐演示问题</h3>
            <p class="section-copy">课堂讲解时可以直接点击问题跳到问答页，减少现场输入成本。</p>
          </div>
        </header>

        <div class="question-list">
          <button
            v-for="question in overview?.suggestedQuestions ?? []"
            :key="question"
            type="button"
            class="question-card"
            @click="openRoute('/chat', question)"
          >
            <strong>{{ question }}</strong>
            <span>点击后自动带入智能问答页</span>
          </button>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">课堂资料索引</h3>
            <p class="section-copy">建议先讲文档依据，再讲问答与图谱联动，最后展示识别能力。</p>
          </div>
        </header>

        <div class="doc-list">
          <article v-for="sample in overview?.documents.samples ?? []" :key="sample.name" class="doc-card">
            <strong>{{ sample.title }}</strong>
            <span>{{ sample.name }}</span>
            <p>{{ sample.summary }}</p>
          </article>
        </div>
      </article>
    </section>

    <section class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">标准讲解流程</h3>
          <p class="section-copy">适合技术员培训、现场演示和答辩串讲。</p>
        </div>
      </header>

      <ol class="flow-list">
        <li v-for="(step, index) in overview?.demoFlow ?? []" :key="step">
          <span class="flow-index">{{ index + 1 }}</span>
          <span>{{ step }}</span>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { systemAPI, type SystemOverviewResponse } from '@/api'

type TrainingModule = {
  kicker: string
  title: string
  audience: string
  objective: string
  highlights: string[]
  route: string
  question?: string
  actionHint: string
}

const router = useRouter()
const overview = ref<SystemOverviewResponse | null>(null)

const modules: TrainingModule[] = [
  {
    kicker: '巡园培训',
    title: '病害巡园与问题识别',
    audience: '技术员',
    objective: '围绕雨季巡园、症状判断和病虫害高发节点组织课堂讲解。',
    highlights: ['先讲高温高湿和连阴雨等诱因', '再讲炭疽病与霜疫霉病的典型区别', '最后衔接到识别页做样图演示'],
    route: '/diagnosis',
    actionHint: '适合讲“发现问题”这一步。'
  },
  {
    kicker: '问答演练',
    title: '知识问答与来源追溯',
    audience: '技术员',
    objective: '展示平台如何基于文档切片和知识图谱生成可追溯回答。',
    highlights: ['优先使用推荐问题快速演示', '重点展示来源卡片与图谱命中实体', '说明离线模式下也能提供保障回答'],
    route: '/chat',
    question: '荔枝炭疽病在雨季怎么防治？',
    actionHint: '适合讲“给出解释”这一步。'
  },
  {
    kicker: '图谱串讲',
    title: '品种、病害与技术关系串讲',
    audience: '技术员',
    objective: '把桂味、病害、药剂和管理技术串成一条完整知识链路。',
    highlights: ['从桂味出发看关联病害', '点击实体查看关系详情', '跳转相关实体讲清“品种-病害-技术-药剂”逻辑'],
    route: '/knowledge',
    actionHint: '适合讲“知识组织方式”这一步。'
  },
  {
    kicker: '销售协同',
    title: '农资协同与用药建议',
    audience: '技术员 / 店主',
    objective: '让技术员可以结合用药指南给出更标准的门店建议。',
    highlights: ['区分保护性用药和发病初期处理', '强调安全间隔期与轮换用药', '衔接门店咨询和现场答复话术'],
    route: '/guide',
    actionHint: '适合讲“落到处置建议”这一步。'
  }
]

const loadTrainingAssets = async () => {
  try {
    const response = await systemAPI.overview()
    overview.value = response.data
  } catch (error) {
    ElMessage.error('加载培训课堂资料失败，请检查后端服务。')
  }
}

const openRoute = (path: string, question?: string) => {
  router.push(
    question
      ? {
          path,
          query: {
            q: question
          }
        }
      : { path }
  )
}

const goToOverview = () => {
  router.push('/overview')
}

onMounted(() => {
  loadTrainingAssets()
})
</script>

<style scoped>
.training-page {
  gap: 18px;
}

.hero,
.panel,
.module-card {
  padding: 22px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.hero-actions,
.module-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.module-grid,
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.module-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.module-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
}

.module-head h3 {
  margin: 0;
  color: var(--ink-strong);
}

.module-copy {
  margin: 16px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.bullet-list {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
  padding-left: 18px;
  color: var(--ink-strong);
}

.module-actions {
  margin-top: 18px;
}

.module-actions span {
  color: var(--ink-soft);
  font-size: 13px;
}

.panel-header {
  margin-bottom: 18px;
}

.question-list,
.doc-list {
  display: grid;
  gap: 12px;
}

.question-card,
.doc-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(47, 106, 89, 0.08);
  background: rgba(255, 255, 255, 0.82);
  text-align: left;
}

.question-card {
  cursor: pointer;
}

.question-card strong,
.doc-card strong {
  color: var(--ink-strong);
}

.question-card span,
.doc-card span,
.doc-card p {
  color: var(--ink-soft);
}

.doc-card p {
  margin: 10px 0 0;
  line-height: 1.7;
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

@media (max-width: 1080px) {
  .hero,
  .module-grid,
  .content-grid {
    grid-template-columns: 1fr;
    display: grid;
  }

  .hero {
    align-items: flex-start;
  }
}
</style>
