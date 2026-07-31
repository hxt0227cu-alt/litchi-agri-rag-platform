<template>
  <div class="page-shell profile-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Shop Profile</span>
        <h3 class="section-title">店铺管理</h3>
        <p class="section-copy">
          这里维护门店在推荐卡中展示的基础信息，包括联系人、服务区域、擅长方向和门店评分。
          资料越完整，农户在方案页看到的门店信息就越可信。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
        <el-button :loading="loading" @click="loadProfile">重新加载</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">资料完整度</div>
        <div class="metric-value">{{ profileCompleteness }}%</div>
        <div class="metric-note">联系方式、地址、服务区域和擅长方向都补全后，推荐卡展示会更完整。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">门店评分</div>
        <div class="metric-value">{{ form.rating.toFixed(1) }}</div>
        <div class="metric-note">作为冷启动排序和推荐展示中的基础参考分。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">服务覆盖</div>
        <div class="metric-value compact">{{ form.serviceArea || '待填写' }}</div>
        <div class="metric-note">帮助农户快速判断门店是否覆盖自己的服务区域。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">擅长方向</div>
        <div class="metric-value">{{ specialtyCount }}</div>
        <div class="metric-note">建议至少补齐 2 到 3 个明确擅长场景，提升推荐理由的可信度。</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">门店资料表单</h3>
            <p class="section-copy">字段将直接用于方案推荐页和门店协同链路展示，建议用农户能看懂的表达填写。</p>
          </div>
        </header>

        <el-form label-position="top" class="profile-form" :rules="profileRules">
          <el-form-item label="门店名称">
            <el-input v-model="form.shopName" placeholder="例如：果园农资服务站" />
          </el-form-item>
          <el-form-item label="联系人">
            <el-input v-model="form.contactName" placeholder="例如：陈师傅" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入联系电话" />
          </el-form-item>
          <el-form-item label="微信">
            <el-input v-model="form.wechat" placeholder="请输入微信号" />
          </el-form-item>
          <el-form-item label="门店地址">
            <el-input v-model="form.address" placeholder="请输入门店详细地址" />
          </el-form-item>
          <el-form-item label="服务区域">
            <el-input v-model="form.serviceArea" placeholder="例如：高州城区、周边乡镇果园" />
          </el-form-item>
          <el-form-item class="span-two" label="擅长方向">
            <el-input
              v-model="form.specialties"
              type="textarea"
              :rows="4"
              placeholder="例如：雨季病害管理、炭疽病门店答复、果园回访跟进"
            />
          </el-form-item>
          <el-form-item label="门店评分" prop="rating">
            <el-input-number v-model="form.rating" :min="0" :max="5" :step="0.1" :precision="1" />
          </el-form-item>
        </el-form>
      </article>

      <article class="soft-card preview-card">
        <header class="panel-header">
          <div>
            <h3 class="section-title">推荐卡预览</h3>
            <p class="section-copy">这里模拟农户在解决方案页看到的门店基础信息，方便即时调整表达。</p>
          </div>
        </header>

        <div class="preview-body">
          <strong>{{ form.shopName || '门店名称待填写' }}</strong>
          <p>{{ form.address || '门店地址待填写' }}</p>

          <div class="preview-meta">
            <span>联系人：{{ form.contactName || '待填写' }}</span>
            <span>电话：{{ form.phone || '待填写' }}</span>
            <span>微信：{{ form.wechat || '待填写' }}</span>
            <span>服务区域：{{ form.serviceArea || '待填写' }}</span>
          </div>

          <div class="pill-row">
            <span v-for="tag in specialtyTags" :key="tag" class="specialty-pill">{{ tag }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { shopAPI } from '@/api'

const loading = ref(false)
const saving = ref(false)

const createDefaultForm = () => ({
  shopName: '',
  contactName: '',
  phone: '',
  wechat: '',
  address: '',
  serviceArea: '',
  specialties: '',
  rating: 4.6
})

const form = reactive(createDefaultForm())

const profileRules = {
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }
  ],
  rating: [
    { required: true, message: '请输入门店评分', trigger: 'blur' },
    {
      validator: (_rule: any, value: number, callback: any) => {
        if (typeof value !== 'number' || value < 0 || value > 5) {
          callback(new Error('评分必须在 0 到 5 之间'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const specialtyTags = computed(() =>
  form.specialties
    .split(/[、，,\n]/)
    .map(item => item.trim())
    .filter(Boolean)
)

const specialtyCount = computed(() => specialtyTags.value.length)

const profileCompleteness = computed(() => {
  const values = [form.shopName, form.contactName, form.phone, form.wechat, form.address, form.serviceArea, form.specialties]
  const filled = values.filter(value => value.trim()).length
  return Math.round((filled / values.length) * 100)
})

const loadProfile = async () => {
  loading.value = true
  try {
    const response = await shopAPI.profile()
    Object.assign(form, createDefaultForm(), response.data)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载店铺资料失败。')
  } finally {
    loading.value = false
  }
}

const saveProfile = async () => {
  saving.value = true
  try {
    await shopAPI.saveProfile({
      shopName: form.shopName,
      contactName: form.contactName,
      phone: form.phone,
      wechat: form.wechat,
      address: form.address,
      serviceArea: form.serviceArea,
      specialties: form.specialties,
      rating: form.rating
    })
    ElMessage.success('店铺资料已保存。')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '保存店铺资料失败。')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-page {
  gap: 18px;
}

.hero,
.panel,
.preview-card {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 14px;
}

.hero-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.12);
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions,
.content-grid {
  display: grid;
  gap: 18px;
}

.content-grid {
  grid-template-columns: minmax(0, 1fr) 360px;
}

.panel-header {
  margin-bottom: 18px;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.span-two {
  grid-column: span 2;
}

.preview-body {
  display: grid;
  gap: 16px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(31, 74, 63, 0.08), rgba(242, 140, 40, 0.08));
}

.preview-body strong {
  color: var(--ink-strong);
  font-size: 24px;
}

.preview-body p,
.preview-meta span {
  color: var(--ink-soft);
}

.preview-meta {
  display: grid;
  gap: 10px;
}

.specialty-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.compact {
  font-size: 20px;
  line-height: 1.45;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid,
  .profile-form {
    grid-template-columns: 1fr;
  }

  .span-two {
    grid-column: span 1;
  }
}
</style>
