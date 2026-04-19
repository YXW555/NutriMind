<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const pageTitle = computed(() => route.meta.title || '管理台')
const search = ref('')
</script>

<template>
  <header class="topbar">
    <div>
      <p class="topbar-kicker">NutriMind Admin</p>
      <h2 class="topbar-title">{{ pageTitle }}</h2>
    </div>

    <div class="topbar-tools">
      <label class="search-shell">
        <span class="search-icon">搜</span>
        <input v-model="search" class="search-input" placeholder="搜索页面内容" />
      </label>
      <span class="icon-chip">通</span>
      <span class="icon-chip">告</span>
      <div class="profile-chip">
        <div class="profile-avatar">YX</div>
        <div>
          <p class="profile-name">管理员</p>
          <p class="profile-role">NutriMind Studio</p>
        </div>
      </div>
    </div>
  </header>
</template>
