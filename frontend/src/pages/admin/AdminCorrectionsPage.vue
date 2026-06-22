<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElInput, ElButton } from 'element-plus'
import { listCorrections, updateCorrection, type CorrectionReport } from '../../api/corrections'

const rows = ref<CorrectionReport[]>([])
const notes = ref<Record<number, string>>({})

async function load() { rows.value = await listCorrections('') }
onMounted(load)

async function setStatus(id: number, status: string) {
  await updateCorrection(id, { status, resolutionNote: notes.value[id] ?? '' })
  await load()
}
defineExpose({ setStatus })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">纠错队列</h1>
  <p class="pageDesc">匿名旅客上报的数据纠错。处理后置为已解决 / 已忽略,可填内部备注。</p>

  <div class="panel">
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="status" label="状态" width="100" />
      <ElTableColumn prop="relatedSourceId" label="线路" width="120" />
      <ElTableColumn prop="description" label="问题描述" />
      <ElTableColumn prop="contact" label="联系方式" width="160" />
      <ElTableColumn prop="createdAt" label="时间" width="170" />
      <ElTableColumn label="处理" width="320">
        <template #default="{ row }">
          <ElInput v-model="notes[row.id]" placeholder="内部备注(可选)" size="small" style="margin-bottom:6px" />
          <ElButton size="small" type="success" @click="setStatus(row.id, 'RESOLVED')">已解决</ElButton>
          <ElButton size="small" @click="setStatus(row.id, 'DISMISSED')">忽略</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
