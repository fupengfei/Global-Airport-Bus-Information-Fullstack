<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { changePassword } from '../api/auth'
import { apiErrorMessage } from '../api/client'

const { t, te } = useI18n()
const router = useRouter()
const auth = useAuth()
const oldPw = ref(''); const newPw = ref(''); const msg = ref(''); const err = ref('')

onMounted(() => { if (!auth.isAuthed) router.push('/login') })

async function changePw() {
  msg.value = ''; err.value = ''
  try {
    await changePassword(oldPw.value, newPw.value)
    msg.value = t('auth.pwChanged'); oldPw.value = ''; newPw.value = ''
  } catch (e) { err.value = apiErrorMessage(e, t, te) }
}
</script>

<template>
  <div class="authCard">
    <router-link class="backLink" to="/me">← {{ t('auth.profile') }}</router-link>
    <h2 style="margin-top: 8px">{{ t('auth.changePw') }}</h2>
    <p v-if="msg" class="authNote">{{ msg }}</p>
    <p v-if="err" class="authErr">{{ err }}</p>
    <form @submit.prevent="changePw">
      <div class="formrow"><label>{{ t('auth.oldPassword') }}</label>
        <input class="input" type="password" v-model="oldPw" /></div>
      <div class="formrow"><label>{{ t('auth.newPassword') }}</label>
        <input class="input" type="password" v-model="newPw" :placeholder="t('auth.passwordPh')" /></div>
      <button class="btn btn-primary btn-block">{{ t('auth.changePw') }}</button>
    </form>
  </div>
</template>

<style scoped>
.backLink { font-size: 13px; font-weight: 600; color: var(--ink-soft); text-decoration: none; }
.backLink:hover { color: var(--brand); }
</style>
