<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMessages } from '../stores/messages'
import { renderMessage } from '../components/renderMessage'

const { t } = useI18n()
const store = useMessages()

onMounted(() => store.loadList())

const rendered = computed(() => store.list.map((m) => ({
  raw: m, view: renderMessage(m.templateCode, m.params, t as any),
})))

async function remove(id: number) { await store.remove(id) }
async function markAllRead() {
  const ids = store.list.filter((m) => !m.isRead).map((m) => m.id)
  if (ids.length) await store.markRead(ids)
}
defineExpose({ remove, markAllRead })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">{{ t('msg.title') }}</h1>
  <div style="margin: 8px 0 16px">
    <button class="btn btn-ghost btn-sm" @click="markAllRead">{{ t('msg.markRead') }}</button>
  </div>

  <div v-if="rendered.length === 0" class="panel">{{ t('msg.empty') }}</div>

  <div v-for="r in rendered" :key="r.raw.id" class="panel" :style="{ opacity: r.raw.isRead ? 0.6 : 1 }">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px">
      <div>
        <div class="msgTitle" style="font-weight:700">{{ r.view.title }}</div>
        <div v-for="(d, i) in r.view.diffs" :key="i" class="diffRow" style="font-size:13px;margin-top:4px">
          <span class="diffField">{{ d.label }}</span>
          <span class="diffOld" style="color:var(--ink-faint)"> {{ d.oldValue }}</span>
          <span class="diffArrow"> → </span>
          <span class="diffNew">{{ d.newValue }}</span>
        </div>
        <div class="msgMeta formNote" style="margin-top:6px">{{ r.raw.createdAt }}</div>
      </div>
      <div style="display:flex;gap:8px;flex-shrink:0">
        <router-link v-if="r.view.link" class="btn btn-ghost btn-sm" :to="r.view.link">{{ t('msg.viewDetail') }}</router-link>
        <router-link v-else-if="r.raw.relatedSourceId" class="btn btn-ghost btn-sm" :to="`/bus/${r.raw.relatedSourceId}`">{{ t('msg.viewDetail') }}</router-link>
        <button class="btn btn-ghost btn-sm" @click="remove(r.raw.id)">{{ t('msg.delete') }}</button>
      </div>
    </div>
  </div>
</template>
