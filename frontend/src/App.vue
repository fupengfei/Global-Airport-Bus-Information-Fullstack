<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
const { t, locale } = useI18n()
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
        <router-link class="btn btn-ghost btn-sm" to="/">{{ t('app.login') }}</router-link>
      </div>
    </div>
  </header>
  <main class="wrap"><router-view /></main>
</template>
