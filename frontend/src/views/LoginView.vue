<template>
  <div class="login-page">
    <section class="login-hero">
      <span class="hero-kicker">Full Platform</span>
      <h1>荔枝智能问答平台</h1>
      <p>
        现在不是单纯的演示壳，而是带登录、对话历史、评测中心和完整业务页的可运行平台。
      </p>

      <div class="demo-accounts">
        <article v-for="account in demoAccounts" :key="account.username" class="account-card">
          <strong>{{ account.label }}</strong>
          <span>{{ account.username }} / {{ account.password }}</span>
          <button type="button" @click="useDemoAccount(account.username, account.password)">一键填充</button>
        </article>
      </div>
    </section>

    <section class="login-panel soft-card">
      <header>
        <h2>{{ mode === 'login' ? '登录平台' : '注册账号' }}</h2>
        <p>{{ mode === 'login' ? '登录后即可访问问答、图谱、文档与评测模块。' : '支持农户、农技员和农资店三种角色。' }}</p>
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
            <el-option label="农技员" value="technician" />
            <el-option label="农资店" value="shopkeeper" />
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
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

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

const demoAccounts = [
  { label: '农户演示账号', username: 'farmer', password: 'demo123' },
  { label: '农技员演示账号', username: 'technician', password: 'demo123' },
  { label: '农资店演示账号', username: 'shopkeeper', password: 'demo123' }
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

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    router.replace(redirect)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '登录失败，请检查账号信息。')
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
  margin: 20px 0 0;
  font-size: 48px;
  line-height: 1.08;
}

.login-hero p {
  max-width: 640px;
  margin: 18px 0 0;
  color: rgba(255, 246, 231, 0.8);
  line-height: 1.9;
  font-size: 16px;
}

.demo-accounts {
  display: grid;
  gap: 14px;
  max-width: 620px;
  margin-top: 34px;
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
}

.account-card span {
  color: rgba(255, 246, 231, 0.72);
}

.account-card button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  color: #173b31;
  background: #ffd26f;
  font-weight: 700;
}

.login-panel {
  margin: 28px;
  padding: 28px;
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

@media (max-width: 1080px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-hero {
    padding: 40px 22px 10px;
  }

  .login-panel {
    margin: 18px;
  }
}
</style>
