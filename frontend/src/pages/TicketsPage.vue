<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTickets } from '../stores/tickets'

const { t } = useI18n()
const store = useTickets()

const opened = ref<number | null>(null)
const newSource = ref('')
const newBody = ref('')
const replyBody = ref<Record<number, string>>({})

onMounted(() => store.load())

async function submitNew() {
  if (!newBody.value.trim()) return
  await store.create({ sourceId: newSource.value.trim() || undefined, body: newBody.value.trim() })
  newSource.value = ''; newBody.value = ''
}
async function open(id: number) {
  opened.value = opened.value === id ? null : id
  if (opened.value === id && !store.threads[id]) await store.open(id)
}
async function sendReply(id: number) {
  const body = (replyBody.value[id] ?? '').trim()
  if (!body) return
  await store.reply(id, body); replyBody.value[id] = ''
}
async function doClose(id: number) { await store.close(id) }
function statusLabel(s: string) { return t('ticket.status.' + s) }
defineExpose({ submitNew, open, sendReply, doClose })
</script>

<template>
  <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:14px;margin-top:8px;flex-wrap:wrap">
    <div>
      <h1 class="pageH2" style="margin:0">{{ t('ticket.title') }}</h1>
      <p class="pageDesc" style="margin:4px 0 0">{{ t('ticket.desc') }}</p>
    </div>
  </div>

  <section class="panel" style="margin-top:18px">
    <h3>{{ t('ticket.newTitle') }}</h3>
    <div class="formrow"><label>{{ t('ticket.sourceLabel') }}</label>
      <input class="input" type="text" v-model="newSource" :placeholder="t('ticket.sourcePlaceholder')" />
    </div>
    <div class="formrow"><label>{{ t('ticket.bodyLabel') }}</label>
      <textarea class="input" data-test="new-body" v-model="newBody" :placeholder="t('ticket.bodyPlaceholder')"></textarea>
    </div>
    <button class="btn btn-primary" data-test="new-submit" @click="submitNew">{{ t('ticket.submit') }}</button>
  </section>

  <div v-if="store.list.length === 0" class="panel">{{ t('ticket.empty') }}</div>

  <section v-for="tk in store.list" :key="tk.id" class="panel">
    <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px">
      <h3 style="margin:0;cursor:pointer" :data-test="`open-${tk.id}`" @click="open(tk.id)">
        #{{ tk.id }}<span v-if="tk.relatedSourceId"> · {{ tk.relatedSourceId }}</span>
      </h3>
      <span class="statusBadge" :class="tk.status.toLowerCase()">{{ statusLabel(tk.status) }}</span>
    </div>

    <template v-if="opened === tk.id && store.threads[tk.id]">
      <div class="thread">
        <div v-for="rp in store.threads[tk.id].replies" :key="rp.id"
             class="bubble" :class="rp.authorType === 'ADMIN' ? 'admin' : 'user'">
          <div class="who">{{ rp.authorType === 'ADMIN' ? t('ticket.admin') : t('ticket.me') }} · {{ rp.createdAt }}</div>
          {{ rp.body }}
        </div>
      </div>
      <div v-if="tk.status === 'CLOSED'" class="formNote">{{ t('ticket.closedNote') }}</div>
      <div class="replyBox">
        <textarea class="input" :data-test="`reply-${tk.id}`" v-model="replyBody[tk.id]" :placeholder="t('ticket.replyPlaceholder')"></textarea>
        <button class="btn btn-primary" @click="sendReply(tk.id)">{{ t('ticket.reply') }}</button>
        <button v-if="tk.status !== 'CLOSED'" class="btn btn-ghost" @click="doClose(tk.id)">{{ t('ticket.close') }}</button>
      </div>
    </template>
  </section>
</template>
