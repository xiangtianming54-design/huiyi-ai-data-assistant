<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="app-aside">
      <div class="app-logo">
        <span class="app-logo-title">会意</span>
        <span class="app-logo-sub">企业经营数据智能分析助手</span>
      </div>
      <el-menu :default-active="$route.path" router class="app-menu">
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能问答</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><FolderOpened /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据分析</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ChatDotRound, DataAnalysis, FolderOpened } from '@element-plus/icons-vue'
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

.app-main {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
</style>
