<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>会意 · 登录</h2>
      <el-tabs v-model="mode">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" show-password placeholder="密码（至少 10 位）" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'">
          <el-input v-model="displayName" placeholder="显示名（可选）" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          {{ mode === 'login' ? '登录' : '注册并进入' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const mode = ref('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.register(username.value, password.value, displayName.value || username.value)
    } else {
      await auth.login(username.value, password.value)
    }
    await router.replace('/chat')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.login-card {
  width: 380px;
}
h2 {
  margin: 0 0 12px;
  text-align: center;
}
</style>
