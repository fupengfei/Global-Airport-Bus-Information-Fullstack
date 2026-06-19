<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { reset } from '../api/auth'
import { asApiError } from '../api/client'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const pw = ref(''); const err = ref(''); const done = ref(false); const busy = ref(false)
const token = String(route.query.token ?? '')

async function submit() {
  err.value = ''; busy.value = true
  try { await reset(token, pw.value); done.value = true; setTimeout(() => router.push('/login'), 1500) }
  catch (e) { err.value = asApiError(e)?.message ?? t('auth.genericError') }
  finally { busy.value = false }
}
</script>

<template>
  <div class="authCard">
    <h2>{{ t('auth.resetTitle') }}</h2>
    <p v-if="!token" class="authErr">{{ t('auth.resetNoToken') }}</p>
    <p v-else-if="done" class="authNote">{{ t('auth.resetDone') }}</p>
    <form v-else @submit.prevent="submit">
      <p v-if="err" class="authErr">{{ err }}</p>
      <div class="formrow"><label>{{ t('auth.newPassword') }}</label>
        <input class="input" type="password" v-model="pw" :placeholder="t('auth.passwordPh')" /></div>
      <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.resetSubmit') }}</button>
    </form>
  </div>
</template>
