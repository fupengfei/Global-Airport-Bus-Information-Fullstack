<script setup lang="ts">
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuery } from '@tanstack/vue-query'
import { getBusDetail } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import BusCard from '../components/BusCard.vue'
import ReportModal from '../components/ReportModal.vue'

const { t } = useI18n()
const props = defineProps<{ sourceId: string }>()
const sourceId = toRef(props, 'sourceId')
const { data, isLoading, isError } = useQuery({
  queryKey: ['busDetail', sourceId],
  queryFn: () => getBusDetail(sourceId.value),
})
</script>

<template>
  <div class="wrap">
    <nav class="crumbs">
      <router-link to="/">{{ t('nav.home') }}</router-link>
      <span class="sep"> / </span>
      <span>{{ data?.route ?? sourceId }}</span>
    </nav>

    <StateBlock :loading="isLoading" :error="isError">
      <BusCard v-if="data" :bus="data" :detail-link="false" />
      <ReportModal v-if="data" :source-id="sourceId" />
    </StateBlock>
  </div>
</template>
