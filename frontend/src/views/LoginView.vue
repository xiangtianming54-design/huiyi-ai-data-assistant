<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <h2>会意</h2>
        <div class="login-rule"></div>
        <p>企业经营数据智能分析助手</p>
      </div>
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
  background: var(--huiyi-paper);
}
.login-card {
  width: 380px;
  background: var(--huiyi-card);
  border: 1px solid var(--huiyi-line);
  box-shadow: 0 16px 40px rgba(80, 60, 30, 0.06);
}
.login-brand {
  text-align: center;
  margin-bottom: 8px;
}
h2 {
  margin: 0;
  font-family: var(--huiyi-serif);
  font-size: 32px;
  font-weight: 400;
  letter-spacing: 0.28em;
  color: var(--huiyi-ink);
}
.login-rule {
  width: 36px;
  height: 1px;
  background: var(--huiyi-brass);
  margin: 12px auto 8px;
}
.login-brand p {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: var(--huiyi-muted);
}
</style>
