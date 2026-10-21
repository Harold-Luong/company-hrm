<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'

import { fieldLimits } from '@/config/validation.js'

import { attendanceShifts as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { emptyPage } from '@/api/pagination.js'
import { computed, onMounted, reactive, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { attendanceError } from '@/attendance/errors.js'
import { shiftBody, shiftLabel } from '@/attendance/shifts.js'
import { minutesLabel, minuteOfDay } from '@/shared/duration.js'
import PageControls from '@/components/PageControls.vue'
const mutating = ref(false),
  message = useLocalizedMessage(),
  stale = ref(false)
const {
  data: result,
  loading,
  error,
  load,
} = useAsyncResource((page = result.value.page) => attendance.shifts(page), {
  initialValue: emptyPage,
  errorMessage: attendanceError,
  resetOnLoad: false,
})
const busy = computed(() => loading.value || mutating.value)
const editing = ref(null),
  history = ref([])
const defaults = () => ({
  name: '',
  overnight: false,
  intervals: [
    { period: 'MORNING', start: '08:00', end: '12:00' },
    { period: 'AFTERNOON', start: '13:30', end: '17:30' },
  ],
  checkInFrom: '06:00',
  checkOutUntil: '22:00',
})
const form = reactive(defaults())
const duration = computed(() =>
  form.intervals.reduce((sum, interval) => {
    return (
      sum +
      Math.max(
        0,
        minuteOfDay(interval.end) - minuteOfDay(interval.start) + (form.overnight ? 1440 : 0),
      )
    )
  }, 0),
)
function reset() {
  editing.value = null
  history.value = []
  stale.value = false
  Object.assign(form, defaults())
}

async function edit(id) {
  mutating.value = true
  error.value = ''
  message.value = ''
  try {
    const [shift, revisions] = await Promise.all([
      attendance.shift(id),
      attendance.shiftHistory(id),
    ])
    editing.value = shift
    Object.assign(form, structuredClone(shift.definition))
    history.value = revisions.content
    stale.value = false
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function save() {
  error.value = ''
  message.value = ''
  try {
    const body = shiftBody(form)
    mutating.value = true
    await attendance.saveShift(editing.value?.id, body, editing.value?.version)
    reset()
    await load(0)
    message.value = () => copy.saved
  } catch (cause) {
    stale.value = [412, 428].includes(cause.status)
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function archive(row) {
  if (!window.confirm(copy.deactivateConfirmation({ name: row.definition.name }))) return
  mutating.value = true
  error.value = ''
  try {
    await attendance.archiveShift(row.id, row.version)
    if (editing.value?.id === row.id) reset()
    await load()
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
function addInterval() {
  form.intervals.push({
    period: form.intervals.some((i) => i.period === 'MORNING') ? 'AFTERNOON' : 'MORNING',
    start: '13:30',
    end: '17:30',
  })
}
onMounted(() => load(0))
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
        {{ copy.reload }}
      </button>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form class="panel create-form" @submit.prevent="save">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ editing ? copy.editTitle : copy.createTitle }}</h2>
          <p class="muted small">{{ copy.formHint }}</p>
        </div>
        <span class="attendance-badge">{{ editing ? copy.editing : copy.newShift }}</span>
      </div>
      <fieldset :disabled="busy || stale || editing?.active === false">
        <div class="field">
          <label for="shift-name">{{ copy.name }}</label
          ><input
            id="shift-name"
            v-model="form.name"
            :maxlength="fieldLimits.shiftName"
            :placeholder="copy.namePlaceholder"
            required
          />
        </div>
        <label><input v-model="form.overnight" type="checkbox" /> {{ copy.overnight }}</label>
        <p v-if="form.overnight" class="muted">{{ copy.overnightHint }}</p>
        <h3 class="attendance-form-title">{{ copy.intervals }}</h3>
        <div v-for="(interval, index) in form.intervals" :key="index" class="attendance-interval">
          <div class="field">
            <label :for="`period-${index}`">{{ copy.intervalLabel({ number: index + 1 }) }}</label
            ><select :id="`period-${index}`" v-model="interval.period">
              <option value="MORNING">{{ copy.morning }}</option>
              <option value="AFTERNOON">{{ form.overnight ? copy.night : copy.afternoon }}</option>
            </select>
          </div>
          <div class="field">
            <label :for="`start-${index}`">{{ copy.intervalStart({ number: index + 1 }) }}</label
            ><input :id="`start-${index}`" v-model="interval.start" type="time" required />
          </div>
          <div class="field">
            <label :for="`end-${index}`">{{ copy.intervalEnd({ number: index + 1 }) }}</label
            ><input :id="`end-${index}`" v-model="interval.end" type="time" required />
          </div>
          <button
            class="button button-secondary"
            type="button"
            :disabled="form.intervals.length === 1"
            @click="form.intervals.splice(index, 1)"
          >
            {{ copy.removeInterval({ number: index + 1 }) }}
          </button>
        </div>
        <button
          v-if="form.intervals.length < 2"
          class="text-button"
          type="button"
          @click="addInterval"
        >
          {{ copy.addInterval }}
        </button>
        <div class="attendance-duration">
          <span>{{ copy.dailyDuration }}</span
          ><strong>{{
            Number.isFinite(duration) ? minutesLabel(duration) : common.missingValue
          }}</strong
          ><small>{{ copy.durationHint }}</small>
        </div>
        <div class="attendance-fields">
          <div class="field">
            <label for="window-from">{{ copy.recordingStart }}</label
            ><input id="window-from" v-model="form.checkInFrom" type="time" required />
          </div>
          <div class="field">
            <label for="window-until">{{ copy.recordingEnd }}</label
            ><input id="window-until" v-model="form.checkOutUntil" type="time" required />
          </div>
        </div>
        <p class="muted">{{ copy.timezoneHint }}</p>
        <div class="button-row">
          <button class="button button-primary">{{ busy ? common.saving : copy.save }}</button>
        </div>
      </fieldset>
      <button
        v-if="editing"
        class="button button-secondary"
        type="button"
        :disabled="busy"
        @click="reset"
      >
        {{ copy.createAnother }}
      </button>
      <button
        v-if="stale && editing"
        class="button button-secondary"
        type="button"
        :disabled="busy"
        @click="edit(editing.id)"
      >
        {{ copy.reloadEditing }}
      </button>
      <p v-if="editing" class="muted small">{{ copy.versionHint }}</p>
      <details v-if="history.length">
        <summary>{{ copy.history({ count: history.length }) }}</summary>
        <ul>
          <li v-for="item in history" :key="item.id">
            {{
              copy.historyVersion({
                version: item.shiftVersion,
                date: new Date(item.occurredAt).toLocaleString(getDisplayLocale()),
                status: item.active ? copy.active : copy.inactive,
              })
            }}
          </li>
        </ul>
      </details>
    </form>
    <section class="panel create-form">
      <h2>{{ copy.listTitle }}</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ copy.name }}</th>
              <th>{{ copy.hours }}</th>
              <th>{{ copy.duration }}</th>
              <th>{{ common.status }}</th>
              <th>{{ common.actions }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in result.content" :key="row.id">
              <td>{{ row.definition.name }}</td>
              <td>{{ shiftLabel(row.definition) }}</td>
              <td>{{ minutesLabel(row.requiredMinutes) }}</td>
              <td>
                <span class="attendance-badge" :class="row.active ? 'status-open' : ''">{{
                  row.active ? copy.active : copy.inactive
                }}</span>
              </td>
              <td>
                <div class="button-row">
                  <button class="text-button" :disabled="busy" @click="edit(row.id)">
                    {{ copy.edit }}</button
                  ><button
                    v-if="row.active"
                    class="text-button"
                    :disabled="busy"
                    @click="archive(row)"
                  >
                    {{ copy.deactivate }}
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!result.content.length">
              <td colspan="5">
                <div class="attendance-empty">
                  <strong>{{ busy ? copy.loading : error ? copy.loadFailed : copy.empty }}</strong
                  ><span>{{ error ? copy.retryHint : copy.emptyHint }}</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
