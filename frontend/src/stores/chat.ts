import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { ChatHistoryItem, ChatSource, GraphEntity } from '@/api'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  sources?: ChatSource[]
  knowledgeGraph?: {
    entities?: GraphEntity[]
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const currentSessionId = ref<string>(window.crypto.randomUUID())

  const addMessage = (message: ChatMessage) => {
    messages.value.push(message)
  }

  const clearMessages = () => {
    messages.value = []
  }

  const startNewSession = () => {
    currentSessionId.value = window.crypto.randomUUID()
    clearMessages()
  }

  const loadHistory = (items: ChatHistoryItem[]) => {
    messages.value = items
      .slice()
      .reverse()
      .flatMap(item => ([
        {
          id: `${item.id}-user`,
          role: 'user' as const,
          content: item.question,
          timestamp: new Date(item.createdAt).getTime()
        },
        {
          id: `${item.id}-assistant`,
          role: 'assistant' as const,
          content: item.answer,
          timestamp: new Date(item.createdAt).getTime(),
          sources: item.sources,
          knowledgeGraph: item.knowledgeGraph
        }
      ]))
  }

  const setLoading = (loading: boolean) => {
    isLoading.value = loading
  }

  const setSessionId = (id: string) => {
    currentSessionId.value = id
  }

  return {
    messages,
    isLoading,
    currentSessionId,
    addMessage,
    clearMessages,
    startNewSession,
    loadHistory,
    setLoading,
    setSessionId
  }
})
