import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { ChatSource, GraphEntity } from '@/api'

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

  const addMessage = (message: ChatMessage) => {
    messages.value.push(message)
  }

  const clearMessages = () => {
    messages.value = []
  }

  const setLoading = (loading: boolean) => {
    isLoading.value = loading
  }

  return {
    messages,
    isLoading,
    addMessage,
    clearMessages,
    setLoading
  }
})
