<script setup lang="ts">
import { ElContainer, ElAside, ElMain, ElHeader } from 'element-plus'
import 'element-plus/dist/index.css'

const nav = [
  { to: { name: 'admin-overview' }, label: '概览', icon: '📊' },
  { to: { name: 'admin-subscriptions' }, label: '订阅统计', icon: '⭐' },
  { to: { name: 'admin-hotness' }, label: '热度榜单', icon: '🔥' },
]
</script>

<template>
  <ElContainer class="admin-shell" style="min-height: 100vh">
    <ElHeader class="topbar">
      <div class="topbar__in">
        <router-link class="brand" :to="{ name: 'home' }"><span class="glyph">B</span> 机场巴士信息 · 后台</router-link>
      </div>
    </ElHeader>
    <ElContainer>
      <ElAside width="210px">
        <nav class="adminNav">
          <div class="role">Admin · 控制台</div>
          <router-link
            v-for="item in nav"
            :key="item.label"
            :to="item.to"
            class="admin-navlink"
            active-class="active"
          >{{ item.icon }} {{ item.label }}</router-link>
        </nav>
      </ElAside>
      <ElMain>
        <router-view />
      </ElMain>
    </ElContainer>
  </ElContainer>
</template>

<style scoped>
/* 复用 tokens.css 的 .adminNav 视觉(SoT);router-link 用 .admin-navlink 套同款样式 */
.admin-navlink {
  display: flex; align-items: center; gap: 9px; padding: 10px 12px;
  border-radius: 9px; text-decoration: none; color: var(--ink-soft);
  font-size: 14px; font-weight: 600; margin-bottom: 2px;
}
.admin-navlink:hover { background: var(--paper); }
.admin-navlink.active { background: var(--info-soft); color: var(--brand); }
</style>
