<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>智能问答</h2>
        <p>问题会结合已上传文档和知识图谱命中结果生成答案。</p>
      </div>
      <el-button text @click="chatStore.clearMessages()">清空对话</el-button>
    </header>

    <main class="page-main">
      <div class="messages" ref="messagesListRef">
        <el-empty
          v-if="!chatStore.messages.length && !chatStore.isLoading"
          description="先上传知识文档，再输入问题开始演示。"
        />

        <article
          v-for="msg in chatStore.messages"
          :key="msg.id"
          :class="['message-row', msg.role]"
        >
          <div :class="['message-card', msg.role]">
            <div class="message-role">{{ msg.role === 'user' ? '你' : '助手' }}</div>
            <div class="message-content">{{ msg.content }}</div>

            <div v-if="msg.sources?.length" class="source-list">
              <div v-for="source in msg.sources" :key="`${msg.id}-${source.source}-${source.page}`" class="source-card">
                <div class="source-head">
                  <strong>{{ source.source }}</strong>
                  <span>{{ formatScore(source.score) }}</span>
                </div>
                <div class="source-meta">
                  <span v-if="source.page">片段 {{ source.page }}</span>
                  <span>{{ source.title }}</span>
                </div>
                <p>{{ source.content }}</p>
              </div>
            </div>

            <el-tag
              v-if="msg.knowledgeGraph?.entities?.length"
              class="graph-tag"
              type="success"
              effect="plain"
            >
              图谱命中 {{ msg.knowledgeGraph.entities.length }} 个实体
            </el-tag>
          </div>
        </article>

        <div v-if="chatStore.isLoading" class="loading-row">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在生成回答...</span>
        </div>
      </div>
    </main>

    <footer class="page-footer">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="例如：荔枝炭疽病在雨季怎么防治？"
        @keydown.enter.prevent="sendMessage"
      />
      <div class="footer-actions">
        <span class="hint">`Enter` 发送，`Shift + Enter` 换行</span>
        <el-button type="primary" :icon="Promotion" :loading="chatStore.isLoading" @click="sendMessage">
          发送问题
        </el-button>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Promotion } from '@element-plus/icons-vue'

import { chatAPI } from '@/api'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const inputMessage = ref('')
const messagesListRef = ref<HTMLElement | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (messagesListRef.value) {
    messagesListRef.value.scrollTop = messagesListRef.value.scrollHeight
  }
}

const formatScore = (score?: number) => {
  if (typeof score !== 'number') {
    return '相关片段'
  }

  return `相关度 ${(score * 100).toFixed(1)}%`
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

    chatStore.addMessage({
      id: `${Date.now()}-assistant`,
      role: 'assistant',
      content: response.data.answer,
      timestamp: Date.now(),
      sources: response.data.sources,
      knowledgeGraph: response.data.knowledgeGraph
    })
  } catch (error) {
    ElMessage.error('发送失败，请检查后端服务是否已启动。')
  } finally {
    chatStore.setLoading(false)
  }
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

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 24px;
  gap: 18px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.page-main {
  min-height: 0;
  flex: 1;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.messages {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-card {
  max-width: min(820px, 80%);
  padding: 16px 18px;
  border-radius: 20px;
}

.message-card.user {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.message-card.assistant {
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.22);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.message-role {
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.72;
}

.message-content {
  white-space: pre-wrap;
  line-height: 1.7;
}

.source-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.source-card {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.source-head,
.source-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: #475569;
}

.source-meta {
  margin-top: 4px;
}

.source-card p {
  margin: 10px 0 0;
  color: #334155;
  line-height: 1.6;
}

.graph-tag {
  margin-top: 12px;
}

.loading-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  padding: 12px 4px;
}

.page-footer {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.footer-actions {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint {
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 960px) {
  .page {
    padding: 16px;
  }

  .message-card {
    max-width: 100%;
  }

  .footer-actions {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
