<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ loading?: boolean; error?: boolean; empty?: boolean; emptyText?: string }>()
const { t } = useI18n()
</script>

<template>
  <div v-if="loading" class="skelCard">
    <div class="skel skelLine" style="width:40%"></div>
    <div class="skel" style="width:62%;height:20px;margin:10px 0"></div>
    <div class="skel skelLine" style="width:80%"></div>
  </div>
  <div v-else-if="error" class="empty">
    <div class="emptyIcon">⚠️</div>
    <p>{{ t('state.error') }}</p>
  </div>
  <div v-else-if="empty" class="empty">
    <div class="emptyIcon">🚌</div>
    <p>{{ emptyText ?? t('state.empty') }}</p>
  </div>
  <slot v-else />
</template>
