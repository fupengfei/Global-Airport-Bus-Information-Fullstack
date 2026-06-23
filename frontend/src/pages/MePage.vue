<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { listFavorites } from '../api/favorites'
import type { BusDetail } from '../api/bus'
import FavoriteCard from '../components/FavoriteCard.vue'
import StateBlock from '../components/StateBlock.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuth()
const favorites = ref<BusDetail[]>([])
const favLoading = ref(true)
const favError = ref(false)

onMounted(async () => {
  if (!auth.isAuthed) { router.push('/login'); return }
  if (!auth.user) { try { await auth.loadMe() } catch { /* 401 拦截器处理 */ } }
  try { favorites.value = await listFavorites() } catch { favError.value = true } finally { favLoading.value = false }
})

async function doLogout() { await auth.logout(); router.push('/') }
</script>

<template>
  <div class="mePage" v-if="auth.user">
    <h1 class="pageH2" style="margin-top: 0">{{ t('auth.profile') }}</h1>
    <p class="profileLine"><strong>{{ t('auth.username') }}:</strong> {{ auth.user.username }}</p>
    <p class="profileLine"><strong>{{ t('auth.email') }}:</strong> {{ auth.user.email }}</p>

    <div class="meActions">
      <router-link class="btn btn-ghost btn-sm" to="/me/password">{{ t('auth.changePw') }}</router-link>
      <button class="btn btn-ghost btn-sm" @click="doLogout">{{ t('auth.logout') }}</button>
    </div>

    <h3 class="meSection">{{ t('favorite.mine') }}</h3>
    <StateBlock :loading="favLoading" :error="favError" :empty="!favLoading && !favError && favorites.length === 0" :empty-text="t('favorite.empty')">
      <FavoriteCard v-for="b in favorites" :key="b.sourceId" :bus="b" />
    </StateBlock>
  </div>
</template>

<style scoped>
.profileLine { color: var(--ink-soft); font-size: 14px; margin: 2px 0; }
.meActions { display: flex; gap: 10px; margin-top: 14px; }
.meSection { margin: 26px 0 12px; }
</style>
