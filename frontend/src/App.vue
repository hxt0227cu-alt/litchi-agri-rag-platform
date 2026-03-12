<template>
  <el-container class="app-shell">
    <el-aside width="240px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">L</div>
        <div>
          <h1>荔枝问答平台</h1>
          <p>毕业设计演示版</p>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="menu"
        background-color="transparent"
        text-color="#dbeafe"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能问答</span>
        </el-menu-item>
        <el-menu-item index="/diagnosis">
          <el-icon><Picture /></el-icon>
          <span>拍照识病</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Connection /></el-icon>
          <span>知识图谱</span>
        </el-menu-item>
        <el-menu-item index="/document">
          <el-icon><Folder /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-shell">
      <router-view />
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Picture, Connection, Folder } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.app-shell {
  min-height: 100%;
}

.sidebar {
  padding: 20px 16px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.94), rgba(30, 41, 59, 0.94)),
    #0f172a;
  border-right: 1px solid rgba(148, 163, 184, 0.18);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 18px;
  border-radius: 18px;
  background: rgba(59, 130, 246, 0.14);
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #eff6ff;
  background: linear-gradient(135deg, #38bdf8, #2563eb);
}

.brand h1 {
  margin: 0;
  font-size: 18px;
  color: #eff6ff;
}

.brand p {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(219, 234, 254, 0.72);
}

.menu {
  border: none;
}

.menu :deep(.el-menu-item) {
  margin-bottom: 8px;
  border-radius: 12px;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(59, 130, 246, 0.28);
}

.main-shell {
  min-width: 0;
  height: 100vh;
}
</style>
