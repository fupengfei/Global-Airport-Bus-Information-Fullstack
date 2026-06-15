<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
const { t, locale } = useI18n()
</script>

<template>
  <header class="topbar">
    <router-link to="/" class="brand">{{ t('app.title') }}</router-link>
    <select :value="locale" @change="(e) => setLocale((e.target as HTMLSelectElement).value as 'zh-CN' | 'en')">
      <option value="zh-CN">中文</option>
      <option value="en">English</option>
    </select>
  </header>
  <main class="content"><router-view /></main>
</template>

<style>
:root { --maxw: 880px; }
.topbar { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #eee; }
.brand { font-weight:700; text-decoration:none; color:inherit; }
.content { max-width: var(--maxw); margin: 0 auto; padding: 16px; }
</style>
