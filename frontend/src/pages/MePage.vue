<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { changePassword } from '../api/auth'
import { listFavorites } from '../api/favorites'
import type { BusDetail } from '../api/bus'
import { asApiError } from '../api/client'
import BusCard from '../components/BusCard.vue'
import StateBlock from '../components/StateBlock.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuth()
const oldPw = ref(''); const newPw = ref(''); const msg = ref(''); const err = ref('')
const favorites = ref<BusDetail[]>([])
const favLoading = ref(true)

onMounted(async () => {
  if (!auth.isAuthed) { router.push('/login'); return }
  if (!auth.user) { try { await auth.loadMe() } catch { /* 401 拦截器处理 */ } }
  try { favorites.value = await listFavorites() } catch { /* 忽略 */ } finally { favLoading.value = false }
})

async function changePw() {
  msg.value = ''; err.value = ''
  try {
    await changePassword(oldPw.value, newPw.value)
    msg.value = t('auth.pwChanged'); oldPw.value = ''; newPw.value = ''
  } catch (e) { err.value = asApiError(e)?.message ?? t('auth.genericError') }
}
async function doLogout() { await auth.logout(); router.push('/') }
</script>

<template>
  <div class="authCard" v-if="auth.user">
    <h2>{{ t('auth.profile') }}</h2>
    <p><strong>{{ t('auth.username') }}:</strong> {{ auth.user.username }}</p>
    <p><strong>{{ t('auth.email') }}:</strong> {{ auth.user.email }}</p>

    <h3>{{ t('favorite.mine') }}</h3>
    <StateBlock :loading="favLoading" :empty="!favLoading && favorites.length === 0" :empty-text="t('favorite.empty')">
      <BusCard v-for="b in favorites" :key="b.sourceId" :bus="b" :detail-link="true" />
    </StateBlock>

    <h3>{{ t('auth.changePw') }}</h3>
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
</template>
