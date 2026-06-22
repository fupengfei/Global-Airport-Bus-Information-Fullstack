<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { submitCorrection } from '../api/corrections'

const props = defineProps<{ sourceId: string }>()
const { t } = useI18n()
const open = ref(false)
const description = ref('')
const contact = ref('')
const sent = ref(false)
const error = ref('')
const submitting = ref(false)

function show() { open.value = true; sent.value = false; error.value = '' }
function close() { open.value = false; description.value = ''; contact.value = ''; error.value = '' }

async function submit() {
  if (submitting.value) return
  if (!description.value.trim()) { error.value = t('report.descRequired'); return }
  error.value = ''
  submitting.value = true
  try {
    await submitCorrection({ sourceId: props.sourceId, description: description.value.trim(), contact: contact.value.trim() })
    sent.value = true
  } catch {
    error.value = t('report.failed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <button class="reportTrigger" data-test="report-trigger" @click="show">{{ t('report.trigger') }}</button>

  <div class="overlay" :class="{ open }" data-test="report-overlay" @click.self="close">
    <div class="modal" role="dialog" aria-modal="true" :aria-label="t('report.title')">
      <button class="modalClose" :aria-label="t('report.cancel')" @click="close">✕</button>
      <h3>{{ t('report.title') }}</h3>
      <p class="modalSub">{{ t('report.sub') }}</p>

      <template v-if="!sent">
        <div class="formrow">
          <label>{{ t('report.descLabel') }}</label>
          <textarea class="input" data-test="report-desc" v-model="description" :placeholder="t('report.descPh')"></textarea>
        </div>
        <div class="formrow">
          <label>{{ t('report.contactLabel') }}</label>
          <input class="input" data-test="report-contact" v-model="contact" type="text" :placeholder="t('report.contactPh')" />
        </div>
        <p v-if="error" class="formNote" style="color:var(--alert-red)" data-test="report-error">{{ error }}</p>
        <div class="modalActions">
          <button class="btn btn-primary" data-test="report-submit" :disabled="submitting" @click="submit">{{ t('report.submit') }}</button>
          <button class="btn btn-ghost" @click="close">{{ t('report.cancel') }}</button>
        </div>
      </template>
      <p v-else data-test="report-sent" style="font-weight:600">{{ t('report.sent') }}</p>
    </div>
  </div>
</template>
