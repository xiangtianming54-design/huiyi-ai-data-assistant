<template>
  <div class="create-view">
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="page-title">新建账号</span>
      </template>
      <el-form class="create-form" label-width="72px" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item label="口令">
          <el-input v-model="password" type="password" show-password placeholder="口令（至少 10 位）" />
        </el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="tier">
            <el-radio v-for="choice in tierOptions" :key="choice" :value="choice">
              {{ tierLabel(choice) }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="displayName" placeholder="显示名（可空）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :disabled="!canSubmit" :loading="submitting">
            新建账号
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tierAccountApi } from '../api'
import { tierChoiceAllowed } from '../quota/quotaView'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const displayName = ref('')
const tier = ref(2)
const submitting = ref(false)

const tierOptions = [1, 2, 3].filter((choice) => tierChoiceAllowed(choice))

const canSubmit = computed(() =>
  username.value.trim().length > 0
  && password.value.length > 0
  && tierChoiceAllowed(tier.value)
  && auth.workspaceId !== ''
)

function tierLabel(choice: number): string {
  if (choice === 2) return '第二级'
  if (choice === 3) return '第三级'
  return String(choice)
}

async function submit() {
  if (!canSubmit.value || !tierChoiceAllowed(tier.value)) return
  submitting.value = true
  try {
    const response = await tierAccountApi.create(auth.workspaceId, {
      username: username.value.trim(),
      password: password.value,
      displayName: displayName.value.trim() || null,
      tier: tier.value
    })
    const created = response.data.data
    ElMessage.success(created?.username ? `已新建 ${created.username}` : '已新建')
    username.value = ''
    password.value = ''
    displayName.value = ''
    tier.value = 2
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-card {
  max-width: 480px;
  background: var(--huiyi-card);
  border: 1px solid var(--huiyi-line);
}

.page-card :deep(.el-card__header) {
  border-bottom: 1px solid var(--huiyi-line);
}

.create-form {
  padding-top: 4px;
}
</style>
