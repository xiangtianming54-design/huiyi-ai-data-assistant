import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken, getRefreshToken } from '../auth/token'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能助手' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识库管理' } },
    { path: '/analysis', redirect: '/chat' }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  if (!getAccessToken() && !getRefreshToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const auth = useAuthStore()
  if (!auth.username && getRefreshToken()) {
    try {
      if (!getAccessToken()) {
        // interceptor refresh happens on first API; loadProfile will 401 then retry
      }
      await auth.loadProfile()
    } catch {
      return { path: '/login' }
    }
  }
  return true
})

export default router
