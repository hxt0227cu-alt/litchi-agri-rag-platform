<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand-card">
        <div class="brand-mark">L</div>
        <div>
          <h1>荔枝智答演示系统</h1>
          <p>病害识别 · 知识图谱 · 检索问答</p>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(246, 240, 224, 0.8)"
        active-text-color="#fff4d4"
      >
        <el-menu-item index="/overview">
          <el-icon><HomeFilled /></el-icon>
          <span>系统总览</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能问答</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Connection /></el-icon>
          <span>知识图谱</span>
        </el-menu-item>
        <el-menu-item index="/document">
          <el-icon><Folder /></el-icon>
          <span>知识文档</span>
        </el-menu-item>
        <el-menu-item index="/diagnosis">
          <el-icon><Picture /></el-icon>
          <span>病害识别</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-note">
        <strong>答辩提示</strong>
        <p>建议按照“总览 → 文档 → 问答 → 图谱 → 识别”的顺序演示。</p>
      </div>
    </aside>

    <main class="app-main">
      <header class="topbar">
        <div>
          <h2>{{ pageTitle }}</h2>
          <p>{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-badges">
          <span class="topbar-pill">离线可演示</span>
          <span class="topbar-pill accent">答辩模式</span>
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
import { useRoute } from 'vue-router'
import { ChatDotRound, Connection, Folder, HomeFilled, Picture } from '@element-plus/icons-vue'

const route = useRoute()

const pageMeta: Record<string, { title: string; subtitle: string }> = {
  '/overview': {
    title: '系统总览',
    subtitle: '一屏查看服务状态、演示素材和推荐讲解顺序。'
  },
  '/chat': {
    title: '智能问答',
    subtitle: '结合知识文档切片和图谱实体，为答辩演示生成可追溯回答。'
  },
  '/knowledge': {
    title: '知识图谱',
    subtitle: '展示荔枝品种、病害、药剂和栽培技术之间的关联关系。'
  },
  '/document': {
    title: '知识文档',
    subtitle: '管理答辩样例文档，并构建检索问答所需的本地索引。'
  },
  '/diagnosis': {
    title: '病害识别',
    subtitle: '支持 YOLO 模型推理与数据集特征匹配双模式演示。'
  }
}

const pageTitle = computed(() => pageMeta[route.path]?.title ?? '荔枝智答演示系统')
const pageSubtitle = computed(() => pageMeta[route.path]?.subtitle ?? '毕业答辩演示版')
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr);
  min-height: 100vh;
}

.app-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 100vh;
  padding: 28px 22px;
  background:
    radial-gradient(circle at top, rgba(255, 210, 122, 0.22), transparent 28%),
    linear-gradient(180deg, rgba(25, 56, 48, 0.98), rgba(14, 35, 31, 0.98));
  border-right: 1px solid rgba(255, 238, 204, 0.12);
}

.brand-card {
  display: flex;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(255, 238, 204, 0.12);
  border-radius: 24px;
  background: rgba(255, 248, 235, 0.06);
  backdrop-filter: blur(12px);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 18px;
  background: linear-gradient(135deg, #ffd26f, #f28c28);
  color: #213a2f;
  font-size: 24px;
  font-weight: 800;
}

.brand-card h1 {
  margin: 0;
  color: #fff4d4;
  font-size: 22px;
  line-height: 1.2;
}

.brand-card p {
  margin: 6px 0 0;
  color: rgba(255, 244, 212, 0.72);
  font-size: 13px;
  line-height: 1.5;
}

.sidebar-menu {
  border: none;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 52px;
  margin-bottom: 8px;
  border-radius: 16px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 248, 235, 0.08);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(255, 210, 111, 0.22), rgba(242, 140, 40, 0.18));
  box-shadow: inset 0 0 0 1px rgba(255, 230, 190, 0.16);
}

.sidebar-note {
  margin-top: auto;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 248, 235, 0.07);
  border: 1px solid rgba(255, 238, 204, 0.1);
}

.sidebar-note strong {
  color: #fff4d4;
  font-size: 14px;
}

.sidebar-note p {
  margin: 8px 0 0;
  color: rgba(255, 244, 212, 0.72);
  font-size: 13px;
  line-height: 1.6;
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

  .sidebar-note {
    margin-top: 0;
  }

  .app-main {
    padding: 20px 16px 24px;
  }

  .topbar {
    flex-direction: column;
  }
}
</style>
