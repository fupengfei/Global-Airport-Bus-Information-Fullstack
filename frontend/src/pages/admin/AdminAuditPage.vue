<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElOption } from 'element-plus'
import { listAudit, type AuditRow } from '../../api/admin-audit'

const rows = ref<AuditRow[]>([])
const action = ref<string>('')
const actions = ['CREATE_BUS', 'UPDATE_BUS', 'DELETE_BUS', 'VERIFY_BUS', 'ROLLBACK_BUS']

async function reload() {
  rows.value = await listAudit(action.value ? { action: action.value } : {})
}
onMounted(reload)
defineExpose({ action, reload })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">操作记录</h1>
  <p class="pageDesc">管理端所有写操作全量记录(谁 / 何时 / 改了哪个对象)。</p>
  <div class="panel">
    <div style="margin-bottom: 14px">
      <ElSelect v-model="action" placeholder="全部动作" clearable size="small" style="width: 180px" @change="reload">
        <ElOption v-for="a in actions" :key="a" :label="a" :value="a" />
      </ElSelect>
    </div>
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="createdAt" label="时间" width="180" />
      <ElTableColumn prop="actorId" label="操作人" width="90" />
      <ElTableColumn prop="action" label="动作" width="140" />
      <ElTableColumn prop="targetId" label="对象" />
      <ElTableColumn prop="ip" label="IP" width="140" />
    </ElTable>
  </div>
</template>
