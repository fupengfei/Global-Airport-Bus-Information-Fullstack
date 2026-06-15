<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ lastUpdated: string | null; fetchFailed: boolean }>()
const { t } = useI18n()
</script>

<template>
  <span class="chip" :class="{ warn: fetchFailed }">
    <span class="d"></span>
    <template v-if="fetchFailed">{{ t('freshness.fetchFailed') }}</template>
    <template v-else>{{ t('freshness.updated') }} {{ lastUpdated }}</template>
  </span>
</template>
