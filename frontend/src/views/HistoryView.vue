<template>
  <div class="history-page">
    <aside class="soft-card sessions-panel">
      <div class="panel-heading">
        <div>
          <h3 class="section-title">会话列表</h3>
          <p class="section-copy">查看同一账号下的历史会话与最近提问。</p>
        </div>
        <el-button @click="loadSessions">刷新</el-button>
      </div>

      <div class="session-list">
        <button
          v-for="session in sessions"
          :key="session.sessionId"
          type="button"
          :class="['session-card', { active: session.sessionId === activeSessionId }]"
          @click="selectSession(session.sessionId)"
        >
          <strong>{{ session.title }}</strong>
          <span>{{ session.lastMessage }}</span>
          <small>{{ formatDate(session.updatedAt) }}</small>
        </button>
      </div>
    </aside>

    <section class="history-main">
      <article class="glass-card history-hero">
        <div>
          <h3 class="section-title">对话历史</h3>
          <p class="section-copy">选中某个会话后，可以查看问题、系统回答、来源片段和图谱命中结果。</p>
        </div>
        <el-button type="primary" @click="openInChat" :disabled="!activeSessionId">继续到问答页</el-button>
      </article>

      <article class="soft-card history-list-card">
        <el-empty v-if="!historyItems.length && !loadingHistory" description="暂无历史记录，先去问答页发起一次提问吧。" />

        <div v-else class="history-list">
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
                <article v-for="source in item.sources" :key="`${item.id}-${source.source}-${source.page}`" class="source-card">
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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { chatAPI, type ChatHistoryItem, type ChatSessionItem } from '@/api'

const router = useRouter()
const sessions = ref<ChatSessionItem[]>([])
const historyItems = ref<ChatHistoryItem[]>([])
const activeSessionId = ref('')
const loadingHistory = ref(false)

const loadSessions = async () => {
  try {
    const response = await chatAPI.sessions(1, 50)
    sessions.value = response.data.items
    const firstSession = sessions.value[0]
    if (!activeSessionId.value && firstSession) {
      await selectSession(firstSession.sessionId)
    }
  } catch (error) {
    ElMessage.error('加载会话列表失败。')
  }
}

const selectSession = async (sessionId: string) => {
  activeSessionId.value = sessionId
  loadingHistory.value = true
  try {
    const response = await chatAPI.history(sessionId, 1, 100)
    historyItems.value = response.data.items
  } catch (error) {
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
  padding: 22px;
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
}

.session-card.active {
  border-color: rgba(47, 106, 89, 0.28);
  box-shadow: 0 14px 32px rgba(31, 74, 63, 0.08);
}

.session-card span,
.session-card small {
  color: var(--ink-soft);
}

.history-main {
  display: grid;
  gap: 18px;
}

.history-hero {
  padding: 24px 26px;
}

.history-item {
  padding: 20px;
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
}

.history-answer {
  margin-top: 16px;
}

.history-answer p {
  margin: 8px 0 0;
  color: var(--ink-strong);
  white-space: pre-wrap;
  line-height: 1.8;
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
  line-height: 1.7;
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
</style>
