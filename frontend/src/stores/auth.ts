import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { AUTH_TOKEN_KEY, authAPI, type AuthResponse, type AuthUser, type LoginRequest, type RegisterRequest } from '@/api'

const AUTH_USER_KEY = 'litchi.auth.user'

const readStoredUser = (): AuthUser | null => {
  const raw = localStorage.getItem(AUTH_USER_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const user = ref<AuthUser | null>(readStoredUser())

  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  const persistSession = (payload: AuthResponse) => {
    token.value = payload.token
    user.value = payload.user
    localStorage.setItem(AUTH_TOKEN_KEY, payload.token)
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(payload.user))
  }

  const clearSession = () => {
    token.value = null
    user.value = null
    localStorage.removeItem(AUTH_TOKEN_KEY)
    localStorage.removeItem(AUTH_USER_KEY)
  }

  const login = async (payload: LoginRequest) => {
    const response = await authAPI.login(payload)
    persistSession(response.data)
    return response.data
  }

  const register = async (payload: RegisterRequest) => {
    const response = await authAPI.register(payload)
    persistSession(response.data)
    return response.data
  }

  const fetchMe = async () => {
    if (!token.value) {
      return null
    }

    try {
      const response = await authAPI.me()
      user.value = response.data
      localStorage.setItem(AUTH_USER_KEY, JSON.stringify(response.data))
      return response.data
    } catch {
      clearSession()
      localStorage.setItem('auth_failed_at', Date.now().toString())
      return null
    }
  }

  const logout = async () => {
    try {
      if (token.value) {
        await authAPI.logout()
      }
    } finally {
      clearSession()
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    register,
    fetchMe,
    logout,
    clearSession
  }
})
