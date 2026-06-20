<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElRadioGroup, ElRadioButton } from 'element-plus'
import { getHotness, type HotnessRow } from '../../api/admin'

const window = ref<'7d' | '30d' | 'all'>('7d')
const rows = ref<HotnessRow[]>([])

async function reload() { rows.value = await getHotness(window.value) }
onMounted(reload)

defineExpose({ window, reload })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">机场搜索热度</h1>
  <p class="pageDesc">按机场展示搜索热度排行,指导数据维护优先级。</p>

  <div class="panel">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px">
      <h3 style="margin: 0">热度榜单</h3>
      <ElRadioGroup v-model="window" size="small" @change="reload">
        <ElRadioButton value="7d">近 7 天</ElRadioButton>
        <ElRadioButton value="30d">近 30 天</ElRadioButton>
        <ElRadioButton value="all">全部</ElRadioButton>
      </ElRadioGroup>
    </div>
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="airportName" label="名称" />
      <ElTableColumn prop="cityName" label="城市" width="140" />
      <ElTableColumn prop="views" label="搜索量" width="120" />
    </ElTable>
  </div>
</template>
