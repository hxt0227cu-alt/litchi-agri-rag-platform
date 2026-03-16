import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { blockedRouteMessage, defaultRouteForRole, isRoleAllowed } from '@/auth/access'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/overview'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: {
      public: true
    }
  },
  {
    path: '/overview',
    name: 'Overview',
    component: () => import('@/views/OverviewView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/training',
    name: 'Training',
    component: () => import('@/views/TrainingView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician']
    }
  },
  {
    path: '/diagnosis',
    name: 'Diagnosis',
    component: () => import('@/views/DiagnosisView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/document',
    name: 'Document',
    component: () => import('@/views/DocumentView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/guide',
    name: 'Guide',
    component: () => import('@/views/GuideView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician', 'shopkeeper']
    }
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: () => import('@/views/FeedbackView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/system',
    name: 'System',
    component: () => import('@/views/SystemView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician']
    }
  },
  {
    path: '/evaluation',
    name: 'Evaluation',
    component: () => import('@/views/EvaluationView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician']
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  const authStore = useAuthStore()
  const isPublic = Boolean(to.meta.public)

  if (!isPublic && !authStore.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return '/overview'
  }

  if (!isPublic && authStore.token && !authStore.user) {
    const me = await authStore.fetchMe()
    if (!me) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath
        }
      }
    }
  }

  if (!isPublic && !isRoleAllowed(authStore.user?.role, to.meta.roles)) {
    ElMessage.warning(blockedRouteMessage(to.path))
    return defaultRouteForRole(authStore.user?.role)
  }

  return true
})

export default router
