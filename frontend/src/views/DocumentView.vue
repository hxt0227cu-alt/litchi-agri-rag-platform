<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h2>文档管理</h2>
        <p>上传文档后会自动保存、本地切块并加入问答检索。</p>
      </div>
      <el-button :icon="Refresh" @click="loadDocuments" :loading="loading">刷新列表</el-button>
    </header>

    <div class="layout">
      <el-card class="upload-card">
        <template #header>
          <div class="card-header">
            <span>上传知识文档</span>
            <el-tag type="info" effect="plain">支持 txt / md / csv / json / pdf / docx</el-tag>
          </div>
        </template>

        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :file-list="fileList"
          :limit="1"
          accept=".txt,.md,.csv,.json,.pdf,.docx"
          :on-change="handleFileChange"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽文件到这里，或 <em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              为保证答辩演示稳定，优先上传 txt 或 md 文档；pdf/docx 也会尝试解析。
            </div>
          </template>
        </el-upload>

        <el-alert
          v-if="resultAlert"
          :type="resultAlert.type"
          :title="resultAlert.message"
          show-icon
          :closable="false"
          class="result-alert"
        />

        <div class="upload-actions">
          <el-button type="primary" :loading="uploading" @click="handleUpload">
            上传并建立索引
          </el-button>
        </div>
      </el-card>

      <el-card class="list-card" v-loading="loading">
        <template #header>
          <div class="card-header">
            <span>已上传文档</span>
            <el-tag type="success" effect="plain">{{ documents.length }} 份</el-tag>
          </div>
        </template>

        <el-table :data="documents" empty-text="暂无文档，先上传一份知识资料。">
          <el-table-column prop="name" label="文档名" min-width="220" />
          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="切块数" width="100">
            <template #default="{ row }">
              {{ row.chunkCount }}
            </template>
          </el-table-column>
          <el-table-column label="索引状态" width="140">
            <template #default="{ row }">
              <el-tag :type="row.indexed ? 'success' : 'warning'" effect="plain">
                {{ row.indexed ? '已入库' : '仅存储' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="statusMessage" label="处理结果" min-width="220" />
          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.uploadTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'

import { documentAPI, type DocumentRecord } from '@/api'

type AlertType = 'success' | 'warning' | 'error'

const fileList = ref<UploadUserFile[]>([])
const documents = ref<DocumentRecord[]>([])
const uploading = ref(false)
const loading = ref(false)
const resultAlert = ref<{ type: AlertType; message: string } | null>(null)

const handleFileChange = (_file: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles.slice(-1) as UploadUserFile[]
  resultAlert.value = null
}

const handleUpload = async () => {
  const rawFile = fileList.value[0]?.raw as File | undefined
  if (!rawFile) {
    ElMessage.warning('请先选择要上传的文件。')
    return
  }

  uploading.value = true
  resultAlert.value = null

  const formData = new FormData()
  formData.append('file', rawFile)

  try {
    const response = await documentAPI.upload(formData)
    const document = response.data
    resultAlert.value = {
      type: document.indexed ? 'success' : 'warning',
      message: `${document.name}：${document.statusMessage}`
    }
    ElMessage.success('文档上传完成。')
    fileList.value = []
    await loadDocuments()
  } catch (error) {
    resultAlert.value = {
      type: 'error',
      message: '上传失败，请检查后端服务和文件格式。'
    }
    ElMessage.error('上传失败。')
  } finally {
    uploading.value = false
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const response = await documentAPI.list()
    documents.value = response.data
  } catch (error) {
    ElMessage.error('加载文档列表失败。')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm('删除后将无法继续在问答中引用该文档，是否继续？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await documentAPI.delete(id)
    ElMessage.success(response.data.message)
    await loadDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败。')
    }
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / Math.pow(1024, index)).toFixed(2)} ${units[index]}`
}

const formatDate = (value: string) => {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.upload-card,
.list-card {
  border-radius: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.upload-icon {
  font-size: 30px;
  color: #2563eb;
}

.result-alert {
  margin-top: 16px;
}

.upload-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.list-card :deep(.el-card__body) {
  padding-top: 8px;
}

@media (max-width: 1100px) {
  .page {
    padding: 16px;
  }

  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
