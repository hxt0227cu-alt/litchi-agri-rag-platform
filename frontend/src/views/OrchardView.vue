<template>
  <div class="orchard-page">
    <section class="orchard-form soft-card">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Orchard Context</span>
          <h3>新增果园</h3>
        </div>
        <el-tag type="info" effect="plain">{{ orchards.length }} 个档案</el-tag>
      </div>

      <el-form label-position="top" @submit.prevent="createOrchard">
        <el-form-item label="果园名称" required>
          <el-input v-model="form.name" maxlength="128" placeholder="例如：荔园东区" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" maxlength="255" placeholder="镇 / 村 / 地块位置" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="品种">
            <el-input v-model="form.variety" maxlength="64" placeholder="例如：桂味" />
          </el-form-item>
          <el-form-item label="生育期">
            <el-input v-model="form.growthStage" maxlength="64" placeholder="例如：花期" />
          </el-form-item>
        </div>
        <el-form-item label="面积（亩）">
          <el-input-number v-model="form.areaMu" :min="0" :precision="2" :step="1" controls-position="right" />
        </el-form-item>
        <el-button type="primary" :loading="saving" :disabled="!form.name.trim()" @click="createOrchard">
          <el-icon><Plus /></el-icon>
          保存档案
        </el-button>
      </el-form>
    </section>

    <section class="orchard-list">
      <div v-if="loading" class="empty-state soft-card">正在加载果园档案...</div>
      <div v-else-if="!orchards.length" class="empty-state soft-card">还没有果园档案</div>
      <article v-for="orchard in orchards" :key="orchard.id" class="orchard-card soft-card">
        <div class="card-heading">
          <div>
            <span class="eyebrow">{{ orchard.id }}</span>
            <h3>{{ orchard.name }}</h3>
          </div>
          <el-tag type="success" effect="plain">已纳入 Agent 上下文</el-tag>
        </div>
        <div class="orchard-facts">
          <span>{{ orchard.location || '位置未填写' }}</span>
          <span>{{ orchard.variety || '品种未填写' }}</span>
          <span>{{ orchard.growthStage || '生育期未填写' }}</span>
          <span>{{ orchard.areaMu ? `${orchard.areaMu} 亩` : '面积未填写' }}</span>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import { orchardAPI, type Orchard } from '@/api'

const orchards = ref<Orchard[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  location: '',
  variety: '',
  growthStage: '',
  areaMu: undefined as number | undefined
})

const loadOrchards = async () => {
  loading.value = true
  try {
    orchards.value = (await orchardAPI.list()).data
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? '果园档案加载失败。')
  } finally {
    loading.value = false
  }
}

const createOrchard = async () => {
  if (!form.name.trim() || saving.value) return
  saving.value = true
  try {
    const response = await orchardAPI.create({ ...form, name: form.name.trim() })
    orchards.value = [response.data, ...orchards.value]
    Object.assign(form, { name: '', location: '', variety: '', growthStage: '', areaMu: undefined })
    ElMessage.success('果园档案已保存。')
  } catch (error) {
    ElMessage.error((error as any)?.response?.data?.message ?? '果园档案保存失败。')
  } finally {
    saving.value = false
  }
}

onMounted(loadOrchards)
</script>

<style scoped>
.orchard-page {
  display: grid;
  grid-template-columns: minmax(300px, 0.75fr) minmax(0, 1.25fr);
  gap: 18px;
  align-items: start;
}

.orchard-form,
.orchard-card,
.empty-state {
  padding: 22px;
}

.orchard-form {
  position: sticky;
  top: 22px;
}

.section-heading,
.card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.eyebrow {
  color: var(--accent-main);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  overflow-wrap: anywhere;
}

h3 {
  margin: 6px 0 0;
  color: var(--ink-strong);
  font-size: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.orchard-list {
  display: grid;
  gap: 14px;
}

.orchard-card h3 {
  line-height: 1.4;
}

.orchard-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.orchard-facts span {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(47, 106, 89, 0.08);
  color: var(--ink-soft);
  font-size: 13px;
}

.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  color: var(--ink-soft);
}

@media (max-width: 900px) {
  .orchard-page {
    grid-template-columns: 1fr;
  }

  .orchard-form {
    position: static;
  }
}
</style>
