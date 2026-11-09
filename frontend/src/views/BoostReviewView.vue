<template>
  <div class="review-view">
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="page-title">审评</span>
      </template>
      <el-table v-loading="loading" :data="reviews" empty-text="没有待审申请">
        <el-table-column prop="applicant" label="申请人" width="140" show-overflow-tooltip />
        <el-table-column prop="reason" label="理由" min-width="220" show-overflow-tooltip />
        <el-table-column prop="stepName" label="步骤名" width="160" show-overflow-tooltip />
        <el-table-column prop="stepTokens" label="步骤 Token" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text :disabled="deciding" @click="decide(row.id, 'APPROVED')">
              通过
            </el-button>
            <el-button size="small" type="danger" text :disabled="deciding" @click="decide(row.id, 'REJECTED')">
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { quotaApi, type BoostReview } from '../api'

const reviews = ref<BoostReview[]>([])
const loading = ref(false)
const deciding = ref(false)

onMounted(() => {
  void load()
})

async function load() {
  loading.value = true
  try {
    const response = await quotaApi.pendingBoosts()
    reviews.value = response.data.data ?? []
  } finally {
    loading.value = false
  }
}

async function decide(id: number, decision: 'APPROVED' | 'REJECTED') {
  deciding.value = true
  try {
    await quotaApi.decideBoost(id, decision)
    await load()
  } finally {
    deciding.value = false
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

.page-card :deep(.el-table) {
  border: 1px solid var(--huiyi-line);
  border-radius: 8px;
}
</style>
