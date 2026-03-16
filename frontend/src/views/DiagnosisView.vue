<template>
  <div class="diagnosis-page page-shell">
    <section class="hero glass-card">
      <div>
        <h3 class="section-title">病害识别演示区</h3>
        <p class="section-copy">
          当前识别服务支持两种模式：有权重时走 YOLO 推理，没有权重时自动切换为数据集特征匹配或规则兜底。你可以直接使用内置样图完成答辩演示。
        </p>
      </div>

      <div class="engine-card">
        <span>当前引擎</span>
        <strong>{{ diagnosisEngine }}</strong>
        <p>{{ diagnosisModeText }}</p>
      </div>
    </section>

    <section class="sample-section soft-card">
      <header class="sample-header">
        <div>
          <h3 class="section-title">内置样图</h3>
          <p class="section-copy">点击任意样图会自动填充预览和待识别文件，现场演示会更顺畅。</p>
        </div>
      </header>

      <div class="sample-grid">
        <button
          v-for="sample in samples"
          :key="sample.title"
          type="button"
          class="sample-card"
          @click="selectSample(sample)"
        >
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
            <p class="section-copy">建议选择叶片或果实病斑清晰的图片，以便更稳定地展示识别结果。</p>
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
            <div class="el-upload__tip">如果现场网络或模型不稳定，也可以直接使用上方内置样图完成演示。</div>
          </template>
        </el-upload>

        <div v-if="imageUrl" class="preview">
          <img :src="imageUrl" alt="待识别预览图" />
        </div>

        <div class="action-row">
          <el-button type="primary" :disabled="!file" :loading="isLoading" @click="handleDiagnosis">
            开始识别
          </el-button>
        </div>
      </article>

      <article class="glass-card result-panel" v-loading="isLoading">
        <header class="panel-header">
          <div>
            <h3 class="section-title">识别结果</h3>
            <p class="section-copy">结果区域会展示主预测、候选类别以及对应处理建议，方便你现场讲解。</p>
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

          <el-alert
            v-if="result.note"
            type="info"
            show-icon
            :closable="false"
            :title="result.note"
          />

          <section class="result-section">
            <h4>防治建议</h4>
            <ul>
              <li v-for="suggestion in result.suggestions" :key="suggestion">{{ suggestion }}</li>
            </ul>
          </section>

          <section class="result-section">
            <h4>识别引擎</h4>
            <div class="pill-row">
              <span class="result-pill">{{ result.engine ?? 'unknown' }}</span>
              <span class="result-pill">{{ result.demoMode ? '演示模式' : '模型模式' }}</span>
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
    description: '用于演示系统对正常样本的基础识别能力。',
    url: '/demo/healthy-demo.jpg',
    fileName: 'healthy-demo.jpg'
  },
  {
    title: '炭疽病样图',
    description: '适合演示疑似真菌病害的识别与防治建议输出。',
    url: '/demo/anthracnose-demo.jpg',
    fileName: 'anthracnose-demo.jpg'
  },
  {
    title: '霜疫霉病样图',
    description: '用于演示雨季病害场景下的识别与建议。',
    url: '/demo/blight-demo.jpg',
    fileName: 'blight-demo.jpg'
  },
  {
    title: '红锈病样图',
    description: '可作为补充样本展示多类别候选结果。',
    url: '/demo/rust-demo.jpg',
    fileName: 'rust-demo.jpg'
  }
]

const imageUrl = ref('')
const isLoading = ref(false)
const file = ref<File | null>(null)
const result = ref<DiagnosisResult | null>(null)
const health = ref<SystemHealthResponse | null>(null)

const diagnosisEngine = computed(() => health.value?.diagnosisDetails.engine ?? 'loading...')
const diagnosisModeText = computed(() =>
  health.value?.diagnosisDetails.modelLoaded
    ? '当前已加载 YOLO 模型，可以展示真实推理链路。'
    : '当前未加载 YOLO 权重，系统会自动回退到数据集特征匹配或规则模式。'
)

const updatePreview = (rawFile: File) => {
  const reader = new FileReader()
  reader.onload = event => {
    imageUrl.value = String(event.target?.result ?? '')
  }
  reader.readAsDataURL(rawFile)
}

const handleFileChange = (fileObj: { raw?: File }) => {
  const rawFile = fileObj.raw
  if (!rawFile) {
    return
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
  } catch (error) {
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
  } catch (error) {
    ElMessage.error('识别失败，请检查后端识别接口。')
  } finally {
    isLoading.value = false
  }
}

const loadHealth = async () => {
  try {
    const response = await systemAPI.health()
    health.value = response.data
  } catch (error) {
    ElMessage.error('获取识别服务状态失败。')
  }
}

onMounted(() => {
  loadHealth()
})
</script>

<style scoped>
.diagnosis-page {
  gap: 18px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  padding: 24px 26px;
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

.sample-section,
.upload-panel {
  padding: 22px;
}

.sample-header,
.panel-header {
  margin-bottom: 18px;
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

.result-panel {
  padding: 22px;
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
  .content-grid,
  .sample-grid {
    grid-template-columns: 1fr;
  }
}
</style>
