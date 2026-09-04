<template>
  <div class="history-page page-shell">
    <aside class="soft-card sessions-panel">
      <div class="panel-heading">
        <div>
          <h3 class="section-title">会话列表</h3>
          <p class="section-copy">查看同一账号下的历史会话与最近提问，作为咨询记录的辅助参考。</p>
        </div>
        <el-button @click="loadSessions">刷新</el-button>
      </div>

      <div class="session-list" style="max-height: 60vh; overflow-y: auto; contain: layout paint;">
        <button
          v-for="session in displaySessions"
          :key="session.sessionId"
          type="button"
          :class="['session-card', { active: session.sessionId === activeSessionId }]"
          @click="selectSession(session.sessionId)"
        >
          <strong>{{ session.title }}</strong>
          <span v-if="session.lastMessage && session.lastMessage !== session.title">{{ session.lastMessage }}</span>
          <small>{{ formatDate(session.updatedAt) }} · {{ session.messageCount }} 条消息</small>
        </button>
      </div>
    </aside>

    <section class="history-main">
      <article class="glass-card history-hero">
        <div>
          <span class="hero-kicker">History Trace</span>
          <h3 class="section-title">咨询记录</h3>
          <p class="section-copy">
            选中某个会话后，可以查看历史提问、系统回答、来源片段和图谱命中结果。
            这页适合补充说明系统为什么会给出某条回答。
          </p>
        </div>
        <el-button type="primary" @click="openInChat" :disabled="!activeSessionId">回到智能问答</el-button>
      </article>

      <article class="soft-card history-list-card">
        <el-empty
          v-if="!historyItems.length && !loadingHistory"
          description="暂时没有历史记录，可以先去智能问答页发起一次提问。"
        />

        <div v-else class="history-list" style="max-height: 60vh; overflow-y: auto; contain: layout paint;">
          <div v-for="item in historyItems" :key="item.id" class="history-item">
            <div class="history-question">
              <span>用户提问</span>
              <strong>{{ item.question }}</strong>
            </div>

            <div class="history-answer">
              <span>系统回答</span>
              <p>{{ item.answer }}</p>
            </div>

            <div v-if="item.sources?.length" class="history-block">
              <h4>来源片段</h4>
              <div class="source-list">
                <article
                  v-for="source in item.sources"
                  :key="`${item.id}-${source.source}-${source.page}`"
                  class="source-card"
                >
                  <strong>{{ source.source }}</strong>
                  <span>{{ source.content }}</span>
                </article>
              </div>
            </div>

            <div v-if="item.knowledgeGraph?.entities?.length" class="history-block">
              <h4>图谱命中</h4>
              <div class="pill-row">
                <span
                  v-for="entity in item.knowledgeGraph.entities"
                  :key="`${item.id}-${entity.id ?? entity.label}-${String(entity.properties?.name ?? '')}`"
                  class="entity-pill"
                >
                  {{ entity.properties?.name ?? entity.label }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { chatAPI, type ChatHistoryItem, type ChatSessionItem } from '@/api'
import { PAGE_SIZE } from '@/config/constants'

const router = useRouter()
const sessions = ref<ChatSessionItem[]>([])
const historyItems = ref<ChatHistoryItem[]>([])
const activeSessionId = ref('')
const loadingHistory = ref(false)

// 过滤明显异常的并发/压测残留会话（异常大的消息数），避免测试数据污染真实列表
const displaySessions = computed(() =>
  sessions.value.filter((session) => (session.messageCount ?? 0) <= 500)
)

const loadSessions = async () => {
  try {
    const response = await chatAPI.sessions(1, 50)
    sessions.value = response.data.items
    const firstSession = sessions.value[0]
    if (!activeSessionId.value && firstSession) {
      await selectSession(firstSession.sessionId)
    }
  } catch {
    ElMessage.error('加载会话列表失败。')
  }
}

const selectSession = async (sessionId: string) => {
  activeSessionId.value = sessionId
  loadingHistory.value = true
  try {
    const response = await chatAPI.history(sessionId, 1, PAGE_SIZE.large)
    historyItems.value = response.data.items
  } catch {
    ElMessage.error('加载会话历史失败。')
  } finally {
    loadingHistory.value = false
  }
}

const openInChat = () => {
  if (!activeSessionId.value) {
    return
  }

  router.push({
    path: '/chat',
    query: {
      session: activeSessionId.value
    }
  })
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.history-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.sessions-panel,
.history-list-card {
  padding: 24px;
}

.panel-heading,
.history-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.session-list,
.history-list {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.session-card {
  display: grid;
  gap: 8px;
  text-align: left;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.76);
  border-radius: 18px;
  padding: 16px;
  cursor: pointer;
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.session-card:hover,
.session-card.active {
  transform: translateY(-2px);
  border-color: rgba(47, 106, 89, 0.28);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
}

.session-card span,
.session-card small {
  color: var(--ink-soft);
  line-height: 1.7;
}

.history-main {
  display: grid;
  gap: 18px;
}

.history-hero {
  padding: 24px;
}

.hero-kicker {
  display: inline-flex;
  width: fit-content;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.history-item {
  padding: 22px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.history-question span,
.history-answer span,
.history-block h4 {
  color: var(--ink-soft);
  font-size: 13px;
}

.history-question strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-strong);
  font-size: 20px;
  line-height: 1.6;
}

.history-answer {
  margin-top: 16px;
}

.history-answer p {
  margin: 8px 0 0;
  color: var(--ink-strong);
  white-space: pre-wrap;
  line-height: 1.85;
}

.history-block {
  margin-top: 16px;
}

.source-list {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.source-card {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(248, 246, 239, 0.9);
}

.source-card span {
  color: var(--ink-soft);
  line-height: 1.75;
}

.entity-pill {
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
  .history-page {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .panel-heading,
  .history-hero {
    flex-direction: column;
  }
}
</style>
