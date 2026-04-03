<script setup>
defineProps({
  items: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <aside class="sidebar">
    <div class="brand-card">
      <div class="brand-mark">N</div>
      <div>
        <p class="brand-kicker">NutriMind</p>
        <h1 class="brand-title">知食分子</h1>
        <p class="brand-desc">营养健康管理后台</p>
      </div>
    </div>

    <nav class="nav-list">
      <router-link
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        active-class="active"
      >
        <span class="nav-icon">{{ item.icon }}</span>
        <span class="nav-copy">
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-hint">{{ item.hint }}</span>
        </span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <div class="sidebar-link">设置</div>
      <div class="sidebar-link">帮助中心</div>
    </div>
  </aside>
</template>