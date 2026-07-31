<template>
  <router-view v-if="route.path === '/login'" />

  <div v-else class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-card">
        <div class="brand-mark">荔</div>
        <div class="brand-copy">
          <h1>基于大模型RAG的荔枝智能问答平台设计与实现</h1>
          <p>面向农户、门店与管理员的病害识别、知识问答、协同推荐与评测优化平台</p>
        </div>
      </div>

      <div class="role-chip">
        <strong>{{ roleLabel }}</strong>
        <span>{{ roleSummary }}</span>
      </div>

      <el-menu
        :default-active="activeMenuPath"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(246, 240, 224, 0.82)"
        active-text-color="#fff4d4"
      >
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon><component :is="iconMap[item.icon]" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-user">
        <strong>{{ authStore.user?.username }}</strong>
        <span>{{ roleLabel }}</span>
        <el-button text type="warning" @click="handleLogout">退出登录</el-button>
      </div>

      <div class="sidebar-note">
        <strong>{{ workspaceNote.title }}</strong>
        <p>{{ workspaceNote.copy }}</p>
      </div>
    </aside>

    <main class="app-main">
      <header class="topbar">
        <div>
          <h2>{{ currentPage.title }}</h2>
          <p>{{ currentPage.subtitle }}</p>
        </div>
        <div class="topbar-badges">
          <span class="topbar-pill">{{ roleLabel }}</span>
          <span class="topbar-pill accent">{{ currentNavLabel }}</span>
        </div>
      </header>

      <section class="page-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Component } from 'vue'
import {
  Bell,
  ChatDotRound,
  Connection,
  DataAnalysis,
  Document,
  EditPen,
  Files,
  Folder,
  HomeFilled,
  MagicStick,
  Location,
  Picture,
  Reading,
  Setting,
  Shop,
  Tickets,
  TrendCharts,
  User
} from '@element-plus/icons-vue'

import { PAGE_META, ROLE_NAV_ITEMS, type NavIconKey } from '@/config/platform'
import { isPlatformRole } from '@/auth/access'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const iconMap: Record<NavIconKey, Component> = {
  home: HomeFilled,
  reading: Reading,
  picture: Picture,
  chat: ChatDotRound,
  agent: MagicStick,
  orchard: Location,
  files: Files,
  document: Document,
  edit: EditPen,
  shop: Shop,
  tickets: Tickets,
  user: User,
  bell: Bell,
  trend: TrendCharts,
  analysis: DataAnalysis,
  folder: Folder,
  connection: Connection,
  setting: Setting
}

const navItems = computed(() => {
  const role = authStore.user?.role
  if (!isPlatformRole(role)) {
    return []
  }
  return ROLE_NAV_ITEMS[role]
})

const activeMenuPath = computed(() => {
  const matched = navItems.value.find(
    (item: (typeof navItems.value)[number]) => route.path === item.path || route.path.startsWith(`${item.path}/`)
  )
  return matched?.path ?? route.path
})

const currentPage = computed(() => {
  return (
    PAGE_META[route.path] ?? {
      title: '基于大模型RAG的荔枝智能问答平台设计与实现',
      subtitle: '围绕农户、门店和管理员三类角色组织识别、推荐、求助与优化闭环。'
    }
  )
})

const currentNavLabel = computed(() => {
  const item = navItems.value.find((entry: (typeof navItems.value)[number]) => entry.path === activeMenuPath.value)
  return item?.label ?? currentPage.value.title
})

const roleLabel = computed(() => {
  const roleMap: Record<string, string> = {
    farmer: '农户账号',
    technician: '管理员账号',
    shopkeeper: '门店账号'
  }
  return roleMap[authStore.user?.role ?? ''] ?? '平台用户'
})

const roleSummary = computed(() => {
  switch (authStore.user?.role) {
    case 'farmer':
      return '先学习判断，再选方案并提交求助'
    case 'shopkeeper':
      return '维护门店方案，跟进求助并观察热度'
    case 'technician':
      return '维护知识、复核低分问题并持续优化 AI'
    default:
      return '协同处理荔枝病害问题'
  }
})

const workspaceNote = computed(() => {
  switch (authStore.user?.role) {
    case 'farmer':
      return {
        title: '推荐体验路径',
        copy: '按“学习课堂 -> 病害识别 / 智能问答 -> 解决方案 -> 我的求助 -> 满意度反馈”的顺序体验，最容易讲清主链路。'
      }
    case 'shopkeeper':
      return {
        title: '推荐工作节奏',
        copy: '先完善门店资料和方案库，再处理待办求助，最后回看高频病症，方便为下一轮咨询提前准备。'
      }
    case 'technician':
      return {
        title: '推荐排查顺序',
        copy: '先看管理员工作台和评测中心，再进入知识文档、研判图谱和系统状态页，最适合解释 AI 是如何被持续优化的。'
      }
    default:
      return {
        title: '协同闭环',
        copy: '平台围绕识别、推荐、求助和复核形成完整协同链路，适合用一条故事线串起答辩演示。'
      }
  }
})

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 304px minmax(0, 1fr);
  min-height: 100vh;
}

.app-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 100vh;
  padding: 28px 22px;
  background:
    radial-gradient(circle at top, rgba(255, 210, 122, 0.24), transparent 28%),
    radial-gradient(circle at 80% 30%, rgba(242, 140, 40, 0.18), transparent 24%),
    linear-gradient(180deg, rgba(25, 56, 48, 0.98), rgba(14, 35, 31, 0.98));
  border-right: 1px solid rgba(255, 238, 204, 0.12);
}

.brand-card,
.role-chip,
.sidebar-user,
.sidebar-note {
  backdrop-filter: blur(12px);
}

.brand-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 18px;
  border: 1px solid rgba(255, 238, 204, 0.12);
  border-radius: 24px;
  background: rgba(255, 248, 235, 0.08);
}

.brand-copy {
  min-width: 0;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 18px;
  background: linear-gradient(145deg, #ffd26f, #f28c28);
  color: #213a2f;
  font-size: 24px;
  font-weight: 800;
  box-shadow: 0 16px 28px rgba(242, 140, 40, 0.22);
}

.brand-card h1 {
  margin: 0;
  color: #fff4d4;
  font-size: 18px;
  line-height: 1.35;
}

.brand-card p {
  margin: 6px 0 0;
  color: rgba(255, 244, 212, 0.72);
  font-size: 12px;
  line-height: 1.6;
}

.role-chip,
.sidebar-user,
.sidebar-note {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 248, 235, 0.08);
  border: 1px solid rgba(255, 238, 204, 0.1);
}

.role-chip strong,
.sidebar-user strong,
.sidebar-note strong {
  color: #fff4d4;
}

.role-chip span,
.sidebar-user span,
.sidebar-note p {
  color: rgba(255, 244, 212, 0.72);
  font-size: 13px;
  line-height: 1.7;
}

.sidebar-menu {
  border: none;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 52px;
  margin-bottom: 8px;
  border-radius: 16px;
  transition:
    transform 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  transform: translateX(2px);
  background: rgba(255, 248, 235, 0.09);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(255, 210, 111, 0.22), rgba(242, 140, 40, 0.18));
  box-shadow:
    inset 0 0 0 1px rgba(255, 230, 190, 0.16),
    0 12px 28px rgba(0, 0, 0, 0.12);
}

.app-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 28px 30px 30px;
}

.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.topbar h2 {
  margin: 0;
  font-size: 32px;
  color: var(--ink-strong);
}

.topbar p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.75;
}

.topbar-badges {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.topbar-pill {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(28, 72, 62, 0.08);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(47, 106, 89, 0.08);
}

.topbar-pill.accent {
  background: rgba(242, 140, 40, 0.16);
  color: #9c4e0c;
}

.page-content {
  min-height: 0;
  flex: 1;
}

@media (max-width: 1080px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .app-sidebar {
    position: relative;
    min-height: auto;
    gap: 18px;
  }

  .app-main {
    padding: 20px 16px 24px;
  }

  .topbar {
    flex-direction: column;
  }
}
</style>
