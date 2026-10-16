<template>
  <div class="knowledge-view">
    <el-card shadow="never">
      <template #header>
        <div class="view-header">
          <span>知识库管理</span>
          <el-upload
            :show-file-list="false"
            accept=".md,.txt,.pdf,.docx"
            :http-request="handleUpload"
          >
            <el-button type="primary" :loading="uploading">上传文档</el-button>
          </el-upload>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        title="支持 Markdown / TXT / PDF / Word，单文件不超过 10MB；上传后自动解析分块并写入向量库。"
        class="tip"
      />

      <el-table v-loading="loading" :data="docs" empty-text="暂无文档，请先上传">
        <el-table-column prop="docName" label="文档名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" text @click="confirmDelete(row)">归档</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi, type KnowledgeDoc } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
watch(() => auth.workspaceId, () => { void loadDocs() })

const docs = ref<KnowledgeDoc[]>([])
const loading = ref(false)
const uploading = ref(false)

onMounted(loadDocs)

async function loadDocs() {
  loading.value = true
  try {
    const response = await knowledgeApi.list()
    docs.value = response.data.data
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: { file: File }) {
  const file = options.file
  if (!/\.(md|txt|pdf|docx)$/i.test(file.name)) {
    ElMessage.warning('仅支持 md / txt / pdf / docx 格式')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 10MB')
    return
  }
  uploading.value = true
  try {
    const response = await knowledgeApi.upload(file)
    ElMessage.success(`解析完成：${response.data.data.chunkCount} 个分块已就绪`)
    await loadDocs()
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    uploading.value = false
  }
}

async function confirmDelete(doc: KnowledgeDoc) {
  await ElMessageBox.confirm(`确定归档「${doc.docName}」吗？文档记录保留，向量将不可再检索。`, '归档确认', {
    type: 'warning',
    confirmButtonText: '归档',
    cancelButtonText: '取消'
  })
  await knowledgeApi.remove(doc.docId)
  ElMessage.success('已归档')
  await loadDocs()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(iso: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 19) : '-'
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    UPLOADED: '已上传',
    PARSED: '已解析',
    READY: '可检索',
    FAILED: '失败',
    ARCHIVED: '已归档'
  }
  return map[status] ?? status
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    UPLOADED: 'info',
    PARSED: 'warning',
    READY: 'success',
    FAILED: 'danger'
  }
  return map[status] ?? 'info'
}
</script>

<style scoped>
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.tip {
  margin-bottom: 12px;
}
</style>
