<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElInput, ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn } from 'element-plus'
import { getTree, getBus, listVersions, updateBus, verifyBus, deleteBus, rollbackVersion, type AdminTreeRow, type BusView, type VersionMeta } from '../../api/admin-bus'
import { asApiError } from '../../api/client'
import { useAuth } from '../../stores/auth'

const auth = useAuth()
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

const canDelete = computed(() => auth.user?.role === 'SUPER_ADMIN')

async function loadTree() { tree.value = await getTree() }
async function select(sourceId: string) {
  current.value = await getBus(sourceId)
  versions.value = await listVersions(sourceId)
}

function blankToNull(s: string | null): string | null {
  return s == null || s.trim() === '' ? null : s
}

async function save() {
  if (!current.value) return
  const c = current.value
  c.data.lastUpdated = blankToNull(c.data.lastUpdated)
  for (const a of c.data.alerts) { a.startDate = blankToNull(a.startDate); a.endDate = blankToNull(a.endDate) }
  try {
    current.value = await updateBus(c.sourceId, { airportCode: c.airportCode, version: c.version, data: c.data })
    versions.value = await listVersions(c.sourceId)
    ElMessage.success('已保存')
  } catch (e) {
    const err = asApiError(e)
    if (err?.code === 'BUS_VERSION_CONFLICT') {
      ElMessage.warning('该线路已被他人修改,正在重新加载最新版本')
      await select(c.sourceId)
    } else { ElMessage.error(err?.message ?? '保存失败') }
  }
}

async function verify() {
  if (!current.value) return
  await verifyBus(current.value.sourceId)
  ElMessage.success('已标记核对无误')
}

async function removeBus() {
  if (!current.value) return
  try {
    await ElMessageBox.confirm('确认下线该线路?', '下线确认', { type: 'warning' })
    await deleteBus(current.value.sourceId)
    current.value = null
    await loadTree()
    ElMessage.success('已下线')
  } catch { /* 取消或失败 */ }
}

function addStop() { current.value!.data.stops.push('') }
function removeStop(i: number) { current.value!.data.stops.splice(i, 1) }
function addSchedule() { current.value!.data.schedules.push({ timeRange: '', intervalText: '', note: '' }) }
function removeSchedule(i: number) { current.value!.data.schedules.splice(i, 1) }
function addAlert() { current.value!.data.alerts.push({ type: 'info', message: '', startDate: null, endDate: null }) }
function removeAlert(i: number) { current.value!.data.alerts.splice(i, 1) }
function addImage() { current.value!.data.images.push({ url: '', caption: null }) }
function removeImage(i: number) { current.value!.data.images.splice(i, 1) }
function addFile() { current.value!.data.files.push({ name: null, url: '' }) }
function removeFile(i: number) { current.value!.data.files.splice(i, 1) }

async function doRollback(version: number) {
  if (!current.value) return
  const saved = await rollbackVersion(current.value.sourceId, version)
  current.value = saved
  versions.value = await listVersions(saved.sourceId)
  ElMessage.success(`已回滚自 v${version}`)
}
async function confirmRollback(version: number) {
  try {
    await ElMessageBox.confirm(`确认把线路回滚到 v${version}?这会生成一个新版本。`, '回滚确认', { type: 'warning' })
    await doRollback(version)
  } catch { /* 用户取消 */ }
}

onMounted(loadTree)
defineExpose({ current, versions, select, loadTree, save, verify, removeBus, canDelete,
  addStop, removeStop, addSchedule, removeSchedule, addAlert, removeAlert, addImage, removeImage, addFile, removeFile,
  doRollback, confirmRollback })
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
        <div style="display:flex;justify-content:space-between;align-items:center">
          <h3 style="margin:0">编辑线路 · {{ current.data.route }}</h3>
          <span class="formNote">v{{ current.version }} · 乐观锁(冲突 409)</span>
        </div>
        <div class="formrow"><label>线路名 route</label><ElInput v-model="current.data.route" /></div>
        <div class="formrow"><label>目的地 destination</label><ElInput v-model="current.data.destination" /></div>
        <div class="formrow"><label>运营方 operator</label><ElInput v-model="current.data.operator" /></div>
        <div class="formrow"><label>官网 officialUrl</label><ElInput v-model="current.data.officialUrl" /></div>
        <div class="formrow"><label>时长 duration</label><ElInput v-model="current.data.duration" /></div>
        <div class="formrow"><label>价格 price</label><ElInput v-model="current.data.price" /></div>
        <div class="formrow"><label>运营时间 operatingHours</label><ElInput v-model="current.data.operatingHours" /></div>
        <div class="formrow"><label>数据日期 lastUpdated</label><ElInput v-model="current.data.lastUpdated" placeholder="YYYY-MM-DD" /></div>

        <h4>停靠站 stops</h4>
        <div v-for="(s, i) in current.data.stops" :key="'s'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <ElInput v-model="current.data.stops[i]" /><ElButton @click="removeStop(i)">删</ElButton>
        </div>
        <ElButton size="small" @click="addStop">+ 停靠站</ElButton>

        <h4>班次 schedules</h4>
        <div v-for="(s, i) in current.data.schedules" :key="'sc'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <ElInput v-model="s.timeRange" placeholder="时段" />
          <ElInput v-model="s.intervalText" placeholder="间隔" />
          <ElInput v-model="s.note" placeholder="备注" />
          <ElButton @click="removeSchedule(i)">删</ElButton>
        </div>
        <ElButton size="small" @click="addSchedule">+ 班次</ElButton>

        <h4>提示 alerts</h4>
        <div v-for="(a, i) in current.data.alerts" :key="'al'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <ElInput v-model="a.type" placeholder="类型" style="width:90px" />
          <ElInput v-model="a.message" placeholder="内容" />
          <ElInput v-model="a.startDate" placeholder="起" style="width:120px" />
          <ElInput v-model="a.endDate" placeholder="止" style="width:120px" />
          <ElButton @click="removeAlert(i)">删</ElButton>
        </div>
        <ElButton size="small" @click="addAlert">+ 提示</ElButton>

        <h4>图片 images</h4>
        <div v-for="(m, i) in current.data.images" :key="'im'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <ElInput v-model="m.url" placeholder="url" /><ElInput v-model="m.caption" placeholder="说明" />
          <ElButton @click="removeImage(i)">删</ElButton>
        </div>
        <ElButton size="small" @click="addImage">+ 图片</ElButton>

        <h4>文件 files</h4>
        <div v-for="(f, i) in current.data.files" :key="'fl'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <ElInput v-model="f.name" placeholder="名称" /><ElInput v-model="f.url" placeholder="url" />
          <ElButton @click="removeFile(i)">删</ElButton>
        </div>
        <ElButton size="small" @click="addFile">+ 文件</ElButton>

        <div style="display:flex;gap:10px;margin-top:16px">
          <ElButton type="primary" @click="save">保存(触发推送)</ElButton>
          <ElButton @click="verify">核对无误</ElButton>
          <ElButton v-if="canDelete" type="danger" @click="removeBus">下线</ElButton>
        </div>

        <h4 style="margin-top:20px">版本历史</h4>
        <ElTable :data="versions" style="width:100%">
          <ElTableColumn label="版本" width="80">
            <template #default="{ row }">v{{ row.version }}</template>
          </ElTableColumn>
          <ElTableColumn prop="actor" label="操作人" width="120" />
          <ElTableColumn prop="createdAt" label="时间" width="180" />
          <ElTableColumn prop="changedSummary" label="变更摘要" />
          <ElTableColumn label="操作" width="100">
            <template #default="{ row }">
              <ElButton size="small" @click="confirmRollback(row.version)">回滚</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>
  </div>
</template>
