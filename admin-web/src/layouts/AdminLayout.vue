<script setup>
import SidebarNav from '@/components/SidebarNav.vue'
import TopBar from '@/components/TopBar.vue'

const navItems = [
  { to: '/dashboard', label: '运营总览', hint: '核心数据与趋势', icon: '总' },
  { to: '/knowledge', label: '知识库管理', hint: '维护知识来源与内容', icon: '知' },
  { to: '/foods', label: '食物库管理', hint: '维护营养数据与分类', icon: '食' },
  { to: '/agents', label: 'Agent链路', hint: '查看执行阶段与依据', icon: '链' },
  { to: '/review', label: '内容审核', hint: '处理帖子与评论', icon: '审' },
  { to: '/system', label: '系统状态', hint: '查看服务与模型状态', icon: '系' }
]
</script>

<template>
  <div class="admin-shell">
    <SidebarNav :items="navItems" />
    <main class="admin-main">
      <TopBar />
      <router-view />
    </main>
  </div>
</template>
