<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn } from 'element-plus'
import { getSubscriptions, type SubscriptionStats } from '../../api/admin'

const data = ref<SubscriptionStats>({ topRoutes: [], topAirports: [], topCities: [] })
onMounted(async () => { data.value = await getSubscriptions() })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">订阅统计</h1>
  <p class="pageDesc">按线路 / 机场 / 城市聚合收藏(= 订阅)计数。</p>

  <div class="panel">
    <h3>最受关注的线路</h3>
    <ElTable :data="data.topRoutes" style="width: 100%">
      <ElTableColumn prop="route" label="线路" />
      <ElTableColumn prop="destination" label="目的地" />
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="cityName" label="城市" width="120" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
      <ElTableColumn prop="notifyCount" label="订阅数" width="100" />
    </ElTable>
  </div>

  <div class="panel">
    <h3>机场维度</h3>
    <ElTable :data="data.topAirports" style="width: 100%">
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="airportName" label="名称" />
      <ElTableColumn prop="cityName" label="城市" width="120" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
    </ElTable>
  </div>

  <div class="panel">
    <h3>城市维度</h3>
    <ElTable :data="data.topCities" style="width: 100%">
      <ElTableColumn prop="cityName" label="城市" />
      <ElTableColumn prop="countryName" label="国家 / 地区" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
    </ElTable>
  </div>
</template>
