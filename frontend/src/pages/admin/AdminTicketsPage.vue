<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElTable, ElTableColumn, ElInput, ElButton } from 'element-plus'
import {
  adminListTickets, adminGetTicket, adminReplyTicket, adminCloseTicket,
  type Ticket, type TicketThread,
} from '../../api/tickets'

const rows = ref<Ticket[]>([])
const threads = reactive<Record<number, TicketThread>>({})
const replyDraft = reactive<Record<number, string>>({})
const opened = ref<number | null>(null)

async function load() { rows.value = await adminListTickets('') }
onMounted(load)

async function openThread(id: number) {
  opened.value = opened.value === id ? null : id
  if (opened.value === id) threads[id] = await adminGetTicket(id)
}
async function sendReply(id: number) {
  const body = (replyDraft[id] ?? '').trim()
  if (!body) return
  threads[id] = await adminReplyTicket(id, body)
  replyDraft[id] = ''
  await load()
}
async function close(id: number) { await adminCloseTicket(id); await load() }
defineExpose({ openThread, sendReply, close, replyDraft, threads })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">工单队列</h1>
  <p class="pageDesc">用户提交的建议/纠错工单。回复后用户会收到 TICKET_REPLIED 站内信。</p>

  <div class="panel">
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="status" label="状态" width="100" />
      <ElTableColumn prop="id" label="#" width="80" />
      <ElTableColumn prop="relatedSourceId" label="线路" width="140" />
      <ElTableColumn prop="userId" label="用户" width="90" />
      <ElTableColumn prop="lastReplyAt" label="最后回复" width="180" />
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openThread(row.id)">查看线程</ElButton>
          <ElButton v-if="row.status !== 'CLOSED'" size="small" type="danger" @click="close(row.id)">关闭</ElButton>
          <div v-if="opened === row.id && threads[row.id]" style="margin-top:10px">
            <div v-for="rp in threads[row.id].replies" :key="rp.id"
                 class="bubble" :class="rp.authorType === 'ADMIN' ? 'admin' : 'user'">
              <div class="who">{{ rp.authorType }} · {{ rp.createdAt }}</div>{{ rp.body }}
            </div>
            <ElInput v-model="replyDraft[row.id]" type="textarea" placeholder="回复用户(发出后用户收站内信)" style="margin:8px 0" />
            <ElButton size="small" type="primary" @click="sendReply(row.id)">回复</ElButton>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
