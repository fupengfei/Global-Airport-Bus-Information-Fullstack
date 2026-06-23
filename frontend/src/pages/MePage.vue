<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { changePassword } from '../api/auth'
import { listFavorites } from '../api/favorites'
import type { BusDetail } from '../api/bus'
import { apiErrorMessage } from '../api/client'
import BusCard from '../components/BusCard.vue'
import StateBlock from '../components/StateBlock.vue'

const { t, te } = useI18n()
const router = useRouter()
const auth = useAuth()
const oldPw = ref(''); const newPw = ref(''); const msg = ref(''); const err = ref('')
const favorites = ref<BusDetail[]>([])
const favLoading = ref(true)
const favError = ref(false)

onMounted(async () => {
  if (!auth.isAuthed) { router.push('/login'); return }
  if (!auth.user) { try { await auth.loadMe() } catch { /* 401 拦截器处理 */ } }
  try { favorites.value = await listFavorites() } catch { favError.value = true } finally { favLoading.value = false }
})

async function changePw() {
  msg.value = ''; err.value = ''
  try {
    await changePassword(oldPw.value, newPw.value)
    msg.value = t('auth.pwChanged'); oldPw.value = ''; newPw.value = ''
  } catch (e) { err.value = apiErrorMessage(e, t, te) }
}
async function doLogout() { await auth.logout(); router.push('/') }
</script>

<template>
  <div class="mePage" v-if="auth.user">
    <h1 class="pageH2" style="margin-top: 0">{{ t('auth.profile') }}</h1>
    <p class="profileLine"><strong>{{ t('auth.username') }}:</strong> {{ auth.user.username }}</p>
    <p class="profileLine"><strong>{{ t('auth.email') }}:</strong> {{ auth.user.email }}</p>

    <h3 class="meSection">{{ t('favorite.mine') }}</h3>
    <StateBlock :loading="favLoading" :error="favError" :empty="!favLoading && !favError && favorites.length === 0" :empty-text="t('favorite.empty')">
      <BusCard v-for="b in favorites" :key="b.sourceId" :bus="b" :detail-link="true" />
    </StateBlock>

    <div class="authCard" style="margin-top: 28px">
      <h3 style="margin-top: 0">{{ t('auth.changePw') }}</h3>
      <p v-if="msg" class="authNote">{{ msg }}</p>
      <p v-if="err" class="authErr">{{ err }}</p>
      <form @submit.prevent="changePw">
        <div class="formrow"><label>{{ t('auth.oldPassword') }}</label>
          <input class="input" type="password" v-model="oldPw" /></div>
        <div class="formrow"><label>{{ t('auth.newPassword') }}</label>
          <input class="input" type="password" v-model="newPw" :placeholder="t('auth.passwordPh')" /></div>
        <button class="btn btn-primary btn-block">{{ t('auth.changePw') }}</button>
      </form>
      <button class="btn btn-ghost btn-block" style="margin-top:12px" @click="doLogout">{{ t('auth.logout') }}</button>
    </div>
  </div>
</template>

<style scoped>
.profileLine { color: var(--ink-soft); font-size: 14px; margin: 2px 0; }
.meSection { margin: 22px 0 12px; }
</style>
