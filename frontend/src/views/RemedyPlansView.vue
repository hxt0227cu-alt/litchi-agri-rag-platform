<template>
  <div class="page-shell plans-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Plan Library</span>
        <h3 class="section-title">配药方案</h3>
        <p class="section-copy">
          门店可以在这里新增、编辑、停用或删除自己的方案。方案字段会直接进入推荐卡和求助协同链路，
          所以这里既是门店方案库，也是答辩时最容易讲清的“供给端”页面。
        </p>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="openCreateDialog">新增方案</el-button>
        <el-button @click="goTo('/shop/trends')">查看高频病症</el-button>
        <el-button :loading="loading" @click="loadPlans">刷新方案库</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">方案总数</div>
        <div class="metric-value">{{ total }}</div>
        <div class="metric-note">当前门店维护的全部方案记录，包含启用与停用状态。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">启用方案</div>
        <div class="metric-value">{{ activeCount }}</div>
        <div class="metric-note">只有启用中的方案会进入农户端推荐列表。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">停用方案</div>
        <div class="metric-value">{{ inactiveCount }}</div>
        <div class="metric-note">可用于临时下线库存不足或不希望展示的方案。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">病症覆盖</div>
        <div class="metric-value">{{ diseaseCoverage }}</div>
        <div class="metric-note">建议优先补齐高频病症对应的方案，避免推荐结果过于单薄。</div>
      </article>
    </section>

    <section class="soft-card panel">
      <header class="panel-header">
        <div>
          <h3 class="section-title">门店方案库</h3>
          <p class="section-copy">方案摘要、产品、使用提示、风险提醒和库存状态会被原样带到推荐页中展示。</p>
        </div>
      </header>

      <div class="plan-list">
        <article v-for="plan in plans" :key="plan.id" class="plan-card">
          <div class="plan-head">
            <div>
              <span class="card-kicker">{{ plan.diseaseTag }} / {{ plan.stageTag }}</span>
              <strong>{{ plan.title }}</strong>
              <p>{{ plan.summary }}</p>
            </div>
            <el-tag :type="plan.active ? 'success' : 'info'" effect="plain">
              {{ plan.active ? '启用中' : '已停用' }}
            </el-tag>
          </div>

          <div class="content-grid">
            <section class="info-block">
              <h4>推荐产品</h4>
              <div class="pill-row">
                <span v-for="product in plan.products" :key="product" class="plan-pill">{{ product }}</span>
              </div>
            </section>

            <section class="info-block">
              <h4>使用提示</h4>
              <ul class="bullet-list">
                <li v-for="tip in plan.usageTips" :key="tip">{{ tip }}</li>
              </ul>
            </section>

            <section class="info-block">
              <h4>风险提醒</h4>
              <ul class="bullet-list">
                <li v-for="risk in plan.riskNotes" :key="risk">{{ risk }}</li>
              </ul>
            </section>

            <section class="info-block">
              <h4>库存状态</h4>
              <p class="inventory-text">{{ plan.inventoryStatus }}</p>
            </section>
          </div>

          <div class="action-row">
            <el-button @click="openEditDialog(plan)">编辑方案</el-button>
            <el-button type="danger" plain @click="removePlan(plan.id)">删除方案</el-button>
          </div>
        </article>
      </div>

      <div class="pagination-row" style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadPlans"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑方案' : '新增方案'" width="760px">
      <el-form label-position="top" :rules="formRules">
        <el-form-item label="方案标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：雨季炭疽病门店推荐方案" />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="病症标签" prop="diseaseTag">
            <el-select v-model="form.diseaseTag" placeholder="请选择病症标签">
              <el-option v-for="tag in diseaseTagOptions" :key="tag" :label="tag" :value="tag" />
            </el-select>
          </el-form-item>
          <el-form-item label="阶段标签" prop="stageTag">
            <el-input v-model="form.stageTag" placeholder="例如：雨季高湿 / 花果期 / 幼果期" />
          </el-form-item>
        </div>

        <el-form-item label="方案摘要" prop="summary">
          <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="用一段话说明该方案适合什么场景。" />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="推荐产品">
            <el-input v-model="productsText" type="textarea" :rows="5" placeholder="每行一条，适合直接展示在推荐卡中。" />
          </el-form-item>
          <el-form-item label="使用提示">
            <el-input v-model="usageTipsText" type="textarea" :rows="5" placeholder="每行一条，重点写清使用边界和操作提醒。" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="风险提醒">
            <el-input v-model="riskNotesText" type="textarea" :rows="5" placeholder="每行一条，例如安全间隔、轮换用药和复查建议。" />
          </el-form-item>
          <el-form-item label="库存状态">
            <el-select v-model="form.inventoryStatus" placeholder="请选择库存状态">
              <el-option v-for="item in inventoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="是否启用">
          <el-switch v-model="form.active" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="action-row">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存方案</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { shopAPI, type RemedyPlan } from '@/api'
import { DISEASE_TAG_OPTIONS, REMEDY_INVENTORY_OPTIONS } from '@/config/platform'

const router = useRouter()
const plans = ref<RemedyPlan[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref('')
const diseaseTagOptions = DISEASE_TAG_OPTIONS
const inventoryOptions = REMEDY_INVENTORY_OPTIONS
const defaultInventoryStatus = REMEDY_INVENTORY_OPTIONS[0] ?? '有现货'

const form = reactive({
  title: '',
  diseaseTag: '',
  stageTag: '',
  summary: '',
  inventoryStatus: defaultInventoryStatus,
  active: true
})
const productsText = ref('')
const usageTipsText = ref('')
const riskNotesText = ref('')

const formRules = {
  title: [
    { required: true, message: '请输入方案标题', trigger: 'blur' },
    { max: 100, message: '标题长度不超过 100 个字符', trigger: 'blur' }
  ],
  diseaseTag: [
    { required: true, message: '请选择病症标签', trigger: 'change' }
  ],
  stageTag: [
    { required: true, message: '请输入阶段标签', trigger: 'blur' },
    { max: 50, message: '阶段标签长度不超过 50 个字符', trigger: 'blur' }
  ],
  summary: [
    { required: true, message: '请输入方案摘要', trigger: 'blur' },
    { max: 500, message: '摘要长度不超过 500 个字符', trigger: 'blur' }
  ]
}

const activeCount = computed(() => plans.value.filter(item => item.active).length)
const inactiveCount = computed(() => plans.value.length - activeCount.value)
const diseaseCoverage = computed(() => new Set(plans.value.map(item => item.diseaseTag)).size)

const goTo = (path: string) => {
  router.push(path)
}

const splitLines = (value: string) =>
  value
    .split('\n')
    .map(item => item.trim())
    .filter(Boolean)

const resetForm = () => {
  editingId.value = ''
  form.title = ''
  form.diseaseTag = ''
  form.stageTag = ''
  form.summary = ''
  form.inventoryStatus = defaultInventoryStatus
  form.active = true
  productsText.value = ''
  usageTipsText.value = ''
  riskNotesText.value = ''
}

const fillForm = (plan: RemedyPlan) => {
  editingId.value = plan.id
  form.title = plan.title
  form.diseaseTag = plan.diseaseTag
  form.stageTag = plan.stageTag
  form.summary = plan.summary
  form.inventoryStatus = plan.inventoryStatus
  form.active = plan.active
  productsText.value = plan.products.join('\n')
  usageTipsText.value = plan.usageTips.join('\n')
  riskNotesText.value = plan.riskNotes.join('\n')
}

const loadPlans = async () => {
  loading.value = true
  try {
    const response = await shopAPI.plans(page.value, size.value)
    plans.value = response.data.items
    total.value = response.data.total
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载门店方案失败。')
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (plan: RemedyPlan) => {
  fillForm(plan)
  dialogVisible.value = true
}

const submitForm = async () => {
  saving.value = true
  try {
    const payload = {
      title: form.title,
      diseaseTag: form.diseaseTag,
      stageTag: form.stageTag,
      summary: form.summary,
      products: splitLines(productsText.value),
      usageTips: splitLines(usageTipsText.value),
      riskNotes: splitLines(riskNotesText.value),
      inventoryStatus: form.inventoryStatus,
      active: form.active
    }

    if (editingId.value) {
      await shopAPI.updatePlan(editingId.value, payload)
      ElMessage.success('方案已更新。')
    } else {
      await shopAPI.createPlan(payload)
      ElMessage.success('方案已创建。')
    }

    dialogVisible.value = false
    await loadPlans()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '保存方案失败。')
  } finally {
    saving.value = false
  }
}

const removePlan = async (id: string) => {
  try {
    await ElMessageBox.confirm('删除后该方案将不再参与推荐，确认继续吗？', '删除方案', {
      type: 'warning'
    })
    await shopAPI.deletePlan(id)
    ElMessage.success('方案已删除。')
    await loadPlans()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除方案失败。')
    }
  }
}

onMounted(() => {
  loadPlans()
})
</script>

<style scoped>
.plans-page {
  gap: 18px;
}

.hero,
.panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 14px;
}

.hero-kicker,
.card-kicker {
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
.plan-list {
  display: grid;
  gap: 14px;
}

.panel-header {
  margin-bottom: 18px;
}

.plan-card {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 22px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.82);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 32px rgba(31, 74, 63, 0.1);
  border-color: rgba(47, 106, 89, 0.16);
}

.plan-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.plan-head strong {
  display: block;
  margin-top: 10px;
  color: var(--ink-strong);
  font-size: 22px;
}

.plan-head p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.75;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.info-block {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(248, 246, 239, 0.92);
}

.info-block h4 {
  margin: 0;
  color: var(--ink-strong);
}

.bullet-list {
  display: grid;
  gap: 10px;
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--ink-soft);
  line-height: 1.7;
}

.plan-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.08);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.inventory-text {
  margin: 12px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

@media (max-width: 1180px) {
  .hero,
  .content-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .plan-head {
    flex-direction: column;
  }
}
</style>
