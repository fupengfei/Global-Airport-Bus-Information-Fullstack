<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../stores/auth'
import 'element-plus/dist/index.css'

const auth = useAuth()
const router = useRouter()

const nav = [
  { to: { name: 'admin-overview' }, label: '概览', icon: '📊' },
  { to: { name: 'admin-subscriptions' }, label: '订阅统计', icon: '⭐' },
  { to: { name: 'admin-hotness' }, label: '热度榜单', icon: '🔥' },
  { to: { name: 'admin-buses' }, label: '巴士维护', icon: '🚌' },
  { to: { name: 'admin-audit' }, label: '操作记录', icon: '🧾' },
  { to: { name: 'admin-corrections' }, label: '纠错队列', icon: '⚠️' },
  { to: { name: 'admin-tickets' }, label: '工单队列', icon: '💬' },
]

const adminName = computed(() => auth.user?.username ?? 'admin')
const adminRole = computed(() => auth.user?.role ?? '')
const initial = computed(() => adminName.value.slice(0, 1).toUpperCase())

async function logout() {
  await auth.logout()
  router.push({ name: 'admin-login' })
}
</script>

<template>
  <header class="topbar">
    <div class="adminBar">
      <router-link class="brand" :to="{ name: 'home' }"><span class="glyph">B</span> 机场巴士信息 · 后台</router-link>
      <div class="actions">
        <router-link class="backLink" :to="{ name: 'home' }">← 返回前台</router-link>
        <span class="who"><span class="avatar">{{ initial }}</span>{{ adminName }} · {{ adminRole }}</span>
        <button class="btn btn-ghost btn-sm" @click="logout">退出登录</button>
      </div>
    </div>
  </header>
  <div class="adminWrap">
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
    <main class="adminMain">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
/* 顶栏内容与控制台共用同一居中容器,左右边缘对齐 */
.adminBar {
  max-width: 1200px; margin: 0 auto; padding: 12px 20px;
  display: flex; align-items: center; justify-content: space-between; gap: 14px;
}
.adminWrap { max-width: 1200px; margin: 0 auto; }

/* 复用 tokens.css 的 .adminNav 视觉(SoT);router-link 用 .admin-navlink 套同款样式 */
.admin-navlink {
  display: flex; align-items: center; gap: 9px; padding: 10px 12px;
  border-radius: 9px; text-decoration: none; color: var(--ink-soft);
  font-size: 14px; font-weight: 600; margin-bottom: 2px;
}
.admin-navlink:hover { background: var(--paper); }
.admin-navlink.active { background: var(--info-soft); color: var(--brand); }

.who { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: var(--ink-soft); }
.backLink { font-size: 13px; font-weight: 600; color: var(--ink-soft); text-decoration: none; }
.backLink:hover { color: var(--brand); }
</style>
