import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理到 Java 主服务；SSE 由 http-proxy 原生透传
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
