import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import KnowledgeView from '@/views/KnowledgeView.vue'
import FoodsView from '@/views/FoodsView.vue'
import ReviewView from '@/views/ReviewView.vue'
import AgentExecutionsView from '@/views/AgentExecutionsView.vue'
import SystemView from '@/views/SystemView.vue'

const routes = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'dashboard',
        component: DashboardView,
        meta: { title: '运营总览' }
      },
      {
        path: '/knowledge',
        name: 'knowledge',
        component: KnowledgeView,
        meta: { title: '知识库管理' }
      },
      {
        path: '/foods',
        name: 'foods',
        component: FoodsView,
        meta: { title: '食物库管理' }
      },
      {
        path: '/review',
        name: 'review',
        component: ReviewView,
        meta: { title: '内容审核' }
      },
      {
        path: '/agents',
        name: 'agents',
        component: AgentExecutionsView,
        meta: { title: 'Agent链路' }
      },
      {
        path: '/system',
        name: 'system',
        component: SystemView,
        meta: { title: '系统状态' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
