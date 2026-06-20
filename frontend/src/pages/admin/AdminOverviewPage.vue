<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElCard } from 'element-plus'
import * as echarts from 'echarts'
import { getOverview, getRegistrations, type Overview, type RegistrationPoint } from '../../api/admin'

const overview = ref<Overview | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)

onMounted(async () => {
  overview.value = await getOverview()
  const points: RegistrationPoint[] = await getRegistrations(7)
  await nextTick()
  if (!chartEl.value) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({
    grid: { left: 36, right: 12, top: 16, bottom: 28 },
    xAxis: { type: 'category', data: points.map((p) => p.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: points.map((p) => p.count), itemStyle: { color: '#2f6df6' } }],
    tooltip: { trigger: 'axis' },
  })
})
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">概览</h1>
  <p class="pageDesc">用户注册与订阅概况。</p>

  <div class="statCards">
    <ElCard class="stat" shadow="never">
      <div class="k">总用户</div>
      <div class="v">{{ overview?.totalUsers ?? '—' }}</div>
      <div class="t">+{{ overview?.newUsersThisWeek ?? 0 }} 本周</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">本周新增</div>
      <div class="v">{{ overview?.newUsersThisWeek ?? '—' }}</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">收藏（订阅）</div>
      <div class="v">{{ overview?.totalFavorites ?? '—' }}</div>
      <div class="t">+{{ overview?.newFavoritesThisWeek ?? 0 }} 本周</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">待处理工单</div>
      <div class="v">—</div>
      <div class="t" style="color: var(--ink-faint)">工单模块上线后接入</div>
    </ElCard>
  </div>

  <div class="panel">
    <h3>注册趋势 · 近 7 天</h3>
    <div ref="chartEl" style="height: 220px"></div>
  </div>
</template>
