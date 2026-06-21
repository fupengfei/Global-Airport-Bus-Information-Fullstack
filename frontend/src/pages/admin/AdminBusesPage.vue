<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getTree, getBus, listVersions, type AdminTreeRow, type BusView, type VersionMeta } from '../../api/admin-bus'

const tree = ref<AdminTreeRow[]>([])
const current = ref<BusView | null>(null)
const versions = ref<VersionMeta[]>([])

const grouped = computed(() => {
  const map = new Map<string, { routes: { sourceId: string; route: string }[] }>()
  for (const r of tree.value) {
    if (!r.busSourceId) continue
    const key = `${r.countryName} · ${r.cityName} · ${r.airportName} (${r.airportCode})`
    if (!map.has(key)) map.set(key, { routes: [] })
    map.get(key)!.routes.push({ sourceId: r.busSourceId, route: r.busRoute ?? r.busSourceId })
  }
  return map
})

async function loadTree() { tree.value = await getTree() }
async function select(sourceId: string) {
  current.value = await getBus(sourceId)
  versions.value = await listVersions(sourceId)
}
onMounted(loadTree)
defineExpose({ current, versions, select, loadTree })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">巴士信息维护</h1>
  <p class="pageDesc">树形选 国家 / 城市 / 机场 → 编辑线路。<strong>保存即触发变更检测</strong>(content_hash 无变化不计版本)。</p>
  <div style="display: grid; grid-template-columns: 280px 1fr; gap: 18px">
    <div class="panel" style="margin: 0">
      <div class="tree">
        <details v-for="[group, info] in grouped" :key="group" open>
          <summary>{{ group }}</summary>
          <div v-for="r in info.routes" :key="r.sourceId" class="leaf">
            <span>{{ r.route }}</span>
            <a href="#" @click.prevent="select(r.sourceId)">编辑</a>
          </div>
        </details>
      </div>
    </div>
    <div class="panel" style="margin: 0">
      <p v-if="!current" class="formNote">从左侧选择一条线路进行编辑。</p>
      <div v-else>
        <h3 style="margin-top: 0">编辑线路 · {{ current.data.route }} <span class="formNote">v{{ current.version }}</span></h3>
        <div class="formNote">价格:{{ current.data.price }}</div>
      </div>
    </div>
  </div>
</template>
