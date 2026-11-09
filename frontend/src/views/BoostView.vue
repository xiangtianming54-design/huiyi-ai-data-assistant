<template>
  <div class="boost-view">
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="page-title">提额</span>
      </template>
      <p class="tip">今天记下 Token 的句子。标「最多」的是 Token 最多的一句。确认后提交选中的一句。</p>
      <el-table
        v-loading="loading"
        :data="sentences"
        empty-text="今天还没有记下 Token 的句子"
        @row-click="onRow"
      >
        <el-table-column width="56">
          <template #default="{ row }">
            <el-radio v-model="selectedRunId" :value="row.runId" />
          </template>
        </el-table-column>
        <el-table-column prop="question" label="句子" min-width="240" show-overflow-tooltip />
        <el-table-column prop="totalTokens" label="Token" width="120" />
        <el-table-column width="80">
          <template #default="{ row }">
            <span v-if="row.heaviest" class="heaviest">最多</span>
          </template>
        </el-table-column>
      </el-table>
      <el-button
        class="confirm"
        type="primary"
        :disabled="!selectedRunId"
        :loading="submitting"
        @click="confirm"
      >
        确认
      </el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { quotaApi, type SentenceUsage } from '../api'

const sentences = ref<SentenceUsage[]>([])
const selectedRunId = ref('')
const loading = ref(false)
const submitting = ref(false)

onMounted(() => {
  void load()
})

async function load() {
  loading.value = true
  try {
    const response = await quotaApi.sentences()
    sentences.value = response.data.data ?? []
    const marked = sentences.value.find((row) => row.heaviest)
    selectedRunId.value = marked?.runId ?? ''
  } finally {
    loading.value = false
  }
}

function onRow(row: SentenceUsage) {
  selectedRunId.value = row.runId
}

async function confirm() {
  if (!selectedRunId.value) return
  submitting.value = true
  try {
    await quotaApi.submitBoost(selectedRunId.value)
    ElMessage.success('已提交')
    await load()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-card {
  background: transparent;
  border: none;
}

.page-card :deep(.el-card__header) {
  border-bottom: none;
  padding-left: 0;
  padding-right: 0;
}

.tip {
  margin: 0 0 12px;
  padding: 8px 10px;
  border: 1px solid var(--huiyi-line);
  border-radius: 6px;
  background: var(--huiyi-card);
  color: var(--huiyi-muted);
  font-size: 12px;
  line-height: 1.6;
}

.page-card :deep(.el-table) {
  border: 1px solid var(--huiyi-line);
  border-radius: 8px;
}

.heaviest {
  color: var(--huiyi-brass);
  letter-spacing: 0.08em;
}

.confirm {
  margin-top: 16px;
}
</style>
