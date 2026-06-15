<script setup lang="ts">
import { computed } from 'vue'
import type { Alert } from '../api/bus'
import { activeAlerts } from './alertFilter'

const props = defineProps<{ alerts: Alert[] }>()
const today = new Date().toISOString().slice(0, 10)
const active = computed(() => activeAlerts(props.alerts, today))
const icon = (t: string) => (t === 'reroute' ? '🔁' : t === 'warning' ? '⚠️' : 'ℹ️')
const label = (t: string) => (t === 'reroute' ? '改道' : t === 'warning' ? '注意' : '提醒')
</script>

<template>
  <div v-for="(a, i) in active" :key="i" class="alert" :class="{ alertInfo: a.type === 'info' }">
    <span class="alertIcon">{{ icon(a.type) }}</span>
    <div class="alertBody">
      <span class="alertType">{{ label(a.type) }}</span>{{ a.message }}
      <span v-if="a.startDate || a.endDate" class="alertDate">{{ a.startDate }} → {{ a.endDate }}</span>
    </div>
  </div>
</template>
