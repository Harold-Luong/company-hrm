<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'
import { COMPANY_TIMEZONE } from '@/config/presentation.js'

import { fieldLimits } from '@/config/validation.js'

import { attendanceOvertime as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { emptyPage } from '@/api/pagination.js'
import { useIdempotencyKey } from '@/composables/useIdempotencyKey.js'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { attendanceError } from '@/attendance/errors.js'
import { attendanceRoles } from '@/attendance/constants.js'
import { minutesLabel } from '@/shared/duration.js'
import { today } from '@/shared/date.js'
import { requestStatuses } from '@/attendance/requests.js'
import AttendanceRequestScope from '@/components/AttendanceRequestScope.vue'
import PageControls from '@/components/PageControls.vue'
const reviewer = computed(() => auth.hasRole(attendanceRoles))
const inbox = ref(false),
  status = ref(''),
  mutating = ref(false),
  message = useLocalizedMessage()
const {
  data: result,
  loading,
  error,
  load,
} = useAsyncResource((page = 0) => attendance.overtime(inbox.value, status.value, page), {
  initialValue: emptyPage,
  errorMessage: attendanceError,
})
const busy = computed(() => loading.value || mutating.value)
const form = reactive({ start: `${today()}T18:00`, end: `${today()}T20:00`, reason: '' })
const notes = reactive({})
const submissionKey = useIdempotencyKey(form)
watch([inbox, status], () => load())
const dateTime = (value) =>
  value
    ? new Intl.DateTimeFormat(getDisplayLocale(), {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: COMPANY_TIMEZONE,
      }).format(new Date(value))
    : common.missingValue

async function submit() {
  mutating.value = true
  error.value = ''
  message.value = ''
  try {
    if (!form.reason.trim() || form.end <= form.start) throw new Error(copy.invalidInput)
    await attendance.createOvertime({ ...form, reason: form.reason.trim() }, submissionKey.get())
    submissionKey.reset()
    message.value = () => copy.submitted
    await load()
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function action(row, operation) {
  mutating.value = true
  error.value = ''
  message.value = ''
  try {
    if (operation === 'APPROVED' || operation === 'REJECTED') {
      if (operation === 'REJECTED' && !notes[row.id]?.trim())
        throw new Error(copy.rejectionRequired)
      await attendance.decideOvertime(row, operation, notes[row.id] || '')
    } else if (operation === 'cancel') await attendance.cancelOvertime(row)
    else {
      const storage = `ot:${auth.state.user?.id}:${row.id}:${operation}`
      let punchKey = sessionStorage.getItem(storage)
      if (!punchKey) {
        punchKey = crypto.randomUUID()
        sessionStorage.setItem(storage, punchKey)
      }
      await attendance.punchOvertime(row, operation, punchKey)
    }
    message.value = () => copy.updated
    await load(result.value.page)
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="load()">
        {{ common.reload }}
      </button>
    </div>
    <AttendanceRequestScope v-if="reviewer" v-model="inbox" :busy="busy" />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form v-if="!inbox" class="panel create-form" @submit.prevent="submit">
      <h2>{{ copy.formTitle }}</h2>
      <p class="muted">{{ copy.calculationHint }}</p>
      <fieldset :disabled="busy">
        <div class="attendance-fields">
          <div class="field">
            <label for="ot-start">{{ copy.startsAt }}</label
            ><input id="ot-start" v-model="form.start" type="datetime-local" required />
          </div>
          <div class="field">
            <label for="ot-end">{{ copy.endsAt }}</label
            ><input
              id="ot-end"
              v-model="form.end"
              type="datetime-local"
              :min="form.start"
              required
            />
          </div>
        </div>
        <div class="field">
          <label for="ot-reason">{{ copy.reason }}</label
          ><textarea
            id="ot-reason"
            v-model="form.reason"
            required
            :maxlength="fieldLimits.attendanceReason"
          />
        </div>
        <button class="button button-primary">{{ copy.submit }}</button>
      </fieldset>
    </form>
    <section class="panel create-form">
      <h2>{{ inbox ? copy.inboxTitle : copy.mineTitle }}</h2>
      <div class="field">
        <label for="ot-status">{{ common.status }}</label
        ><select id="ot-status" v-model="status" :disabled="busy">
          <option value="">{{ common.all }}</option>
          <option v-for="(label, value) in requestStatuses" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>
      <article v-for="row in result.content" :key="row.id" class="panel create-form">
        <h3>{{ row.employeeName }} · {{ requestStatuses[row.status] }}</h3>
        <p>{{ dateTime(`${row.start}+07:00`) }} → {{ dateTime(`${row.end}+07:00`) }}</p>
        <p>{{ row.reason }}</p>
        <p>
          {{
            copy.durationSummary({
              approved: minutesLabel(row.approvedMinutes),
              recorded: minutesLabel(row.countedMinutes),
            })
          }}
        </p>
        <p>
          {{ copy.recordedTimes({ start: dateTime(row.checkIn), end: dateTime(row.checkOut) }) }}
        </p>
        <p v-if="row.reviewedAt">
          {{ copy.reviewedAt({ date: dateTime(row.reviewedAt), note: row.reviewNote }) }}
        </p>
        <template v-if="inbox && row.status === 'PENDING'">
          <div class="field">
            <label :for="`ot-note-${row.id}`">{{ copy.reviewNote }}</label
            ><textarea
              :id="`ot-note-${row.id}`"
              v-model="notes[row.id]"
              :maxlength="fieldLimits.attendanceReason"
              :disabled="busy"
            />
          </div>
          <div class="button-row">
            <button class="button button-primary" :disabled="busy" @click="action(row, 'APPROVED')">
              {{ copy.inboxTitle }}</button
            ><button
              class="button button-secondary"
              :disabled="busy"
              @click="action(row, 'REJECTED')"
            >
              {{ common.reject }}
            </button>
          </div>
        </template>
        <button
          v-if="!inbox && row.status === 'PENDING'"
          class="button button-secondary"
          :disabled="busy"
          @click="action(row, 'cancel')"
        >
          {{ copy.withdraw }}
        </button>
        <div v-if="!inbox && row.status === 'APPROVED'" class="button-row">
          <button
            class="button button-primary"
            :disabled="busy || !!row.checkIn"
            @click="action(row, 'check-in')"
          >
            {{ copy.startRecording }}</button
          ><button
            class="button button-secondary"
            :disabled="busy || !row.checkIn || !!row.checkOut"
            @click="action(row, 'check-out')"
          >
            {{ copy.finishRecording }}
          </button>
        </div>
      </article>
      <p v-if="!result.content.length" class="muted">{{ copy.empty }}</p>
      <PageControls
        :page="result.page"
        :pages="result.totalPages"
        :total="result.totalElements"
        :busy="busy"
        @change="load"
      />
    </section>
  </div>
</template>
