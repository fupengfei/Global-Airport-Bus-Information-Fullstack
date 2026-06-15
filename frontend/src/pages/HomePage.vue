<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuery } from '@tanstack/vue-query'
import { getAirportBuses, getBusDetail, getTree } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import BusCard from '../components/BusCard.vue'

const { t } = useI18n()

// ---- tree(国家/城市/机场)----
const treeQ = useQuery({ queryKey: ['tree'], queryFn: getTree })

const countries = computed(() => treeQ.data.value?.countries ?? [])
const treeEmpty = computed(() => treeQ.isSuccess.value && countries.value.length === 0)

// 三联选择(级联)
const countryCode = ref('')
const cityName = ref('')
const airportCode = ref('')

// 客户端搜索(EN2 反向检索:本期无搜索端点,基于已加载 tree 过滤机场)
const search = ref('')

const selectedCountry = computed(() => countries.value.find((c) => c.code === countryCode.value))
const cities = computed(() => selectedCountry.value?.cities ?? [])
const selectedCity = computed(() => cities.value.find((c) => c.name === cityName.value))
const airports = computed(() => selectedCity.value?.airports ?? [])

// 搜索命中:在整棵树里按机场名 / 城市名 / IATA 过滤,给出快捷跳转
interface AirportHit { code: string; name: string; cityName: string; countryCode: string }
const allAirports = computed<AirportHit[]>(() =>
  countries.value.flatMap((co) =>
    co.cities.flatMap((ci) =>
      ci.airports.map((ap) => ({ code: ap.code, name: ap.name, cityName: ci.name, countryCode: co.code })),
    ),
  ),
)
const searchHits = computed<AirportHit[]>(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return []
  return allAirports.value
    .filter((a) => a.name.toLowerCase().includes(q) || a.cityName.toLowerCase().includes(q) || a.code.toLowerCase().includes(q))
    .slice(0, 6)
})

function pickHit(hit: AirportHit) {
  countryCode.value = hit.countryCode
  cityName.value = hit.cityName
  airportCode.value = hit.code
  search.value = ''
}

// 级联重置:上层变动时清空下层
watch(countryCode, () => { cityName.value = ''; airportCode.value = '' })
watch(cityName, () => { airportCode.value = '' })

// ---- 选中机场 → 线路列表 ----
const busesQ = useQuery({
  queryKey: ['airportBuses', airportCode],
  queryFn: () => getAirportBuses(airportCode.value),
  enabled: computed(() => !!airportCode.value),
})
const buses = computed(() => busesQ.data.value ?? [])
const selectedAirport = computed(() => airports.value.find((a) => a.code === airportCode.value))

// 切换机场后清空已选线路
const routeId = ref('')
watch(airportCode, () => { routeId.value = '' })
// 线路加载完成后默认选中第一条(忠实设计稿:默认选中一条并展示卡片)
watch(buses, (list) => {
  if (list.length && !list.some((b) => b.sourceId === routeId.value)) routeId.value = list[0].sourceId
})

// ---- 选中线路 → 详情卡 ----
const detailQ = useQuery({
  queryKey: ['busDetail', routeId],
  queryFn: () => getBusDetail(routeId.value),
  enabled: computed(() => !!routeId.value),
})
</script>

<template>
  <header class="header">
    <div class="kicker">{{ t('home.kicker') }}</div>
    <h1 class="title">{{ t('home.title') }}</h1>
    <p class="sub">{{ t('home.sub') }}</p>
  </header>

  <StateBlock :loading="treeQ.isLoading.value" :error="treeQ.isError.value" :empty="treeEmpty" :empty-text="t('home.onlyTwoCities')">
    <!-- 搜索框(客户端机场过滤,EN2) -->
    <div class="searchWrap">
      <span class="searchIcon">⌕</span>
      <input class="search" type="text" v-model="search" :placeholder="t('home.searchPlaceholder')" :aria-label="t('home.searchAirport')" />
      <div v-if="searchHits.length" class="suggest">
        <div v-for="hit in searchHits" :key="hit.code" class="suggestItem" tabindex="0" @click="pickHit(hit)" @keydown.enter="pickHit(hit)">
          <span class="suggestCode">{{ hit.code }}</span> {{ hit.name }}
          <span class="suggestType">{{ hit.cityName }}</span>
        </div>
      </div>
    </div>

    <!-- 国家 / 城市 / 机场 三联选择器 -->
    <section class="selector">
      <div class="field">
        <label>{{ t('home.country') }}</label>
        <select v-model="countryCode">
          <option value="" disabled>{{ t('home.selectCountry') }}</option>
          <option v-for="c in countries" :key="c.code" :value="c.code">{{ c.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>{{ t('home.city') }}</label>
        <select v-model="cityName" :disabled="!selectedCountry">
          <option value="" disabled>{{ t('home.selectCity') }}</option>
          <option v-for="c in cities" :key="c.name" :value="c.name">{{ c.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>{{ t('home.airport') }}</label>
        <select v-model="airportCode" :disabled="!selectedCity">
          <option value="" disabled>{{ t('home.selectAirport') }}</option>
          <option v-for="a in airports" :key="a.code" :value="a.code">{{ a.name }} ({{ a.code }})</option>
        </select>
      </div>
    </section>

    <!-- 选中机场后的线路列表 + 选中线路的卡片 -->
    <section v-if="selectedAirport" class="results">
      <div class="placeHead">
        <h2>{{ selectedAirport.name }}</h2>
        <span class="iata">{{ selectedAirport.code }}</span>
      </div>

      <StateBlock :loading="busesQ.isLoading.value" :error="busesQ.isError.value" :empty="busesQ.isSuccess.value && buses.length === 0" :empty-text="t('home.noBuses')">
        <p class="count">{{ t('home.routeCount', { count: buses.length }) }}</p>

        <div class="routePick">
          <label v-for="b in buses" :key="b.sourceId">
            <input type="radio" name="route" :value="b.sourceId" v-model="routeId" />
            {{ b.route }} → {{ b.destination ?? b.route }}
          </label>
        </div>

        <StateBlock v-if="routeId" :loading="detailQ.isLoading.value" :error="detailQ.isError.value">
          <BusCard v-if="detailQ.data.value" :bus="detailQ.data.value" />
        </StateBlock>
        <p v-else class="count">{{ t('home.pickRoute') }}</p>
      </StateBlock>
    </section>

    <footer class="footer">{{ t('home.footer') }}</footer>
  </StateBlock>
</template>
