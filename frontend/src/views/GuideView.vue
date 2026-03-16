<template>
  <div class="guide-page page-shell">
    <section class="hero soft-card">
      <div>
        <h3 class="section-title">快配药与用药指南</h3>
        <p class="section-copy">面向农资店咨询和现场答复场景，按病害与虫害快速查看药剂建议、使用提醒和沟通要点。</p>
      </div>

      <div class="toolbar-actions">
        <el-input v-model="keyword" clearable placeholder="搜索病害、虫害或药剂，例如：炭疽病、咪鲜胺" />
        <el-select v-model="selectedCategory" placeholder="场景分类">
          <el-option label="全部场景" value="all" />
          <el-option label="病害" value="disease" />
          <el-option label="虫害" value="pest" />
          <el-option label="综合管理" value="general" />
        </el-select>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">指南条目</div>
        <div class="metric-value">{{ filteredCards.length }}</div>
        <div class="metric-note">覆盖炭疽病、霜疫霉病、蒂蛀虫与雨季管理场景。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">药剂建议</div>
        <div class="metric-value">{{ totalProducts }}</div>
        <div class="metric-note">用于快速筛选合适的门店答复方案。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">安全提醒</div>
        <div class="metric-value">3 类</div>
        <div class="metric-note">统一强调轮换用药、安全间隔期和标签说明。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">问答联动</div>
        <div class="metric-value">已接通</div>
        <div class="metric-note">遇到复杂问题可一键带问题跳转到智能问答页。</div>
      </article>
    </section>

    <section class="pill-row">
      <button v-for="tag in quickTags" :key="tag" class="chip-button" type="button" @click="keyword = tag">
        {{ tag }}
      </button>
    </section>

    <section class="guide-grid">
      <article v-for="card in filteredCards" :key="card.title" class="soft-card guide-card">
        <div class="card-head">
          <div>
            <span class="guide-kicker">{{ card.categoryLabel }}</span>
            <h3>{{ card.title }}</h3>
          </div>
          <el-tag effect="plain">{{ card.stage }}</el-tag>
        </div>

        <p class="card-copy">{{ card.summary }}</p>

        <div class="section-block">
          <strong>推荐药剂 / 处理要点</strong>
          <ul class="bullet-list">
            <li v-for="product in card.products" :key="product">{{ product }}</li>
          </ul>
        </div>

        <div class="section-block">
          <strong>门店沟通提醒</strong>
          <ul class="bullet-list">
            <li v-for="tip in card.tips" :key="tip">{{ tip }}</li>
          </ul>
        </div>

        <div class="section-block">
          <strong>风险提示</strong>
          <ul class="bullet-list">
            <li v-for="risk in card.risks" :key="risk">{{ risk }}</li>
          </ul>
        </div>

        <div class="card-actions">
          <el-button type="primary" @click="askQuestion(card.question)">进入智能问答</el-button>
          <span>{{ card.actionHint }}</span>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

type GuideCategory = 'disease' | 'pest' | 'general'

type GuideCard = {
  category: GuideCategory
  categoryLabel: string
  title: string
  stage: string
  summary: string
  products: string[]
  tips: string[]
  risks: string[]
  question: string
  actionHint: string
}

const router = useRouter()
const keyword = ref('')
const selectedCategory = ref<'all' | GuideCategory>('all')

const quickTags = ['炭疽病', '霜疫霉病', '蒂蛀虫', '咪鲜胺', '烯酰吗啉', '安全间隔期']

const cards: GuideCard[] = [
  {
    category: 'disease',
    categoryLabel: '病害',
    title: '炭疽病发病初期快配药建议',
    stage: '病害初发',
    summary: '适合回答“果面有褐斑、近期连阴雨，现在该用什么药”的门店咨询场景。',
    products: ['咪鲜胺：适合炭疽病等真菌性病害初期轮换使用', '苯醚甲环唑：可作为轮换方案的一部分', '结合清园与修剪，避免只卖药不讲管理'],
    tips: ['先问清是否为雨后高湿环境与果面褐斑', '提醒客户按标签和安全间隔期使用', '建议连续阴雨后增加复查频次'],
    risks: ['避免长期单一药剂导致效果下降', '不能忽视修剪和通风管理', '销售时不要脱离说明书自行放大剂量'],
    question: '炭疽病发病初期可以考虑哪些药剂？',
    actionHint: '适合门店快速答复炭疽病用户。'
  },
  {
    category: 'disease',
    categoryLabel: '病害',
    title: '霜疫霉病雨季管理建议',
    stage: '雨季高风险',
    summary: '适合回答“雨季病果带白霉层、扩散很快，该怎么处理”的咨询场景。',
    products: ['烯酰吗啉：可用于雨季前后的保护性喷施', '加强排水和清理病果，降低园内湿度', '重点覆盖花穗、幼果和湿度较高区域'],
    tips: ['优先确认是否有白色霉层和排水不良', '强调预防性处理比重症后补救更重要', '建议客户区分霜疫霉病和炭疽病的典型症状'],
    risks: ['不要把所有病斑都当成炭疽病', '药剂建议必须结合天气和物候期', '持续降雨时应把排水与巡园放在前面'],
    question: '霜疫霉病和炭疽病有什么区别？',
    actionHint: '适合雨季门店咨询与回访。'
  },
  {
    category: 'pest',
    categoryLabel: '虫害',
    title: '蒂蛀虫监测与防治建议',
    stage: '花果期到果期',
    summary: '适合回答“花穗和果梗有虫害迹象，应该怎么监测和处理”的咨询场景。',
    products: ['先讲监测窗口，再给出处理建议', '结合诱捕、清理虫果和人工巡查一起做', '防治建议要跟物候期和虫情监测结合'],
    tips: ['先问清是花期、幼果期还是近成熟期', '鼓励客户先拍照或带样复核', '如虫口高峰明显，建议同步联系技术员判断'],
    risks: ['不能只卖药不讲监测', '虫害管理过晚容易导致虫果和落果扩大', '忽视清园会影响下一轮虫源控制'],
    question: '蒂蛀虫高发期应该怎么监测和处理？',
    actionHint: '适合解释“为什么要先监测再处理”。'
  },
  {
    category: 'general',
    categoryLabel: '综合管理',
    title: '连续降雨后的综合处置优先级',
    stage: '综合管理',
    summary: '适合回答“最近连续降雨，门店应该怎么给客户排优先级”的综合场景。',
    products: ['先排水与通风，再复查病果病枝', '根据风险选择保护性或针对性用药', '复杂情况建议转到智能问答获取更完整说明'],
    tips: ['先问清品种、雨量和当前症状', '把排水、修剪、巡园和药剂建议串起来', '适合用作门店统一答复话术模板'],
    risks: ['不能跳过基础管理直接推药', '连续高湿下要强调复查而非一次性处理', '果实转色期更要注意安全间隔期'],
    question: '连续降雨后荔枝果园管理的优先级是什么？',
    actionHint: '适合做门店统一答复模板。'
  }
]

const filteredCards = computed(() => {
  const needle = keyword.value.trim().toLowerCase()

  return cards.filter(card => {
    const categoryMatched = selectedCategory.value === 'all' || card.category === selectedCategory.value
    if (!categoryMatched) {
      return false
    }

    if (!needle) {
      return true
    }

    const haystack = [card.title, card.summary, ...card.products, ...card.tips, ...card.risks].join(' ').toLowerCase()
    return haystack.includes(needle)
  })
})

const totalProducts = computed(() =>
  filteredCards.value.reduce((total, card) => total + card.products.length, 0)
)

const askQuestion = (question: string) => {
  router.push({
    path: '/chat',
    query: {
      q: question
    }
  })
}
</script>

<style scoped>
.guide-page {
  gap: 18px;
}

.hero,
.guide-card {
  padding: 22px;
}

.hero {
  display: grid;
  gap: 16px;
}

.toolbar-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 12px;
}

.guide-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.guide-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.card-head h3 {
  margin: 0;
  color: var(--ink-strong);
}

.card-copy {
  margin: 16px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.section-block {
  margin-top: 18px;
}

.section-block strong {
  color: var(--ink-strong);
}

.bullet-list {
  display: grid;
  gap: 10px;
  margin: 10px 0 0;
  padding-left: 18px;
  color: var(--ink-soft);
}

.card-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 20px;
}

.card-actions span {
  color: var(--ink-soft);
  font-size: 13px;
}

@media (max-width: 1080px) {
  .toolbar-actions,
  .guide-grid {
    grid-template-columns: 1fr;
  }
}
</style>
