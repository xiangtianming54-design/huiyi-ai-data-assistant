<template>
  <router-view v-if="isLogin" />
  <el-container v-else class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="app-logo">
        <span class="app-logo-title">会意</span>
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
html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}

.app-layout {
  height: 100%;
}

.app-aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}

.app-logo {
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-logo-title {
  color: #fff;
  font-size: 22px;
  font-weight: 600;
}

.app-logo-sub {
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
}

.app-menu {
  border-right: none;
  background: transparent;
}

.app-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.75);
}

.app-menu .el-menu-item.is-active,
.app-menu .el-menu-item:hover {
  background: #1890ff;
  color: #fff;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.user-name {
  margin-left: auto;
  color: #606266;
}

.app-main {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
</style>
