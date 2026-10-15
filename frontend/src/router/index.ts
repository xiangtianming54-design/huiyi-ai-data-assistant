import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能助手' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识库管理' } },
    // v0.2 统一对话入口：原数据分析页已并入智能助手
    { path: '/analysis', redirect: '/chat' }
  ]
})

export default router
