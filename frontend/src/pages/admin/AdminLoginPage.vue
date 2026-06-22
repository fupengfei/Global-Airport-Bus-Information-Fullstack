<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../../stores/auth'
import { isAdminRole } from '../../router/adminGuard'
import { apiErrorMessage } from '../../api/client'

const { t, te } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuth()

// 仅接受站内绝对路径(/ 开头且非 //),默认进 /admin
function target(): string {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/admin'
}

// 已登录的管理员直接进后台,不必再登一次
onMounted(() => {
  if (auth.isAuthed && isAdminRole(auth.user?.role)) router.replace(target())
})

const account = ref(''); const password = ref('')
const err = ref(''); const busy = ref(false)

async function doLogin() {
  err.value = ''; busy.value = true
  try {
    await auth.login(account.value, password.value)
    if (isAdminRole(auth.user?.role)) {
      router.push(target())
    } else {
      err.value = t('adminAuth.noPermission')
      await auth.logout().catch(() => { /* best-effort cleanup */ })
    }
  } catch (e) {
    err.value = apiErrorMessage(e, t, te)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="authCard">
    <h1 class="adminAuthTitle">{{ t('adminAuth.title') }}</h1>
    <p class="authNote">{{ t('adminAuth.subtitle') }}</p>

    <p v-if="err" class="authErr">{{ err }}</p>

    <form @submit.prevent="doLogin">
      <div class="formrow"><label>{{ t('auth.account') }}</label>
        <input class="input" type="text" v-model="account" :placeholder="t('auth.accountPh')" /></div>
      <div class="formrow"><label>{{ t('auth.password') }}</label>
        <input class="input" type="password" v-model="password" /></div>
      <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.login') }}</button>
    </form>
  </div>
</template>

<style scoped>
.adminAuthTitle { font-family: var(--font-display, 'Sora'), sans-serif; font-size: 1.25rem; margin: 0 0 4px; }
</style>
