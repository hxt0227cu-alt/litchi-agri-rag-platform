<template>
  <div class="chat-page">
    <aside class="chat-sidebar soft-card">
      <section>
        <h3 class="section-title">推荐提问</h3>
        <p class="section-copy">点一下就能直接演示检索增强问答，不需要现场现想问题。</p>
      </section>

      <div class="question-palette">
        <button
          v-for="question in suggestedQuestions"
          :key="question"
          type="button"
          class="suggestion-card"
          @click="useQuestion(question)"
        >
          {{ question }}
        </button>
      </div>

      <section class="hint-card">
        <strong>讲解建议</strong>
        <p>回答区域下方会同步展示来源片段和图谱实体，适合解释“系统为什么这么回答”。</p>
      </section>
    </aside>

    <section class="chat-main">
      <div class="conversation glass-card">
        <div class="messages" ref="messagesListRef">
          <div v-if="!chatStore.messages.length && !chatStore.isLoading" class="empty-state">
            <h3>从一个推荐问题开始最稳妥</h3>
            <p>系统会先检索文档切片，再补充图谱命中实体，最后生成一段可追溯的回答。</p>
          </div>

          <article
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :class="['message-row', msg.role]"
          >
            <div :class="['message-card', msg.role]">
              <div class="message-role">{{ msg.role === 'user' ? '提问' : '系统回答' }}</div>
              <div class="message-content">{{ msg.content }}</div>

              <div v-if="msg.sources?.length" class="source-list">
                <div
                  v-for="source in msg.sources"
                  :key="`${msg.id}-${source.source}-${source.page}`"
                  class="source-card"
                >
                  <div class="source-head">
                    <strong>{{ source.source }}</strong>
                    <span>{{ formatScore(source.score) }}</span>
                  </div>
                  <div class="source-meta">
                    <span>{{ source.title }}</span>
                    <span v-if="source.page">片段 {{ source.page }}</span>
                  </div>
                  <p>{{ source.content }}</p>
                </div>
              </div>

              <div v-if="msg.knowledgeGraph?.entities?.length" class="entity-list">
                <span
                  v-for="entity in msg.knowledgeGraph.entities"
                  :key="`${msg.id}-${entity.label}-${String(entity.properties?.name ?? '')}`"
                  class="entity-pill"
                >
                  {{ entity.properties?.name ?? entity.label }}
                </span>
              </div>
            </div>
          </article>

          <div v-if="chatStore.isLoading" class="loading-row">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>正在生成回答并整理来源...</span>
          </div>
        </div>
      </div>

      <footer class="composer soft-card">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="例如：荔枝炭疽病在雨季怎么防治？"
          @keydown.enter.prevent="handleEnter"
        />
        <div class="composer-actions">
          <span class="composer-hint">Enter 发送，Shift + Enter 换行</span>
          <div class="composer-buttons">
            <el-button @click="chatStore.clearMessages()">清空对话</el-button>
            <el-button type="primary" :loading="chatStore.isLoading" @click="sendMessage">
              发送问题
            </el-button>
          </div>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

import { chatAPI, systemAPI } from '@/api'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

const inputMessage = ref('')
const messagesListRef = ref<HTMLElement | null>(null)
const suggestedQuestions = ref<string[]>([
  '荔枝炭疽病在雨季怎么防治？',
  '霜疫霉病和炭疽病有什么区别？',
  '桂味荔枝花果期需要注意哪些管理要点？',
  '蒂蛀虫高发期应该怎么监测和处理？'
])
const lastAutoQuestion = ref('')

const scrollToBottom = async () => {
  await nextTick()
  if (messagesListRef.value) {
    messagesListRef.value.scrollTop = messagesListRef.value.scrollHeight
  }
}

const formatScore = (score?: number) => {
  if (typeof score !== 'number') {
    return '文档片段'
  }
  return `相关度 ${(score * 100).toFixed(1)}%`
}

const loadSuggestions = async () => {
  try {
    const response = await systemAPI.overview()
    suggestedQuestions.value = response.data.suggestedQuestions
  } catch (error) {
    // Keep the local fallback list silently.
  }
}

const appendAssistantMessage = (answer: string, sources?: unknown, knowledgeGraph?: unknown) => {
  chatStore.addMessage({
    id: `${Date.now()}-assistant`,
    role: 'assistant',
    content: answer,
    timestamp: Date.now(),
    sources: sources as never,
    knowledgeGraph: knowledgeGraph as never
  })
}

const sendMessage = async () => {
  const question = inputMessage.value.trim()
  if (!question || chatStore.isLoading) {
    return
  }

  chatStore.addMessage({
    id: Date.now().toString(),
    role: 'user',
    content: question,
    timestamp: Date.now()
  })

  inputMessage.value = ''
  chatStore.setLoading(true)

  try {
    const response = await chatAPI.send({ question })
    appendAssistantMessage(response.data.answer, response.data.sources, response.data.knowledgeGraph)
  } catch (error) {
    ElMessage.error('发送失败，请检查后端服务是否启动。')
  } finally {
    chatStore.setLoading(false)
  }
}

const useQuestion = (question: string) => {
  inputMessage.value = question
  sendMessage()
}

const handleEnter = (event: KeyboardEvent) => {
  if (event.shiftKey) {
    return
  }
  sendMessage()
}

const maybeSendRouteQuestion = async () => {
  const question = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (!question || question === lastAutoQuestion.value) {
    return
  }

  lastAutoQuestion.value = question
  inputMessage.value = question
  await sendMessage()
  router.replace({ path: route.path, query: {} })
}

watch(
  () => chatStore.messages.length,
  () => {
    scrollToBottom()
  }
)

watch(
  () => chatStore.isLoading,
  () => {
    scrollToBottom()
  }
)

watch(
  () => route.query.q,
  () => {
    maybeSendRouteQuestion()
  }
)

onMounted(() => {
  loadSuggestions()
  maybeSendRouteQuestion()
  scrollToBottom()
})
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 210px);
}

.chat-sidebar,
.composer {
  padding: 22px;
}

.chat-sidebar {
  align-self: start;
  display: grid;
  gap: 18px;
}

.question-palette {
  display: grid;
  gap: 12px;
}

.suggestion-card {
  padding: 16px 18px;
  text-align: left;
  border: 1px solid rgba(47, 106, 89, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
  color: var(--ink-strong);
  cursor: pointer;
  line-height: 1.7;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.suggestion-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(31, 74, 63, 0.08);
  border-color: rgba(47, 106, 89, 0.18);
}

.hint-card {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(31, 74, 63, 0.08), rgba(242, 140, 40, 0.08));
}

.hint-card strong {
  color: var(--ink-strong);
}

.hint-card p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.chat-main {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 18px;
  min-height: 0;
}

.conversation {
  min-height: 0;
}

.messages {
  height: 100%;
  min-height: 520px;
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding: 22px;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 320px;
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: var(--ink-strong);
}

.empty-state p {
  margin: 10px 0 0;
  max-width: 520px;
  color: var(--ink-soft);
  line-height: 1.8;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-card {
  max-width: min(900px, 88%);
  padding: 18px;
  border-radius: 24px;
}

.message-card.user {
  background: linear-gradient(135deg, #245649, #183f35);
  color: #fff6e7;
}

.message-card.assistant {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.message-role {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.72;
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.9;
}

.source-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.source-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(248, 246, 239, 0.92);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.source-head,
.source-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.source-head {
  color: var(--ink-strong);
}

.source-meta {
  margin-top: 4px;
  color: var(--ink-soft);
}

.source-card p {
  margin: 10px 0 0;
  color: var(--ink-strong);
  line-height: 1.7;
}

.entity-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
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

.loading-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
  padding: 12px 4px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
}

.composer-hint {
  color: var(--ink-soft);
  font-size: 13px;
}

.composer-buttons {
  display: flex;
  gap: 10px;
}

@media (max-width: 1180px) {
  .chat-page {
    grid-template-columns: 1fr;
  }

  .messages {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .composer-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
