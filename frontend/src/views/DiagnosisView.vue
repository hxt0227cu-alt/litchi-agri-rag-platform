<template>
  <div class="diagnosis-page page-shell">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Diagnosis Flow</span>
        <h3 class="section-title">病害识别</h3>
        <p class="section-copy">
          上传病斑图片后，系统会先尝试用智能模型直接识别；模型不可用时，会自动改用内置病虫害知识库来匹配判断。
          识别结果会直接生成病症标签，并作为进入解决方案页的第一跳依据。
        </p>
      </div>

      <div class="engine-card">
        <span>当前引擎</span>
        <strong>{{ diagnosisEngine }}</strong>
        <p>{{ diagnosisModeText }}</p>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">样图数量</div>
        <div class="metric-value">{{ samples.length }}</div>
        <div class="metric-note">可直接使用内置样图快速完成演示，不依赖现场临时准备图片。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">识别模式</div>
        <div class="metric-value compact">{{ health?.diagnosisDetails.modelLoaded ? '模型推理' : '回退保障' }}</div>
        <div class="metric-note">用来说明系统在服务波动时仍能维持“可讲、可演示、可落地”的能力。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">结果出口</div>
        <div class="metric-value">解决方案</div>
        <div class="metric-note">识别完成后可直接带病症标签进入方案推荐页继续协同。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">结果状态</div>
        <div class="metric-value">{{ result ? '已生成' : '待识别' }}</div>
        <div class="metric-note">结果区会展示主预测、候选类别、建议和引擎模式。</div>
      </article>
    </section>

    <section class="sample-section soft-card">
      <header class="panel-header">
        <div>
          <h3 class="section-title">内置样图</h3>
          <p class="section-copy">点击任意样图会自动填入预览和待识别文件，便于快速验证识别流程。</p>
        </div>
      </header>

      <div class="sample-grid">
        <button v-for="sample in samples" :key="sample.title" type="button" class="sample-card" @click="selectSample(sample)">
          <img :src="sample.url" :alt="sample.title" />
          <div>
            <strong>{{ sample.title }}</strong>
            <p>{{ sample.description }}</p>
          </div>
        </button>
      </div>
    </section>

    <section class="content-grid">
      <article class="soft-card upload-panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">上传或选择图片</h3>
            <p class="section-copy">建议选择病斑清晰、光线均匀的叶片或果实图片，结果会更稳定。</p>
          </div>
        </header>

        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          accept="image/*"
          :on-change="handleFileChange"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽病叶或果实图片到这里，或 <em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">如果现场不方便上传图片，也可以直接使用上方样图进行演示。</div>
          </template>
        </el-upload>

        <div v-if="imageUrl" class="preview">
          <img :src="imageUrl" alt="待识别预览图" />
        </div>

        <div class="action-row">
          <el-button type="primary" :disabled="!file" :loading="isLoading" @click="handleDiagnosis">开始识别</el-button>
          <el-button v-if="result" @click="openSolutions">查看解决方案</el-button>
        </div>
      </article>

      <article class="glass-card result-panel" v-loading="isLoading">
        <header class="panel-header">
          <div>
            <h3 class="section-title">识别结果</h3>
            <p class="section-copy">结果区会展示主预测、候选类别、引擎模式以及可继续进入方案页的出口。</p>
          </div>
        </header>

        <el-empty v-if="!result" description="选择一张样图或上传图片后开始识别。" />

        <div v-else class="result-content">
          <div class="result-summary">
            <div>
              <span>主预测结果</span>
              <strong>{{ result.disease }}</strong>
            </div>
            <div>
              <span>置信度</span>
              <strong>{{ (result.confidence * 100).toFixed(2) }}%</strong>
            </div>
          </div>

          <el-alert v-if="result.note" type="info" show-icon :closable="false" :title="result.note" />

          <section class="result-section">
            <h4>识别建议</h4>
            <ul>
              <li v-for="suggestion in result.suggestions" :key="suggestion">{{ suggestion }}</li>
            </ul>
          </section>

          <section class="result-section">
            <h4>识别引擎</h4>
            <div class="pill-row">
              <span class="result-pill">{{ result.engine ?? 'unknown' }}</span>
              <span class="result-pill">{{ result.demoMode ? '保障模式' : '模型模式' }}</span>
            </div>
          </section>

          <section v-if="result.diseases?.length" class="result-section">
            <h4>候选类别</h4>
            <div class="candidate-list">
              <div v-for="disease in result.diseases" :key="disease.name" class="candidate-card">
                <strong>{{ disease.name }}</strong>
                <span>{{ (disease.confidence * 100).toFixed(1) }}%</span>
              </div>
            </div>
          </section>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

import { diagnosisAPI, systemAPI, type DiagnosisResult, type SystemHealthResponse } from '@/api'

type SampleCard = {
  title: string
  description: string
  url: string
  fileName: string
}

const samples: SampleCard[] = [
  {
    title: '健康叶片',
    description: '用于验证系统对正常样本的基础识别与说明能力。',
    url: '/demo/healthy-demo.jpg',
    fileName: 'healthy-demo.jpg'
  },
  {
    title: '炭疽病样图',
    description: '适合演示真菌性病害识别和结果带标签进入方案页的过程。',
    url: '/demo/anthracnose-demo.jpg',
    fileName: 'anthracnose-demo.jpg'
  },
  {
    title: '霜疫霉病样图',
    description: '适合演示雨季高湿场景下的病害识别与建议说明。',
    url: '/demo/blight-demo.jpg',
    fileName: 'blight-demo.jpg'
  },
  {
    title: '红锈病样图',
    description: '用于补充展示候选类别和多病症样本识别表现。',
    url: '/demo/rust-demo.jpg',
    fileName: 'rust-demo.jpg'
  }
]

const imageUrl = ref('')
const isLoading = ref(false)
const file = ref<File | null>(null)
const result = ref<DiagnosisResult | null>(null)
const health = ref<SystemHealthResponse | null>(null)
const router = useRouter()

const diagnosisEngine = computed(() => health.value?.diagnosisDetails.engine ?? 'loading...')
const diagnosisModeText = computed(() =>
  health.value?.diagnosisDetails.modelLoaded
    ? '当前已启用模型推理，可展示真实识别链路。'
    : '当前未启用模型推理服务，系统会自动使用内置病虫害知识库来匹配判断。'
)

const updatePreview = (rawFile: File) => {
  const reader = new FileReader()
  reader.onload = event => {
    imageUrl.value = String(event.target?.result ?? '')
  }
  reader.readAsDataURL(rawFile)
}

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/bmp']
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

const handleFileChange = (fileObj: { raw?: File }) => {
  const rawFile = fileObj.raw
  if (!rawFile) {
    return
  }

  if (!ALLOWED_IMAGE_TYPES.includes(rawFile.type)) {
    ElMessage.warning('仅支持 JPG、PNG、WebP、BMP 格式的图片。')
    return false
  }
  if (rawFile.size > MAX_IMAGE_SIZE) {
    ElMessage.warning('图片大小不能超过 10MB。')
    return false
  }

  file.value = rawFile
  result.value = null
  updatePreview(rawFile)
}

const selectSample = async (sample: SampleCard) => {
  try {
    const response = await fetch(sample.url)
    const blob = await response.blob()
    const selectedFile = new File([blob], sample.fileName, { type: blob.type || 'image/jpeg' })
    file.value = selectedFile
    result.value = null
    imageUrl.value = sample.url
    ElMessage.success(`已选择样图：${sample.title}`)
  } catch {
    ElMessage.error('加载内置样图失败。')
  }
}

const handleDiagnosis = async () => {
  if (!file.value) {
    return
  }

  isLoading.value = true
  const formData = new FormData()
  formData.append('file', file.value)

  try {
    const response = await diagnosisAPI.upload(formData)
    result.value = response.data
    ElMessage.success('识别完成。')
  } catch {
    ElMessage.error('识别失败，请检查后端识别服务。')
  } finally {
    isLoading.value = false
  }
}

const loadHealth = async () => {
  try {
    const response = await systemAPI.health()
    health.value = response.data
  } catch {
    ElMessage.error('获取识别服务状态失败。')
  }
}

const openSolutions = () => {
  if (!result.value) {
    return
  }

  router.push({
    path: '/solutions',
    query: {
      diseaseTag: result.value.disease,
      question: `我在病害识别中识别到了 ${result.value.disease}，请给我推荐适合的门店方案。`
    }
  })
}

onMounted(() => {
  loadHealth()
})
</script>

<style scoped>
.diagnosis-page {
  gap: 18px;
}

.hero,
.sample-section,
.upload-panel,
.result-panel {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
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
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.engine-card {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(31, 74, 63, 0.94), rgba(28, 58, 50, 0.96));
  color: #fff4d4;
}

.engine-card span {
  color: rgba(255, 244, 212, 0.72);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.engine-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
}

.engine-card p {
  margin: 12px 0 0;
  line-height: 1.8;
}

.panel-header {
  margin-bottom: 18px;
}

.compact {
  font-size: 20px;
  line-height: 1.45;
}

.sample-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.sample-card {
  display: grid;
  gap: 14px;
  padding: 14px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.sample-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(31, 74, 63, 0.08);
}

.sample-card img {
  width: 100%;
  aspect-ratio: 1.2;
  object-fit: cover;
  border-radius: 16px;
}

.sample-card strong {
  color: var(--ink-strong);
}

.sample-card p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
  font-size: 13px;
}

.content-grid {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 18px;
}

.upload-icon {
  font-size: 30px;
  color: var(--primary-main);
}

.preview {
  margin-top: 18px;
  overflow: hidden;
  border-radius: 22px;
}

.preview img {
  display: block;
  width: 100%;
  max-height: 360px;
  object-fit: cover;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
}

.result-content {
  display: grid;
  gap: 18px;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.result-summary > div {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.result-summary span {
  display: block;
  color: var(--ink-soft);
  font-size: 13px;
}

.result-summary strong {
  display: block;
  margin-top: 10px;
  color: var(--ink-strong);
  font-size: 26px;
}

.result-section h4 {
  margin: 0 0 10px;
  color: var(--ink-strong);
}

.result-section ul {
  margin: 0;
  padding-left: 18px;
  color: var(--ink-strong);
  line-height: 1.8;
}

.result-pill {
  display: inline-flex;
  align-items: center;
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(47, 106, 89, 0.08);
  color: var(--primary-deep);
  font-size: 13px;
  font-weight: 700;
}

.candidate-list {
  display: grid;
  gap: 10px;
}

.candidate-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(34, 53, 47, 0.06);
}

.candidate-card strong {
  color: var(--ink-strong);
}

.candidate-card span {
  color: var(--ink-soft);
}

@media (max-width: 1180px) {
  .hero,
  .sample-grid,
  .content-grid,
  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
