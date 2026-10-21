<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { attendancePeriod as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { onMounted, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { attendanceError } from '@/attendance/errors.js'
import { today } from '@/shared/date.js'

const props = defineProps({ busy: Boolean })
const emit = defineEmits(['updated'])
const periodMonth = ref(today().slice(0, 7))
const period = ref(null)
const periodHistory = ref([])
const periodReason = ref('')
const periodBusy = ref(false)
const periodError = useLocalizedMessage()
const periodNotice = ref('')
function resetPeriod() {
  period.value = null
  periodHistory.value = []
  periodNotice.value = ''
}
async function loadPeriod() {
  if (periodBusy.value) return
  periodBusy.value = true
  period.value = null
  periodError.value = ''
  periodHistory.value = []
  const month = periodMonth.value
  try {
    const [status, history] = await Promise.all([
      attendance.period(month),
      attendance.periodHistory(month),
    ])
    if (month !== periodMonth.value) return
    period.value = status
    periodHistory.value = history
  } catch (cause) {
    periodError.value = () => attendanceError(cause)
  } finally {
    periodBusy.value = false
  }
}
async function checkPeriodSources() {
  if (!period.value?.closed || periodBusy.value) return
  periodBusy.value = true
  periodError.value = ''
  periodNotice.value = ''
  try {
    const result = await attendance.checkPeriodSources(period.value.month)
    periodNotice.value = result.changed ? copy.sourcesChanged : copy.sourcesUnchanged
  } catch (cause) {
    periodError.value = () => attendanceError(cause)
  } finally {
    periodBusy.value = false
  }
}
async function changePeriod() {
  if (!period.value || periodBusy.value || !periodReason.value.trim() || props.busy) return
  const current = period.value
  periodBusy.value = true
  periodError.value = ''
  periodNotice.value = ''
  try {
    await attendance.changePeriod(
      current.month,
      current.closed ? 'reopen' : 'close',
      current.version,
      periodReason.value.trim(),
    )
    periodNotice.value = current.closed ? copy.reopened : copy.closed
    periodReason.value = ''
  } catch (cause) {
    periodError.value = () => attendanceError(cause)
  } finally {
    periodBusy.value = false
  }
  const failure = periodError.value
  await loadPeriod()
  if (failure) periodError.value = failure
  emit('updated')
}

onMounted(loadPeriod)
</script>

<template>
  <section class="panel report-period-panel" :aria-label="copy.sectionLabel">
    <div class="report-period-intro">
      <h2>{{ copy.title }}</h2>
      <p class="muted">{{ copy.description }}</p>
      <p class="muted">{{ copy.snapshotHint }}</p>
    </div>
    <form class="report-period-toolbar" @submit.prevent="loadPeriod">
      <div class="field">
        <label for="report-period-month">{{ common.month }}</label>
        <input
          id="report-period-month"
          v-model="periodMonth"
          type="month"
          required
          :disabled="periodBusy"
          @change="resetPeriod"
        />
      </div>
      <button class="button button-secondary" :disabled="periodBusy">{{ copy.loadStatus }}</button>
    </form>
    <p v-if="period" class="report-period-status">
      <strong
        >{{ period.month }} · {{ period.closed ? copy.closedStatus : copy.openStatus }}</strong
      >
    </p>
    <button
      v-if="period?.closed"
      class="button button-secondary report-period-check"
      :disabled="periodBusy"
      @click="checkPeriodSources"
    >
      {{ copy.checkSources }}
    </button>
    <form v-if="period" class="report-period-action" @submit.prevent="changePeriod">
      <div class="field">
        <label for="report-period-reason">{{
          copy.reasonLabel({ action: period.closed ? copy.reopenVerb : copy.closeVerb })
        }}</label>
        <input
          id="report-period-reason"
          v-model="periodReason"
          required
          :maxlength="fieldLimits.periodReason"
          :disabled="periodBusy"
        />
      </div>
      <button class="button button-primary" :disabled="periodBusy || busy || !periodReason.trim()">
        {{ periodBusy ? copy.processing : period.closed ? copy.reopenCompany : copy.closeCompany }}
      </button>
    </form>
    <p v-if="periodError" class="alert alert-error" role="alert">{{ periodError }}</p>
    <p v-if="periodNotice" class="alert alert-success" role="status">{{ periodNotice }}</p>
    <details v-if="periodHistory.length" class="report-period-history">
      <summary>{{ copy.history }}</summary>
      <ul>
        <li v-for="item in periodHistory" :key="item.version">
          {{ item.closed ? copy.closeAction : copy.reopenAction }} · {{ item.changedAt }} ·
          {{ item.reason }}
        </li>
      </ul>
    </details>
  </section>
</template>
