<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
import { useAuth } from './stores/auth'
import { useFavorites } from './stores/favorites'
const { t, locale } = useI18n()
const auth = useAuth()

onMounted(() => {
  if (auth.isAuthed) useFavorites().load().catch(() => { /* 忽略 */ })
})
</script>

<template>
  <header class="topbar">
    <div class="topbar__in">
      <router-link class="brand" to="/"><span class="glyph">B</span> {{ t('app.title') }}</router-link>
      <div class="actions">
        <div class="lang" role="group" aria-label="language">
          <button :aria-pressed="locale === 'zh-CN'" @click="setLocale('zh-CN')">中文</button>
          <button :aria-pressed="locale === 'en'" @click="setLocale('en')">EN</button>
          <button :aria-pressed="locale === 'de'" @click="setLocale('de')">DE</button>
        </div>
        <router-link v-if="!auth.isAuthed" class="btn btn-ghost btn-sm" to="/login">{{ t('app.login') }}</router-link>
        <router-link v-else class="btn btn-ghost btn-sm" to="/me">{{ auth.user?.username ?? t('auth.profile') }}</router-link>
      </div>
    </div>
  </header>
  <main class="wrap"><router-view /></main>
</template>
