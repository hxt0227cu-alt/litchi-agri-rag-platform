<template>
  <div class="login-page">
    <section class="login-hero">
      <span class="hero-kicker">Three Roles Collaboration</span>
      <h1>基于大模型RAG的荔枝智能问答平台设计与实现</h1>
      <p>
        把农户提问、门店方案和技术复核放进同一条荔枝协同链路
      </p>

      <div class="hero-metrics">
        <article v-for="item in heroMetrics" :key="item.label" class="hero-metric-card">
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
          <small>{{ item.copy }}</small>
        </article>
      </div>

      <LitchiHero3D class="hero-visual" />

      <div class="demo-accounts">
        <article v-for="account in demoAccounts" :key="account.username" class="account-card">
          <div>
            <strong>{{ account.label }}</strong>
            <span>{{ account.username }} / {{ account.password }}</span>
          </div>
          <button type="button" @click="useDemoAccount(account.username, account.password)">一键填入</button>
        </article>
      </div>
    </section>

    <section class="login-panel soft-card">
      <header>
        <h2>{{ mode === 'login' ? '登录平台' : '注册账号' }}</h2>
        <p>
          {{
            mode === 'login'
              ? '登录后即可进入农户、门店或管理员工作台。'
              : '支持农户、管理员和门店三类角色注册体验。'
          }}
        </p>
      </header>

      <div class="mode-switch">
        <button :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'">注册</button>
      </div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="角色">
          <el-select v-model="form.role" placeholder="请选择角色">
            <el-option label="农户" value="farmer" />
            <el-option label="管理员" value="technician" />
            <el-option label="门店" value="shopkeeper" />
          </el-select>
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="submit">
          {{ mode === 'login' ? '登录平台' : '创建账号' }}
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { defaultRouteForRole } from '@/auth/access'
const LitchiHero3D = defineAsyncComponent(() => import('@/components/LitchiHero3D.vue'))
import { useAuthStore } from '@/stores/auth'

type Mode = 'login' | 'register'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const mode = ref<Mode>('login')
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '',
  role: 'farmer' as 'farmer' | 'technician' | 'shopkeeper'
})

const heroMetrics = [
  { value: '3', label: '角色工作台', copy: '农户、门店和管理员都有独立首页。' },
  { value: '4', label: '核心入口', copy: '识别、问答、学习和方案推荐都挂在 Hero 上。' },
  { value: '1', label: '协同闭环', copy: '从病症判断到门店求助再到技术复核一条线讲清楚。' }
]

const demoAccounts = [
  { label: '农户演示账号', username: 'farmer', password: 'demo123' },
  { label: '管理员演示账号', username: 'technician', password: 'demo123' },
  { label: '门店演示账号', username: 'shopkeeper', password: 'demo123' }
]

const useDemoAccount = (username: string, password: string) => {
  form.username = username
  form.password = password
  mode.value = 'login'
}

const submit = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码。')
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'login') {
      await authStore.login({
        username: form.username.trim(),
        password: form.password
      })
      ElMessage.success('登录成功。')
    } else {
      await authStore.register({
        username: form.username.trim(),
        password: form.password,
        role: form.role
      })
      ElMessage.success('注册成功，已自动登录。')
    }

    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : defaultRouteForRole(authStore.user?.role)
    router.replace(redirect)
  } catch (error: any) {
    const status = error?.response?.status
    const serverMessage = error?.response?.data?.message
    if (!error?.response) {
      ElMessage.error('无法连接服务器，请确认后端服务已启动（8080 端口）。')
    } else if (status === 401) {
      ElMessage.error(serverMessage ?? '用户名或密码错误，请检查后重试。')
    } else if (status === 403) {
      ElMessage.error(serverMessage ?? '当前浏览器来源不在服务端允许范围内，请使用本地端口访问。')
    } else {
      ElMessage.error(serverMessage ?? `登录失败（HTTP ${status ?? '未知'}），请稍后重试。`)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) 440px;
  background:
    radial-gradient(circle at top left, rgba(255, 210, 111, 0.24), transparent 26%),
    radial-gradient(circle at 70% 30%, rgba(223, 107, 89, 0.16), transparent 22%),
    linear-gradient(135deg, #183f35, #0d221d 58%, #142d27);
}

.login-hero {
  padding: 56px 64px;
  color: #fff6e7;
  display: grid;
  align-content: center;
}

.hero-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 210, 111, 0.16);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.login-hero h1 {
  max-width: 760px;
  margin: 20px 0 0;
  font-size: 52px;
  line-height: 1.08;
}

.login-hero p {
  max-width: 680px;
  margin: 18px 0 0;
  color: rgba(255, 246, 231, 0.8);
  line-height: 1.9;
  font-size: 16px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  max-width: 760px;
  margin-top: 24px;
}

.hero-metric-card {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 248, 235, 0.08);
  border: 1px solid rgba(255, 244, 212, 0.12);
  box-shadow: 0 18px 36px rgba(6, 19, 15, 0.16);
}

.hero-metric-card strong {
  font-size: 28px;
  color: #fff4d4;
}

.hero-metric-card span {
  font-size: 14px;
  font-weight: 700;
  color: rgba(255, 246, 231, 0.9);
}

.hero-metric-card small {
  color: rgba(255, 246, 231, 0.72);
  line-height: 1.6;
}

.hero-visual {
  max-width: 780px;
  margin-top: 28px;
}

.demo-accounts {
  display: grid;
  gap: 14px;
  max-width: 680px;
  margin-top: 32px;
}

.account-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 248, 235, 0.08);
  border: 1px solid rgba(255, 244, 212, 0.12);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.account-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 36px rgba(6, 19, 15, 0.18);
  border-color: rgba(255, 210, 111, 0.22);
}

.account-card strong {
  display: block;
  color: #fff4d4;
}

.account-card span {
  display: block;
  margin-top: 6px;
  color: rgba(255, 246, 231, 0.72);
}

.account-card button {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  cursor: pointer;
  color: #173b31;
  background: #ffd26f;
  font-weight: 700;
}

.login-panel {
  margin: 28px;
  padding: 30px;
  align-self: center;
}

.login-panel h2 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 30px;
}

.login-panel p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin: 24px 0 18px;
  padding: 6px;
  border-radius: 18px;
  background: rgba(47, 106, 89, 0.08);
}

.mode-switch button {
  border: none;
  border-radius: 14px;
  padding: 12px;
  background: transparent;
  cursor: pointer;
  color: var(--primary-deep);
  font-weight: 700;
}

.mode-switch button.active {
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(31, 74, 63, 0.08);
}

.submit-btn {
  width: 100%;
  margin-top: 10px;
}

@media (max-width: 1180px) {
  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1080px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-hero {
    padding: 40px 22px 10px;
  }

  .login-hero h1 {
    font-size: 40px;
  }

  .login-panel {
    margin: 18px;
  }

  .account-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
