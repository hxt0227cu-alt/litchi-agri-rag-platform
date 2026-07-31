<template>
  <div class="page-shell inbox-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Shop Inbox</span>
        <h3 class="section-title">待处理求助</h3>
        <p class="section-copy">
          门店在这里统一接收农户提交的求助意向，并把状态推进为“待处理、已联系、已完成”。
          页面结构尽量直观，方便答辩时直接演示门店如何承接农户方案选择后的协同动作。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goTo('/shop/plans')">去看方案库</el-button>
        <el-button @click="goTo('/shop/trends')">查看高频病症</el-button>
        <el-button :loading="loading" @click="loadRecords">刷新收件箱</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">待处理</div>
        <div class="metric-value">{{ pendingCount }}</div>
        <div class="metric-note">刚进入门店的求助记录，建议优先查看并尽快联系农户。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已联系</div>
        <div class="metric-value">{{ contactedCount }}</div>
        <div class="metric-note">已经完成初次跟进，但还没有闭环的求助记录。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已完成</div>
        <div class="metric-value">{{ completedCount }}</div>
        <div class="metric-note">已完成沟通和处理，可作为协同闭环示例进行展示。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">求助总量</div>
        <div class="metric-value">{{ total }}</div>
        <div class="metric-note">全部统计来自农户真实提交的求助意向，而不是浏览点击数据。</div>
      </article>
    </section>

    <section class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">门店收件箱</h3>
          <p class="section-copy">每条记录都包含病症标签、农户说明、联系方式和当前状态，可直接更新协同进度。</p>
        </div>
      </header>

      <el-empty v-if="!records.length && !loading" description="当前没有待处理求助，新的求助意向会在这里自动汇总。" />

      <div v-else class="record-list">
        <article v-for="item in records" :key="item.id" class="record-card">
          <div class="record-head">
            <div>
              <span class="card-kicker">{{ item.diseaseTag }} / {{ item.stageTag || '阶段待补充' }}</span>
              <strong>{{ item.planTitle }}</strong>
              <p>{{ item.farmerUsername }} · {{ item.shopName }}</p>
            </div>

            <el-select v-model="draftStatus[item.id]" class="status-select">
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>

          <dl class="record-grid">
            <div>
              <dt>联系电话</dt>
              <dd>{{ item.phone || '未填写' }}</dd>
            </div>
            <div>
              <dt>微信</dt>
              <dd>{{ item.wechat || '未填写' }}</dd>
            </div>
            <div>
              <dt>提交时间</dt>
              <dd>{{ formatDate(item.createdAt) }}</dd>
            </div>
            <div>
              <dt>当前状态</dt>
              <dd>{{ statusLabel(item.status) }}</dd>
            </div>
          </dl>

          <div class="question-box">
            <span>农户补充说明</span>
            <p>{{ item.question || '当前未填写额外说明，可先根据病症标签和方案标题与农户沟通。' }}</p>
          </div>

          <div class="action-row">
            <el-button type="primary" :loading="updatingId === item.id" @click="updateStatus(item.id)">
              更新状态
            </el-button>
          </div>
        </article>
      </div>

      <div class="pagination-row" style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadRecords"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { consultationAPI, type ConsultationRecord } from '@/api'
import { CONSULTATION_STATUS_OPTIONS } from '@/config/platform'

const router = useRouter()
const loading = ref(false)
const updatingId = ref('')
const records = ref<ConsultationRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const draftStatus = reactive<Record<string, ConsultationRecord['status']>>({})
const statusOptions = CONSULTATION_STATUS_OPTIONS

const pendingCount = computed(() => records.value.filter(item => item.status === 'pending').length)
const contactedCount = computed(() => records.value.filter(item => item.status === 'contacted').length)
const completedCount = computed(() => records.value.filter(item => item.status === 'completed').length)

const goTo = (path: string) => {
  router.push(path)
}

const statusLabel = (status: ConsultationRecord['status']) => {
  switch (status) {
    case 'pending':
      return '待处理'
    case 'contacted':
      return '已联系'
    case 'completed':
      return '已完成'
    default:
      return status
  }
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

const loadRecords = async () => {
  loading.value = true
  try {
    const response = await consultationAPI.inbox(page.value, size.value)
    records.value = response.data.items
    total.value = response.data.total
    for (const item of response.data.items) {
      draftStatus[item.id] = item.status
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载待处理求助失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

const updateStatus = async (id: string) => {
  updatingId.value = id
  try {
    await consultationAPI.updateStatus(id, {
      status: draftStatus[id] ?? 'pending'
    })
    ElMessage.success('求助状态已更新。')
    await loadRecords()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '更新求助状态失败。')
  } finally {
    updatingId.value = ''
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.inbox-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 14px;
}

.hero-kicker,
.card-kicker {
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

.hero-actions,
.record-list {
  display: grid;
  gap: 14px;
}

.panel-header {
  margin-bottom: 18px;
}

.record-card {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 22px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.82);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.record-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(47, 106, 89, 0.16);
}

.record-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.record-head strong {
  display: block;
  margin-top: 10px;
  color: var(--ink-strong);
  font-size: 22px;
}

.record-head p,
.record-grid dt,
.question-box span,
.question-box p {
  color: var(--ink-soft);
}

.status-select {
  width: 180px;
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.record-grid dd {
  margin: 8px 0 0;
  color: var(--ink-strong);
}

.question-box {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(248, 246, 239, 0.92);
}

.question-box p {
  margin: 8px 0 0;
  line-height: 1.75;
}

.action-row {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1180px) {
  .hero,
  .record-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .record-head {
    flex-direction: column;
  }

  .status-select {
    width: 100%;
  }
}
</style>
