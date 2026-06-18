<script setup lang="ts">
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuery } from '@tanstack/vue-query'
import { getAirportBuses } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import FreshnessBadge from '../components/FreshnessBadge.vue'

const { t } = useI18n()
const props = defineProps<{ code: string }>()
const code = toRef(props, 'code')
const { data, isLoading, isError } = useQuery({
  queryKey: ['airportBuses', code],
  queryFn: () => getAirportBuses(code.value),
})
</script>

<template>
  <div class="wrap">
    <nav class="crumbs">
      <router-link to="/" class="">{{ t('nav.home') }}</router-link>
      <span class="sep">/</span>
      <span>{{ code }}</span>
    </nav>

    <StateBlock :loading="isLoading" :error="isError" :empty="!isLoading && !(data?.length)">
      <div class="results">
        <p class="count">{{ t('home.routeCount', { count: data?.length ?? 0 }) }}</p>
        <router-link
          v-for="b in data"
          :key="b.sourceId"
          :to="{ name: 'bus', params: { sourceId: b.sourceId } }"
          class="card"
        >
          <div class="card__top">
            <div>
              <div class="route">{{ b.route }}</div>
              <div class="dest">{{ b.destination }}</div>
              <div v-if="b.operator" class="operator">{{ b.operator }}</div>
            </div>
            <div v-if="b.price" class="price">{{ b.price }}</div>
          </div>
          <div v-if="b.duration" class="metaRow">
            <div class="metaItem">
              <span class="metaLabel">时长</span>
              <span class="metaVal">{{ b.duration }}</span>
            </div>
          </div>
          <div class="updated">
            <FreshnessBadge :last-updated="b.lastUpdated" :fetch-failed="b.fetchFailed" />
          </div>
        </router-link>
      </div>
    </StateBlock>
  </div>
</template>
