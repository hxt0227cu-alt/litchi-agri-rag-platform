<template>
  <div class="page-shell trends-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Trend Board</span>
        <h3 class="section-title">高频病症看板</h3>
        <p class="section-copy">
          看板完全按农户提交的求助意向做聚合，不把浏览和点击混入热度指标。
          这样既方便答辩说明，也更贴近门店真实备货和准备话术的工作场景。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goTo('/shop/plans')">完善方案库</el-button>
        <el-button @click="goTo('/shop/inbox')">查看待处理求助</el-button>
        <el-button :loading="loading" @click="loadTrends">刷新看板</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">病症排行第一</div>
        <div class="metric-value compact">{{ topTrend?.diseaseTag ?? '暂无数据' }}</div>
        <div class="metric-note">近期最值得优先准备方案、库存和答复口径的病症标签。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">最近热度</div>
        <div class="metric-value">{{ topTrend?.recentConsultations ?? 0 }}</div>
        <div class="metric-note">近 7 天求助数量，用于判断近期关注度是否快速上升。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">累计求助</div>
        <div class="metric-value">{{ topTrend?.totalConsultations ?? 0 }}</div>
        <div class="metric-note">当前第一病症标签在全部时间范围内的累计求助总数。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">最新触发时间</div>
        <div class="metric-value compact">{{ latestTime }}</div>
        <div class="metric-note">最后一次影响当前统计结果的求助提交时间。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card spotlight-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">门店备战建议</h3>
            <p class="section-copy">围绕当前热度第一的病症，快速决定先补方案、先备货还是先准备沟通话术。</p>
          </div>
        </header>

        <div class="spotlight-body">
          <strong>{{ topTrend?.diseaseTag ?? '暂无数据' }}</strong>
          <p>
            {{
              topTrend
                ? `最近 7 天新增 ${topTrend.recentConsultations} 条求助，累计 ${topTrend.totalConsultations} 条。建议优先检查该病症相关方案是否完整、库存是否充足。`
                : '当前还没有足够的求助数据，门店可先完善常见病症方案并等待数据积累。'
            }}
          </p>
        </div>
      </article>

      <article class="soft-card table-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">病症排行列表</h3>
            <p class="section-copy">便于快速查看每个病症标签的累计求助、近期热度和最近触发时间。</p>
          </div>
        </header>

        <el-empty v-if="!trends.length && !loading" description="当前还没有病症趋势数据，新的求助意向会自动汇总到这里。" />

        <el-table v-else :data="trends" class="trend-table">
          <el-table-column prop="diseaseTag" label="病症标签" min-width="180" />
          <el-table-column prop="recentConsultations" label="最近热度" width="120" />
          <el-table-column prop="totalConsultations" label="累计求助" width="120" />
          <el-table-column label="最近时间" min-width="180">
            <template #default="{ row }">
              {{ row.latestAt ? formatDate(row.latestAt) : '暂无数据' }}
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { shopAPI, type ShopTrend } from '@/api'

const router = useRouter()
const loading = ref(false)
const trends = ref<ShopTrend[]>([])

const topTrend = computed(() => trends.value[0] ?? null)
const latestTime = computed(() => (topTrend.value?.latestAt ? formatDate(topTrend.value.latestAt) : '暂无数据'))

const goTo = (path: string) => {
  router.push(path)
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

const loadTrends = async () => {
  loading.value = true
  try {
    const response = await shopAPI.trends()
    trends.value = response.data
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载病症趋势失败。')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTrends()
})
</script>

<style scoped>
.trends-page {
  gap: 18px;
}

.hero,
.spotlight-card,
.table-card {
  padding: 24px;
}

.hero,
.content-grid {
  display: grid;
  gap: 18px;
}

.hero {
  grid-template-columns: minmax(0, 1fr) 240px;
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
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions {
  display: grid;
  gap: 14px;
}

.content-grid {
  grid-template-columns: 320px minmax(0, 1fr);
}

.panel-header {
  margin-bottom: 18px;
}

.compact {
  font-size: 22px;
  line-height: 1.45;
}

.spotlight-body {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(31, 74, 63, 0.08), rgba(242, 140, 40, 0.08));
}

.spotlight-body strong {
  color: var(--ink-strong);
  font-size: 24px;
}

.spotlight-body p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.75;
}

.trend-table :deep(.el-table) {
  --el-table-border-color: rgba(34, 53, 47, 0.08);
  --el-table-header-bg-color: rgba(245, 244, 237, 0.82);
  background: transparent;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
