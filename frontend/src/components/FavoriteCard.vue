<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BusDetail } from '../api/bus'
import { useFavorites } from '../stores/favorites'

const props = defineProps<{ bus: BusDetail }>()
const { t } = useI18n()
const favs = useFavorites()
const faved = computed(() => favs.isFavorited(props.bus.sourceId))

// 价格只取第一段作为主价(与 BusCard 一致),缩减卡不展示副价
const priceMain = computed(() => (props.bus.price ?? '').split(/\n|·/)[0]?.trim() || props.bus.price || '')

// 国家 · 城市 · 机场(code),逐项过滤空值
const location = computed(() => {
  const parts = [props.bus.countryName, props.bus.cityName].filter(Boolean)
  let s = parts.join(' · ')
  if (props.bus.airportName) {
    const ap = props.bus.airportCode ? `${props.bus.airportName} (${props.bus.airportCode})` : props.bus.airportName
    s = s ? `${s} · ${ap}` : ap
  }
  return s
})

async function onFav() {
  try { await favs.toggle(props.bus.sourceId) } catch { /* 401 由拦截器处理,其余忽略 */ }
}
</script>

<template>
  <router-link class="card favCard" :to="{ name: 'bus', params: { sourceId: bus.sourceId } }">
    <div class="favMain">
      <div class="route">{{ bus.route }}</div>
      <div class="dest">{{ bus.destination ?? bus.route }}</div>
      <div v-if="location" class="favLoc">{{ location }}</div>
      <div v-if="priceMain" class="favPrice">{{ priceMain }}</div>
    </div>
    <div class="favSide">
      <button
        class="favBtn"
        :class="{ favOn: faved }"
        :aria-label="faved ? t('favorite.remove') : t('favorite.add')"
        :title="faved ? t('favorite.remove') : t('favorite.add')"
        @click.stop.prevent="onFav"
      >{{ faved ? '♥' : '♡' }}</button>
      <span class="detailLink">{{ t('detail.viewDetail') }} →</span>
    </div>
  </router-link>
</template>

<style scoped>
.favCard { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 16px 20px; }
.favMain { min-width: 0; }
.favLoc { font-size: 13px; color: var(--ink-soft); margin-top: 4px; }
.favPrice { font-family: var(--font-display); font-weight: 800; font-size: 15px; color: var(--brand-deep); margin-top: 6px; }
.favSide { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; flex-shrink: 0; }
.favBtn { border: none; background: none; cursor: pointer; line-height: 1; font-size: 22px; color: var(--ink-faint); padding: 2px 4px; }
.favBtn.favOn { color: #e0245e; }
.detailLink { font-size: 13px; white-space: nowrap; }
</style>
