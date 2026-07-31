<template>
  <div class="page-shell shop-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Shop Workflow</span>
        <h3 class="section-title">维护门店方案，及时响应求助，并持续观察高频病症变化</h3>
        <p class="section-copy">
          门店工作台聚焦店铺资料、方案维护、求助跟进和趋势观察，不再承担知识文档管理职责。
        </p>

        <div class="hero-signals">
          <article class="hero-signal-card">
            <span>资料状态</span>
            <strong>{{ profile?.shopName ? '已配置' : '待完善' }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>启用方案</span>
            <strong>{{ activePlanCount }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>待处理求助</span>
            <strong>{{ pendingInboxCount }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-actions">
        <el-button type="primary" size="large" @click="goTo('/shop/plans')">维护配药方案</el-button>
        <el-button size="large" @click="goTo('/shop/inbox')">处理待办求助</el-button>
        <el-button size="large" @click="goTo('/shop/profile')">完善店铺资料</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">门店名称</div>
        <div class="metric-value compact">{{ profile?.shopName ?? '未设置' }}</div>
        <div class="metric-note">店铺资料会展示给农户，直接影响解决方案页的门店基础信息。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">启用方案</div>
        <div class="metric-value">{{ activePlanCount }}</div>
        <div class="metric-note">建议优先维护高频病症方案，并保持库存状态准确。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">待处理求助</div>
        <div class="metric-value">{{ pendingInboxCount }}</div>
        <div class="metric-note">农户提交求助后会进入待处理列表，联系状态需要及时更新。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">高频病症</div>
        <div class="metric-value compact">{{ topTrend?.diseaseTag ?? '暂无' }}</div>
        <div class="metric-note">根据农户求助意向聚合，反映近期门店最该提前准备的场景。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">最近待处理求助</h3>
            <p class="section-copy">优先跟进高频病症和刚提交的求助，能更稳定地拉高响应率。</p>
          </div>
        </header>

        <div class="item-list">
          <article v-for="item in inboxPreview" :key="item.id" class="list-card">
            <strong>{{ item.planTitle }}</strong>
            <p>{{ item.diseaseTag }} · {{ item.shopName }}</p>
            <span>{{ item.statusLabel }}</span>
          </article>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">近期高频病症</h3>
            <p class="section-copy">这里展示最近最热的病症标签，方便门店提前备货和准备沟通话术。</p>
          </div>
        </header>

        <div class="item-list">
          <article v-for="trend in trends.slice(0, 4)" :key="trend.diseaseTag" class="list-card">
            <strong>{{ trend.diseaseTag }}</strong>
            <p>最近求助 {{ trend.recentConsultations }} 条</p>
            <span>累计 {{ trend.totalConsultations }} 条</span>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { consultationAPI, shopAPI, type ConsultationRecord, type ShopTrend, type StoreProfile } from '@/api'

const router = useRouter()
const profile = ref<StoreProfile | null>(null)
const plans = ref<Array<{ active: boolean }>>([])
const inbox = ref<ConsultationRecord[]>([])
const trends = ref<ShopTrend[]>([])

const activePlanCount = computed(() => plans.value.filter(item => item.active).length)
const pendingInboxCount = computed(
  () => inbox.value.filter(item => item.status === 'pending' || item.status === 'contacted').length
)
const topTrend = computed(() => trends.value[0] ?? null)

const inboxPreview = computed(() =>
  inbox.value.slice(0, 4).map(item => ({
    ...item,
    statusLabel: item.status === 'pending' ? '待处理' : item.status === 'contacted' ? '已联系' : '已完成'
  }))
)

const goTo = (path: string) => {
  router.push(path)
}

const loadData = async () => {
  try {
    const [profileResponse, plansResponse, inboxResponse, trendsResponse] = await Promise.all([
      shopAPI.profile(),
      shopAPI.plans(),
      consultationAPI.inbox(),
      shopAPI.trends()
    ])
    profile.value = profileResponse.data
    plans.value = plansResponse.data.items
    inbox.value = inboxResponse.data.items
    trends.value = trendsResponse.data
  } catch {
    profile.value = null
    plans.value = []
    inbox.value = []
    trends.value = []
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.shop-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
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
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
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

.hero-signal-card span {
  color: var(--ink-soft);
  font-size: 13px;
}

.hero-signal-card strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-strong);
  font-size: 26px;
}

.hero-actions,
.content-grid {
  display: grid;
  gap: 14px;
}

.compact {
  font-size: 22px;
}

.panel-header {
  margin-bottom: 18px;
}

.item-list {
  display: grid;
  gap: 12px;
}

.list-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.78);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.list-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(47, 106, 89, 0.16);
}

.list-card p,
.list-card span {
  margin: 8px 0 0;
  color: var(--ink-soft);
}

@media (max-width: 1180px) {
  .hero,
  .hero-signals,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
