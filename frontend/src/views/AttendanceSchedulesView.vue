<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'

import { fieldLimits } from '@/config/validation.js'

import { attendanceSchedules as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { emptyPage } from '@/api/pagination.js'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { attendance } from '@/attendance/api.js'
import { today } from '@/shared/date.js'
import { weekdays } from '@/attendance/constants.js'
import { shiftLabel } from '@/attendance/shifts.js'
import { minutesLabel } from '@/shared/duration.js'
import { attendanceError } from '@/attendance/errors.js'
import { hrm } from '@/hrm/api.js'
import { fullName } from '@/hrm/utils.js'
import ReferencePicker from '@/components/ReferencePicker.vue'
import PageControls from '@/components/PageControls.vue'
const shifts = ref([]),
  busy = ref(false),
  error = useLocalizedMessage(),
  message = useLocalizedMessage(),
  preview = ref(null),
  selectedEmployee = ref('')
const selectedNames = reactive({}),
  history = ref(emptyPage())
const form = reactive({
  shiftId: '',
  scope: 'COMPANY_DEFAULT',
  employeeIds: [],
  from: today(),
  until: '',
  weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  reason: '',
})
let applyKey = ''
const selectedShift = computed(() => shifts.value.find((s) => s.id === form.shiftId))
watch(
  form,
  () => {
    preview.value = null
    applyKey = ''
  },
  { deep: true },
)
async function load() {
  busy.value = true
  error.value = ''
  preview.value = null
  try {
    const loaded = []
    for (let page = 0, pages = 1; page < pages; page++) {
      const result = await attendance.shifts(page)
      loaded.push(...result.content)
      pages = result.totalPages
    }
    shifts.value = loaded.filter((s) => s.active)
    if (!shifts.value.some((s) => s.id === form.shiftId)) form.shiftId = shifts.value[0]?.id || ''
    history.value = await attendance.scheduleHistory()
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function loadHistory(page) {
  busy.value = true
  error.value = ''
  try {
    history.value = await attendance.scheduleHistory(page)
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function addEmployee() {
  if (!selectedEmployee.value || form.employeeIds.includes(selectedEmployee.value)) return
  busy.value = true
  error.value = ''
  try {
    const person = await hrm.get('employees', selectedEmployee.value)
    selectedNames[person.id] = `${person.employeeCode} · ${fullName(person)}`
    form.employeeIds.push(person.id)
    selectedEmployee.value = ''
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
function body() {
  if (!selectedShift.value || !form.weekdays.length || !form.reason.trim())
    throw new Error(copy.invalidInput)
  if (form.scope === 'SELECTED_EMPLOYEES' && !form.employeeIds.length)
    throw new Error(copy.employeeRequired)
  return {
    ...form,
    employeeIds: form.scope === 'SELECTED_EMPLOYEES' ? [...form.employeeIds] : [],
    shiftVersion: selectedShift.value.version,
    until: form.until || null,
    weekdays: [...form.weekdays],
    reason: form.reason.trim(),
  }
}
async function showPreview() {
  busy.value = true
  error.value = ''
  message.value = ''
  preview.value = null
  try {
    const request = body()
    const result = await attendance.preview(request)
    preview.value = { ...result, request }
    applyKey = crypto.randomUUID()
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function apply() {
  if (!preview.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await attendance.apply(preview.value.request, preview.value.scheduleRevision, applyKey)
    message.value = () => copy.applied
    preview.value = null
    history.value = await attendance.scheduleHistory()
  } catch (cause) {
    if ([409, 412, 428].includes(cause.status)) preview.value = null
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
function batchSummary(value) {
  try {
    const request = JSON.parse(value)
    return `${request.from} → ${request.until || copy.unlimited} · ${request.reason}`
  } catch {
    return ''
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
      <button class="button button-secondary" :disabled="busy" @click="load">
        {{ common.reload }}
      </button>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form class="panel create-form" @submit.prevent="showPreview">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ copy.formTitle }}</h2>
          <p class="muted small">{{ copy.formHint }}</p>
        </div>
        <span class="attendance-badge">{{ preview ? copy.ready : copy.newAssignment }}</span>
      </div>
      <fieldset :disabled="busy">
        <h3 class="attendance-step">
          <span>{{ copy.firstStep }}</span> {{ copy.scopeTitle }}
        </h3>
        <div class="attendance-fields">
          <div class="field">
            <label for="schedule-shift">{{ copy.shiftLabel }}</label
            ><select id="schedule-shift" v-model="form.shiftId" required>
              <option value="">{{ copy.shiftPlaceholder }}</option>
              <option v-for="s in shifts" :key="s.id" :value="s.id">
                {{ s.definition.name }} · {{ shiftLabel(s.definition) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="schedule-scope">{{ copy.scopeLabel }}</label
            ><select id="schedule-scope" v-model="form.scope">
              <option value="COMPANY_DEFAULT">{{ copy.companyDefault }}</option>
              <option value="SELECTED_EMPLOYEES">{{ copy.selectedEmployees }}</option>
              <option value="ALL_EMPLOYEES">{{ copy.allEmployees }}</option>
            </select>
          </div>
        </div>
        <p v-if="form.scope === 'COMPANY_DEFAULT'" class="info-strip">{{ copy.defaultHint }}</p>
        <p v-if="form.scope === 'SELECTED_EMPLOYEES'" class="info-strip">{{ copy.overrideHint }}</p>
        <p v-if="selectedShift" class="muted">
          {{ copy.dailyDuration({ duration: minutesLabel(selectedShift.requiredMinutes) }) }}
        </p>
        <div v-if="form.scope === 'SELECTED_EMPLOYEES'">
          <ReferencePicker
            v-model="selectedEmployee"
            resource="employees"
            :label="copy.employeeLabel"
          /><button
            class="button button-secondary"
            type="button"
            :disabled="!selectedEmployee || form.employeeIds.length >= 100"
            @click="addEmployee"
          >
            {{ copy.addEmployee }}
          </button>
          <div>
            <span v-for="id in form.employeeIds" :key="id" class="attendance-chip"
              >{{ selectedNames[id] || id
              }}<button
                class="text-button"
                type="button"
                :aria-label="copy.removeEmployee({ name: selectedNames[id] || id })"
                @click="form.employeeIds = form.employeeIds.filter((value) => value !== id)"
              >
                ×
              </button></span
            >
          </div>
        </div>
        <p v-if="form.scope === 'ALL_EMPLOYEES'" class="info-strip">{{ copy.replacementHint }}</p>
        <h3 class="attendance-step">
          <span>{{ copy.secondStep }}</span> {{ copy.periodTitle }}
        </h3>
        <div class="attendance-fields">
          <div class="field">
            <label for="schedule-from">{{ copy.startsAt }}</label
            ><input id="schedule-from" v-model="form.from" type="date" :min="today()" required />
          </div>
          <div class="field">
            <label for="schedule-until">{{ copy.endsAt }}</label
            ><input id="schedule-until" v-model="form.until" type="date" :min="form.from" />
          </div>
        </div>
        <div class="attendance-days" role="group" :aria-label="copy.weekdays">
          <label v-for="[value, label] in weekdays" :key="value"
            ><input v-model="form.weekdays" type="checkbox" :value="value" />{{ label }}</label
          >
        </div>
        <p class="muted small">{{ copy.weekdaysHint }}</p>
        <h3 class="attendance-step">
          <span>{{ copy.thirdStep }}</span> {{ copy.confirmationTitle }}
        </h3>
        <div class="field">
          <label for="schedule-reason">{{ copy.reason }}</label
          ><textarea
            id="schedule-reason"
            v-model="form.reason"
            required
            :maxlength="fieldLimits.attendanceReason"
            rows="3"
            :placeholder="copy.reasonPlaceholder"
          ></textarea>
        </div>
        <button class="button button-primary" :disabled="!shifts.length">
          {{ busy ? copy.processing : copy.preview }}
        </button>
        <RouterLink v-if="!shifts.length" to="/attendance/shifts">{{
          copy.createShift
        }}</RouterLink>
      </fieldset>
      <section v-if="preview" class="attendance-preview" :aria-label="copy.previewTitle">
        <h2>{{ copy.preview }}</h2>
        <p>
          {{
            copy.previewSummary({
              name: preview.shift.definition.name,
              schedule: shiftLabel(preview.shift.definition),
              duration: minutesLabel(preview.shift.requiredMinutes),
            })
          }}
        </p>
        <p>{{ preview.request.from }} → {{ preview.request.until || copy.unlimitedEnd }}</p>
        <p>{{ copy.affectedSchedules({ count: preview.affectedOverrides.length }) }}</p>
        <p
          v-if="
            preview.recordedDayConflicts ||
            preview.approvedLeaveConflicts ||
            preview.approvedRequestConflicts ||
            preview.approvedOvertimeConflicts
          "
          class="alert alert-error"
          role="alert"
        >
          {{
            copy.conflicts({
              recordedDays: preview.recordedDayConflicts,
              approvedLeave: preview.approvedLeaveConflicts,
              approvedRequests: preview.approvedRequestConflicts || 0,
              approvedOvertime: preview.approvedOvertimeConflicts || 0,
            })
          }}
        </p>
        <button
          type="button"
          class="button button-primary"
          :disabled="
            busy ||
            preview.recordedDayConflicts > 0 ||
            preview.approvedLeaveConflicts > 0 ||
            preview.approvedRequestConflicts > 0 ||
            preview.approvedOvertimeConflicts > 0
          "
          @click="apply"
        >
          {{ copy.apply }}
        </button>
      </section>
    </form>
    <section class="panel create-form">
      <h2>{{ copy.historyTitle }}</h2>
      <ul class="attendance-history">
        <li v-for="item in history.content" :key="item.id">
          {{ batchSummary(item.requestBody)
          }}<small> · {{ new Date(item.occurredAt).toLocaleString(getDisplayLocale()) }}</small>
        </li>
      </ul>
      <div v-if="!history.content.length" class="attendance-empty">
        <strong>{{ busy ? copy.historyLoading : copy.empty }}</strong
        ><span>{{ copy.emptyHint }}</span>
      </div>
      <PageControls
        :page="history.page"
        :pages="history.totalPages"
        :total="history.totalElements"
        :busy="busy"
        @change="loadHistory"
      />
    </section>
  </div>
</template>
