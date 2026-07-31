import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'

import { blockedRouteMessage, defaultRouteForRole, isRoleAllowed } from '@/auth/access'
import { useAuthStore } from '@/stores/auth'

const resolveRoleHome = () => {
  const authStore = useAuthStore()
  return defaultRouteForRole(authStore.user?.role)
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: resolveRoleHome
  },
  {
    path: '/overview',
    redirect: resolveRoleHome
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
    path: '/farmer/workbench',
    name: 'FarmerWorkbench',
    component: () => import('@/views/FarmerWorkbenchView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer']
    }
  },
  {
    path: '/shop/workbench',
    name: 'ShopWorkbench',
    component: () => import('@/views/ShopWorkbenchView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['shopkeeper']
    }
  },
  {
    path: '/technician/workbench',
    name: 'TechnicianWorkbench',
    component: () => import('@/views/OverviewView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician']
    }
  },
  {
    path: '/training',
    name: 'Training',
    component: () => import('@/views/TrainingView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
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
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('@/views/AgentView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/orchards',
    name: 'Orchards',
    component: () => import('@/views/OrchardView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/solutions',
    name: 'Solutions',
    component: () => import('@/views/SolutionsView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/consultations/my',
    name: 'MyConsultations',
    component: () => import('@/views/MyConsultationsView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer']
    }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: () => import('@/views/FeedbackView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['farmer', 'technician']
    }
  },
  {
    path: '/shop/profile',
    name: 'ShopProfile',
    component: () => import('@/views/ShopProfileView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['shopkeeper']
    }
  },
  {
    path: '/shop/plans',
    name: 'ShopPlans',
    component: () => import('@/views/RemedyPlansView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['shopkeeper']
    }
  },
  {
    path: '/shop/inbox',
    name: 'ShopInbox',
    component: () => import('@/views/ConsultationInboxView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['shopkeeper']
    }
  },
  {
    path: '/shop/trends',
    name: 'ShopTrends',
    component: () => import('@/views/DiseaseTrendsView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['shopkeeper']
    }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician', 'farmer']
    }
  },
  {
    path: '/document',
    name: 'Document',
    component: () => import('@/views/DocumentView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['technician']
    }
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

  const authFailedAt = localStorage.getItem('auth_failed_at')
  if (authFailedAt && !isPublic && to.path !== '/login') {
    const elapsed = Date.now() - Number(authFailedAt)
    if (elapsed < 5000) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath
        }
      }
    }
  }

  if (authStore.token && !authStore.user) {
    const me = await authStore.fetchMe()
    if (!me && !isPublic) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath
        }
      }
    }
  }

  if (!isPublic && !authStore.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return defaultRouteForRole(authStore.user?.role)
  }

  if (!isPublic && !isRoleAllowed(authStore.user?.role, to.meta.roles)) {
    ElMessage.warning(blockedRouteMessage(to.path))
    return defaultRouteForRole(authStore.user?.role)
  }

  return true
})

export default router
