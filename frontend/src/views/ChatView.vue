<template>
  <div class="chat-page">
    <aside class="chat-sidebar soft-card">
      <section class="sidebar-intro">
        <span class="sidebar-kicker">Q&A Flow</span>
        <h3 class="section-title">智能问答</h3>
        <p class="section-copy">
          问答页会先检索知识文档，再补充图谱命中，最后生成可追溯回答。
          对农户来说这是确认病症和理解 AI 建议的关键一步，对管理员来说也是最直观的演示页。
        </p>
      </section>

      <section>
        <h3 class="section-title">推荐提问</h3>
        <p class="section-copy">点击即可直接体验检索增强问答，不需要临时组织问题。</p>
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
        <p>回答区域下方会同步展示来源片段和图谱实体，适合解释“系统为什么会这样回答”。</p>
      </section>

      <div class="sidebar-actions">
        <el-button type="primary" plain @click="startFreshSession">开始新会话</el-button>
        <el-button type="primary" @click="openSolutions">查看解决方案</el-button>
      </div>
    </aside>

    <section class="chat-main">
      <div class="conversation glass-card">
        <div class="messages" ref="messagesListRef">
          <div v-if="!chatStore.messages.length && !chatStore.isLoading" class="empty-state">
            <h3>从一个推荐问题开始最稳妥</h3>
            <p>系统会先检索文档片段，再补充图谱命中实体，最后生成一段便于继续讲解和跳转方案页的回答。</p>
          </div>

          <article
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :class="['message-row', msg.role]"
          >
            <div :class="['message-card', msg.role]">
              <div class="message-role">{{ msg.role === 'user' ? '农户提问' : '系统回答' }}</div>
              <div class="message-content">{{ msg.content }}</div>

              <div v-if="msg.role === 'assistant'" class="speech-actions">
                <el-button link type="primary" @click="speakMessage(msg.content)">朗读回答</el-button>
              </div>

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
          placeholder="例如：荔枝炭疽病在雨季应该如何预防和处理？"
          @keydown.enter.prevent="handleEnter"
        />
        <div class="composer-actions">
          <span class="composer-hint">当前会话：{{ chatStore.currentSessionId }}</span>
          <div class="composer-buttons">
            <el-button :type="isListening ? 'danger' : 'default'" @click="toggleVoiceInput">
              {{ isListening ? '停止语音' : '语音输入' }}
            </el-button>
            <el-button @click="chatStore.clearMessages()">清空对话</el-button>
            <el-button type="primary" :loading="chatStore.isLoading" @click="sendMessage">发送问题</el-button>
          </div>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

import { chatAPI, systemAPI } from '@/api'
import { PAGE_SIZE } from '@/config/constants'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

const inputMessage = ref('')
const messagesListRef = ref<HTMLElement | null>(null)
const isListening = ref(false)
let speechRecognition: any = null
const suggestedQuestions = ref<string[]>([
  '荔枝炭疽病在雨季怎么防治？',
  '霜疫霉病和炭疽病有什么区别？',
  '桂味荔枝花果期需要注意哪些管理要点？',
  '蒂蛀虫高发期应该怎么监测和处理？'
])
const lastAutoQuestion = ref('')
let lastRequestId = 0
const latestQuestion = computed(() => {
  const latestUserMessage = [...chatStore.messages].reverse().find(message => message.role === 'user')
  return latestUserMessage?.content ?? inputMessage.value.trim()
})

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
  const requestId = ++lastRequestId
  try {
    const response = await systemAPI.overview()
    if (requestId !== lastRequestId) return
    suggestedQuestions.value = response.data.suggestedQuestions
  } catch {
    // Keep local fallback suggestions.
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
    const response = await chatAPI.send({
      question,
      sessionId: chatStore.currentSessionId,
      useKnowledgeGraph: true,
      useVectorSearch: true
    })
    appendAssistantMessage(response.data.answer, response.data.sources, response.data.knowledgeGraph)
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? '发送失败，请检查后端问答服务。')
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

  const requestId = ++lastRequestId
  lastAutoQuestion.value = question
  inputMessage.value = question
  await sendMessage()
  if (requestId !== lastRequestId) return
  router.replace({ path: route.path, query: {} })
}

const maybeLoadRouteSession = async () => {
  const sessionId = typeof route.query.session === 'string' ? route.query.session.trim() : ''
  if (!sessionId) {
    return
  }

  const requestId = ++lastRequestId
  try {
    const response = await chatAPI.history(sessionId, 1, PAGE_SIZE.large)
    if (requestId !== lastRequestId) return
    chatStore.setSessionId(sessionId)
    chatStore.loadHistory(response.data.items)
  } catch {
    if (requestId !== lastRequestId) return
    ElMessage.error('加载历史会话失败。')
  }
}

const startFreshSession = () => {
  chatStore.startNewSession()
  inputMessage.value = ''
}

const openSolutions = () => {
  const question = latestQuestion.value
  router.push({
    path: '/solutions',
    query: question ? { question } : {}
  })
}

const createSpeechRecognition = () => {
  const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  if (!SpeechRecognition) {
    return null
  }

  const recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.interimResults = false
  recognition.maxAlternatives = 1
  recognition.onstart = () => {
    isListening.value = true
  }
  recognition.onend = () => {
    isListening.value = false
  }
  recognition.onresult = (event: any) => {
    const transcript = event.results?.[0]?.[0]?.transcript
    if (transcript) {
      inputMessage.value = transcript
      ElMessage.success('语音转文字完成。')
    }
  }
  recognition.onerror = () => {
    isListening.value = false
    ElMessage.error('语音识别失败，请检查浏览器权限。')
  }
  return recognition
}

const toggleVoiceInput = () => {
  if (isListening.value && speechRecognition) {
    speechRecognition.stop()
    return
  }

  speechRecognition = speechRecognition || createSpeechRecognition()
  if (!speechRecognition) {
    ElMessage.warning('当前浏览器不支持语音识别，建议使用 Chrome 或 Edge 浏览器。')
    return
  }
  try {
    speechRecognition.start()
    ElMessage.info('已开启语音识别，请在浏览器弹出的权限提示中允许使用麦克风。')
  } catch {
    ElMessage.warning('无法启动语音识别，请在浏览器设置中允许麦克风权限后重试。')
  }
}

const speakMessage = (content: string) => {
  if (!('speechSynthesis' in window)) {
    ElMessage.warning('当前浏览器不支持语音播报。')
    return
  }

  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(content)
  utterance.lang = 'zh-CN'
  utterance.rate = 1
  window.speechSynthesis.speak(utterance)
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

watch(
  () => route.query.session,
  () => {
    maybeLoadRouteSession()
  }
)

onMounted(() => {
  loadSuggestions()
  maybeLoadRouteSession()
  maybeSendRouteQuestion()
  scrollToBottom()
})

onBeforeUnmount(() => {
  if (speechRecognition) {
    speechRecognition.stop()
    speechRecognition.onstart = null
    speechRecognition.onend = null
    speechRecognition.onresult = null
    speechRecognition.onerror = null
  }
  window.speechSynthesis.cancel()
})
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 210px);
  align-items: start;
}

.chat-sidebar,
.composer {
  padding: 24px;
}

.chat-sidebar {
  align-self: start;
  display: grid;
  gap: 18px;
}

.sidebar-intro {
  display: grid;
  gap: 12px;
}

.sidebar-kicker {
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

.question-palette,
.sidebar-actions {
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
  line-height: 1.75;
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
  line-height: 1.75;
}

.chat-main {
  display: grid;
  grid-template-rows: auto auto;
  gap: 18px;
  min-height: 0;
  align-self: start;
}

.conversation {
  min-height: 0;
  overflow: hidden;
}

.messages {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 260px;
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  color: var(--ink-strong);
}

.empty-state p {
  margin: 10px 0 0;
  max-width: 540px;
  color: var(--ink-soft);
  line-height: 1.8;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.message-row:last-child {
  margin-bottom: 0;
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

.speech-actions {
  margin-top: 8px;
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
  line-height: 1.75;
  overflow-wrap: anywhere;
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

  .composer-buttons {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
