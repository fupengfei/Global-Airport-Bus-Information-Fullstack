<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useQueries, useQuery } from '@tanstack/vue-query'
import { getAirportBuses, getBusDetail, getTree, search as searchApi } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import BusCard from '../components/BusCard.vue'

const { t } = useI18n()
const router = useRouter()

// ---- tree(国家/城市/机场)----
const treeQ = useQuery({ queryKey: ['tree'], queryFn: getTree })

const countries = computed(() => treeQ.data.value?.countries ?? [])
const treeEmpty = computed(() => treeQ.isSuccess.value && countries.value.length === 0)

// 三联选择(级联)
const countryCode = ref('')
const cityName = ref('')
const airportCode = ref('')

const selectedCountry = computed(() => countries.value.find((c) => c.code === countryCode.value))
const cities = computed(() => selectedCountry.value?.cities ?? [])
const selectedCity = computed(() => cities.value.find((c) => c.name === cityName.value))
const airports = computed(() => selectedCity.value?.airports ?? [])

// ---- 搜索(服务端:机场 + 按站点匹配的线路)----
const search = ref('')
const debounced = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
watch(search, (v) => {
  clearTimeout(timer)
  timer = setTimeout(() => { debounced.value = v.trim() }, 250)
})

const searchQ = useQuery({
  queryKey: ['search', debounced],
  queryFn: () => searchApi(debounced.value),
  enabled: computed(() => debounced.value.length >= 1),
})
const airportHits = computed(() => searchQ.data.value?.airports ?? [])
const routeHits = computed(() => searchQ.data.value?.routes ?? [])

function pickAirport(hit: { code: string; cityName: string; countryCode: string }) {
  countryCode.value = hit.countryCode
  cityName.value = hit.cityName
  airportCode.value = hit.code
  search.value = ''
  debounced.value = ''
}
function pickRoute(sourceId: string) {
  router.push({ name: 'bus', params: { sourceId } })
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

// 选中线路(可选收窄):'' = 全部展示;切换机场时复位
const routeId = ref('')
watch(airportCode, () => { routeId.value = '' })

// 要展示的线路:未选 = 全部;已选 = 仅该条
const shownBuses = computed(() =>
  routeId.value ? buses.value.filter((b) => b.sourceId === routeId.value) : buses.value,
)

// 并发拉取展示中各线路的详情
const detailQueries = useQueries({
  queries: computed(() =>
    shownBuses.value.map((b) => ({
      queryKey: ['busDetail', b.sourceId],
      queryFn: () => getBusDetail(b.sourceId),
    })),
  ),
})
const details = computed(() =>
  detailQueries.value.map((q) => q.data).filter((d): d is NonNullable<typeof d> => !!d),
)
</script>

<template>
  <header class="header">
    <div class="kicker">{{ t('home.kicker') }}</div>
    <h1 class="title">{{ t('home.title') }}</h1>
    <p class="sub">{{ t('home.sub') }}</p>
  </header>

  <StateBlock :loading="treeQ.isLoading.value" :error="treeQ.isError.value" :empty="treeEmpty" :empty-text="t('home.onlyTwoCities')">
    <!-- 搜索框(服务端:机场 + 站点) -->
    <div class="searchWrap">
      <span class="searchIcon">⌕</span>
      <input class="search" type="text" v-model="search" :placeholder="t('home.searchPlaceholder')" :aria-label="t('home.searchAirport')" />
      <div v-if="airportHits.length || routeHits.length" class="suggest">
        <div v-for="hit in airportHits" :key="'ap-' + hit.code" class="suggestItem" tabindex="0" @click="pickAirport(hit)" @keydown.enter="pickAirport(hit)">
          <span class="suggestCode">{{ hit.code }}</span> {{ hit.name }}
          <span class="suggestType">{{ hit.cityName }}</span>
        </div>
        <div v-for="hit in routeHits" :key="'rt-' + hit.sourceId" class="suggestItem" tabindex="0" @click="pickRoute(hit.sourceId)" @keydown.enter="pickRoute(hit.sourceId)">
          <span class="suggestCode">{{ hit.route }}</span> {{ hit.matchedStop }}
          <span class="suggestType">{{ t('home.stopHit') }}</span>
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

    <!-- 选中机场后:默认列出该机场全部线路完整卡片 -->
    <section v-if="selectedAirport" class="results">
      <div class="placeHead">
        <h2>{{ selectedAirport.name }}</h2>
        <span class="iata">{{ selectedAirport.code }}</span>
      </div>

      <StateBlock :loading="busesQ.isLoading.value" :error="busesQ.isError.value" :empty="busesQ.isSuccess.value && buses.length === 0" :empty-text="t('home.noBuses')">
        <p class="count">{{ t('home.routeCount', { count: buses.length }) }}</p>

        <div class="routePick">
          <label>
            <input type="radio" name="route" value="" v-model="routeId" />
            {{ t('home.allRoutes') }}
          </label>
          <label v-for="b in buses" :key="b.sourceId">
            <input type="radio" name="route" :value="b.sourceId" v-model="routeId" />
            {{ b.route }} → {{ b.destination ?? b.route }}
          </label>
        </div>

        <BusCard v-for="d in details" :key="d.sourceId" :bus="d" :detail-link="true" />
      </StateBlock>
    </section>

    <footer class="footer">{{ t('home.footer') }}</footer>
  </StateBlock>
</template>
