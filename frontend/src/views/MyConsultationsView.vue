<template>
  <div class="page-shell consultations-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Farmer Requests</span>
        <h3 class="section-title">我的求助</h3>
        <p class="section-copy">
          这里集中展示农户已经提交给门店的求助记录，包括目标门店、选中方案、当前状态和推荐原因，
          方便在答辩时完整讲清“识别或问答之后，如何进入门店协同”。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goTo('/solutions')">继续看解决方案</el-button>
        <el-button @click="goTo('/chat')">返回智能问答</el-button>
        <el-button :loading="loading" @click="loadRecords">刷新记录</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">求助总数</div>
        <div class="metric-value">{{ total }}</div>
        <div class="metric-note">已经提交给门店并进入协同链路的全部记录。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">待处理</div>
        <div class="metric-value">{{ pendingCount }}</div>
        <div class="metric-note">门店尚未开始联系的求助记录，适合作为演示重点入口。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已联系</div>
        <div class="metric-value">{{ contactedCount }}</div>
        <div class="metric-note">已经由门店跟进，但还没有完成闭环的记录。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">已完成</div>
        <div class="metric-value">{{ completedCount }}</div>
        <div class="metric-note">可作为满意度反馈和协同结果说明的完成记录。</div>
      </article>
    </section>

    <section class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">求助记录列表</h3>
          <p class="section-copy">每条记录都保留病症标签、目标门店、方案标题、联系方式和推荐原因。</p>
        </div>
      </header>

      <el-empty
        v-if="!records.length && !loading"
        description="暂时还没有求助记录，可以先去病害识别或智能问答，再进入解决方案页提交求助。"
      />

      <div v-else class="record-list">
        <article v-for="item in records" :key="item.id" class="record-card">
          <div class="record-top">
            <div>
              <span class="card-kicker">{{ item.diseaseTag }} / {{ item.stageTag || '待确认阶段' }}</span>
              <strong>{{ item.planTitle }}</strong>
              <p>{{ item.shopName }} · {{ item.contactName || '门店联系人待补充' }}</p>
            </div>
            <el-tag :type="statusTagType(item.status)" effect="plain">
              {{ statusLabel(item.status) }}
            </el-tag>
          </div>

          <dl class="record-grid">
            <div>
              <dt>目标门店</dt>
              <dd>{{ item.shopName }}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd>{{ item.phone || '未填写' }}</dd>
            </div>
            <div>
              <dt>微信</dt>
              <dd>{{ item.wechat || '未填写' }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDate(item.createdAt) }}</dd>
            </div>
          </dl>

          <div class="question-box">
            <span>农户补充说明</span>
            <p>{{ item.question || '当前没有额外备注，门店会以方案和病症标签为主继续跟进。' }}</p>
          </div>

          <div class="pill-row">
            <span v-for="tag in item.reasonTags" :key="tag" class="reason-pill">{{ tag }}</span>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { consultationAPI, type ConsultationRecord } from '@/api'

const router = useRouter()
const records = ref<ConsultationRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const pendingCount = computed(() => records.value.filter(item => item.status === 'pending').length)
const contactedCount = computed(() => records.value.filter(item => item.status === 'contacted').length)
const completedCount = computed(() => records.value.filter(item => item.status === 'completed').length)

const loadRecords = async () => {
  loading.value = true
  try {
    const response = await consultationAPI.my(page.value, size.value)
    records.value = response.data.items
    total.value = response.data.total
  } finally {
    loading.value = false
  }
}

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

const statusTagType = (status: ConsultationRecord['status']) => {
  switch (status) {
    case 'pending':
      return 'warning'
    case 'contacted':
      return 'primary'
    case 'completed':
      return 'success'
    default:
      return 'info'
  }
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.consultations-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
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
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
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
  border-color: rgba(242, 140, 40, 0.18);
}

.record-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.record-top strong {
  display: block;
  margin-top: 10px;
  color: var(--ink-strong);
  font-size: 22px;
}

.record-top p,
.record-grid dt,
.question-box span,
.question-box p {
  color: var(--ink-soft);
}

.record-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.record-grid dt {
  font-size: 12px;
  letter-spacing: 0.04em;
}

.record-grid dd {
  margin: 8px 0 0;
  color: var(--ink-strong);
  line-height: 1.6;
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

.reason-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.08);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 1180px) {
  .hero,
  .record-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .record-top {
    flex-direction: column;
  }
}
</style>
