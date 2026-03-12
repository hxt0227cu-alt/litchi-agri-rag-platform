<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>拍照识病</h2>
        <p>当前为可演示规则识别链路，接口字段已与后端统一为 `file -> disease / suggestions`。</p>
      </div>
    </header>

    <div class="content">
      <el-card class="upload-card">
        <template #header>
          <div class="card-header">
            <span>上传图片</span>
            <el-tag type="info" effect="plain">jpg / png / jpeg</el-tag>
          </div>
        </template>

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
            <div class="el-upload__tip">建议上传清晰的病斑特写图，识别结果仅供演示。</div>
          </template>
        </el-upload>

        <div v-if="imageUrl" class="preview">
          <el-image :src="imageUrl" fit="cover" />
        </div>

        <div class="action-row">
          <el-button type="primary" :icon="Camera" :disabled="!file" :loading="isLoading" @click="handleDiagnosis">
            开始识别
          </el-button>
        </div>
      </el-card>

      <el-card class="result-card" v-loading="isLoading">
        <template #header>
          <div class="card-header">
            <span>识别结果</span>
            <el-tag v-if="result" :type="result.demoMode ? 'warning' : 'success'" effect="plain">
              {{ result.demoMode ? '演示规则识别' : '模型推理' }}
            </el-tag>
          </div>
        </template>

        <el-empty v-if="!result" description="上传图片后开始识别。" />

        <div v-else class="result-content">
          <el-alert
            type="warning"
            show-icon
            :title="`识别结果：${result.disease}`"
            :description="`置信度：${(result.confidence * 100).toFixed(2)}%`"
          />

          <el-alert
            v-if="result.note"
            type="info"
            :title="result.note"
            show-icon
            :closable="false"
          />

          <section class="section">
            <h3>防治建议</h3>
            <ul>
              <li v-for="suggestion in result.suggestions" :key="suggestion">{{ suggestion }}</li>
            </ul>
          </section>

          <section v-if="result.engine" class="section">
            <h3>推理引擎</h3>
            <el-tag effect="plain">{{ result.engine }}</el-tag>
          </section>

          <section v-if="result.diseases?.length" class="section">
            <h3>候选病害</h3>
            <div class="candidate-list">
              <div v-for="disease in result.diseases" :key="disease.name" class="candidate-card">
                <strong>{{ disease.name }}</strong>
                <span>{{ (disease.confidence * 100).toFixed(1) }}%</span>
              </div>
            </div>
          </section>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, UploadFilled } from '@element-plus/icons-vue'

import { diagnosisAPI, type DiagnosisResult } from '@/api'

const imageUrl = ref('')
const isLoading = ref(false)
const file = ref<File | null>(null)
const result = ref<DiagnosisResult | null>(null)

const handleFileChange = (fileObj: { raw?: File }) => {
  const rawFile = fileObj.raw
  if (!rawFile) {
    return
  }

  file.value = rawFile
  result.value = null

  const reader = new FileReader()
  reader.onload = event => {
    imageUrl.value = String(event.target?.result ?? '')
  }
  reader.readAsDataURL(rawFile)
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
    ElMessage.error('识别失败，请检查后端识病接口。')
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.upload-card,
.result-card {
  border-radius: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-icon {
  font-size: 30px;
  color: #2563eb;
}

.preview {
  margin-top: 18px;
  border-radius: 18px;
  overflow: hidden;
}

.preview :deep(.el-image) {
  width: 100%;
  height: 280px;
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

.section h3 {
  margin: 0 0 10px;
}

.section ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 8px;
}

.candidate-list {
  display: grid;
  gap: 10px;
}

.candidate-card {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

@media (max-width: 1080px) {
  .page {
    padding: 16px;
  }

  .content {
    grid-template-columns: 1fr;
  }
}
</style>
