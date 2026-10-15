import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能问答' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识库管理' } },
    { path: '/analysis', name: 'analysis', component: () => import('../views/AnalysisView.vue'), meta: { title: '数据分析' } }
  ]
})

export default router
