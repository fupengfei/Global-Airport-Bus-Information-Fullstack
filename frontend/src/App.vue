<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
import { useAuth } from './stores/auth'
import { useFavorites } from './stores/favorites'
import { useMessages } from './stores/messages'
const { t, locale } = useI18n()
const auth = useAuth()
const messages = useMessages()

onMounted(() => {
  if (auth.isAuthed) { useFavorites().load().catch(() => { /* 忽略 */ }); messages.startPolling() }
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
        <template v-else>
          <router-link data-test="bell" class="bell" to="/inbox" :aria-label="t('msg.title')">
            🔔<span v-if="messages.unread > 0" data-test="bell-dot" class="dot">{{ messages.unread }}</span>
          </router-link>
          <router-link class="btn btn-ghost btn-sm" to="/tickets">{{ t('ticket.nav') }}</router-link>
          <router-link class="btn btn-ghost btn-sm" to="/me">{{ auth.user?.username ?? t('auth.profile') }}</router-link>
        </template>
      </div>
    </div>
  </header>
  <main class="wrap"><router-view /></main>
</template>
