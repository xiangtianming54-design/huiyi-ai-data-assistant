<template>
  <router-view v-if="isLogin" />
  <el-container v-else class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="app-logo">
        <span class="app-logo-title">会意</span>
        <div class="app-logo-rule"></div>
        <span class="app-logo-sub">企业经营数据智能分析助手</span>
      </div>
      <el-menu :default-active="$route.path" router class="app-menu">
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能助手</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><FolderOpened /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <el-select
          :model-value="auth.workspaceId"
          placeholder="选择工作区"
          style="width: 240px"
          @change="onWorkspaceChange"
        >
          <el-option
            v-for="item in auth.workspaces"
            :key="item.workspaceId ?? item.id"
            :label="item.name"
            :value="String(item.workspaceId ?? item.id)"
          />
        </el-select>
        <span class="user-name">{{ auth.displayName || auth.username }}</span>
        <el-button text @click="onLogout">退出</el-button>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, FolderOpened } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isLogin = computed(() => route.path === '/login')

function onWorkspaceChange(id: string) {
  auth.switchWorkspace(Number(id))
}

async function onLogout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<style>
.app-layout {
  height: 100%;
  background: var(--huiyi-paper);
}

.app-aside {
  background: var(--huiyi-card);
  border-right: 1px solid var(--huiyi-line);
  display: flex;
  flex-direction: column;
}

.app-logo {
  padding: 28px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.app-logo-title {
  font-family: var(--huiyi-serif);
  color: var(--huiyi-ink);
  font-size: 26px;
  font-weight: 400;
  letter-spacing: 0.22em;
}

.app-logo-rule {
  width: 28px;
  height: 1px;
  background: var(--huiyi-brass);
}

.app-logo-sub {
  color: var(--huiyi-muted);
  font-size: 12px;
  letter-spacing: 0.04em;
  line-height: 1.5;
}

.app-menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--huiyi-muted);
  --el-menu-hover-bg-color: transparent;
  --el-menu-hover-text-color: var(--huiyi-ink);
  --el-menu-active-color: var(--huiyi-ink);
}

.app-menu .el-menu-item {
  border-left: 2px solid transparent;
  margin: 2px 0;
}

.app-menu .el-menu-item.is-active {
  background: transparent;
  border-left-color: var(--huiyi-brass);
}

.app-menu .el-menu-item:hover {
  background: transparent;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--huiyi-paper);
  border-bottom: 1px solid var(--huiyi-line);
}

.user-name {
  margin-left: auto;
  color: var(--huiyi-muted);
  letter-spacing: 0.04em;
}

.app-main {
  background: var(--huiyi-paper);
  padding: 24px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
