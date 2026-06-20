<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'
import { sendRegisterCode, forgot } from '../api/auth'
import { asApiError } from '../api/client'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuth()

function go() {
  const r = route.query.redirect
  router.push(typeof r === 'string' && r ? r : '/')
}

const tab = ref<'login' | 'register' | 'forgot'>('login')
const err = ref('')
const busy = ref(false)

const account = ref(''); const password = ref('')
const ru = ref(''); const re = ref(''); const rc = ref(''); const rp = ref('')
const codeCountdown = ref(0)
const fe = ref(''); const sent = ref(false)

function fail(e: unknown) { err.value = asApiError(e)?.message ?? t('auth.genericError') }

async function doLogin() {
  err.value = ''; busy.value = true
  try { await auth.login(account.value, password.value); go() }
  catch (e) { fail(e) } finally { busy.value = false }
}
async function getCode() {
  err.value = ''
  try {
    await sendRegisterCode(re.value)
    codeCountdown.value = 60
    const id = setInterval(() => { if (--codeCountdown.value <= 0) clearInterval(id) }, 1000)
  } catch (e) { fail(e) }
}
async function doRegister() {
  err.value = ''; busy.value = true
  try { await auth.register({ username: ru.value, email: re.value, code: rc.value, password: rp.value }); go() }
  catch (e) { fail(e) } finally { busy.value = false }
}
async function doForgot() {
  err.value = ''; busy.value = true
  try { await forgot(fe.value); sent.value = true }
  catch (e) { fail(e) } finally { busy.value = false }
}
</script>

<template>
  <div class="authCard">
    <div class="tabs" role="group">
      <button :aria-pressed="tab==='login'" @click="tab='login'">{{ t('auth.login') }}</button>
      <button :aria-pressed="tab==='register'" @click="tab='register'">{{ t('auth.register') }}</button>
    </div>

    <p v-if="err" class="authErr">{{ err }}</p>

    <form v-if="tab==='login'" @submit.prevent="doLogin">
      <div class="formrow"><label>{{ t('auth.account') }}</label>
        <input class="input" type="text" v-model="account" :placeholder="t('auth.accountPh')" /></div>
      <div class="formrow"><label>{{ t('auth.password') }}
        <a href="#" @click.prevent="tab='forgot'">{{ t('auth.forgot') }}</a></label>
        <input class="input" type="password" v-model="password" /></div>
      <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.login') }}</button>
    </form>

    <form v-else-if="tab==='register'" @submit.prevent="doRegister">
      <div class="formrow"><label>{{ t('auth.username') }}</label>
        <input class="input" type="text" v-model="ru" placeholder="3-20" /></div>
      <div class="formrow"><label>{{ t('auth.email') }}</label>
        <input class="input" type="email" v-model="re" placeholder="you@example.com" /></div>
      <div class="formrow"><label>{{ t('auth.code') }}</label>
        <div style="display:flex;gap:8px">
          <input class="input" type="text" v-model="rc" :placeholder="t('auth.codePh')" style="flex:1" />
          <button type="button" class="btn btn-ghost" :disabled="codeCountdown>0" @click="getCode">
            {{ codeCountdown>0 ? t('auth.resendIn', { s: codeCountdown }) : t('auth.getCode') }}
          </button>
        </div></div>
      <div class="formrow"><label>{{ t('auth.password') }}</label>
        <input class="input" type="password" v-model="rp" :placeholder="t('auth.passwordPh')" /></div>
      <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.register') }}</button>
    </form>

    <form v-else @submit.prevent="doForgot">
      <p v-if="sent" class="authNote">{{ t('auth.forgotSent') }}</p>
      <template v-else>
        <p class="authNote">{{ t('auth.forgotHint') }}</p>
        <div class="formrow"><label>{{ t('auth.email') }}</label>
          <input class="input" type="email" v-model="fe" placeholder="you@example.com" /></div>
        <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.sendReset') }}</button>
      </template>
      <p class="formhint"><a href="#" @click.prevent="tab='login'">{{ t('auth.backLogin') }}</a></p>
    </form>
  </div>
</template>
