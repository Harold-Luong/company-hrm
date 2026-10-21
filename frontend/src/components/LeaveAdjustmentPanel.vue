<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { leaveAdjustment as copy } from '@/content/leave.js'
import { common } from '@/content/common.js'

import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { leave } from '@/leave/api.js'
import { dateLabel, dateTimeLabel as timeLabel } from '@/shared/date.js'
import { leaveError } from '@/leave/errors.js'
import { leavePeriods } from '@/leave/constants.js'

const props = defineProps({
  request: { type: Object, required: true },
  own: Boolean,
  reviewer: Boolean,
})
const emit = defineEmits(['updated'])
const entries = ref([])
const loading = ref(true)
const saving = ref(false)
const stale = ref(false)
const error = useLocalizedMessage()
const note = ref('')
const form = reactive({
  cancelAll: true,
  startDate: props.request.startDate,
  endDate: props.request.endDate,
  period: props.request.period || 'FULL_DAY',
  reason: '',
})
const pending = computed(() => entries.value.find((entry) => entry.status === 'PENDING'))
const statuses = {
  get PENDING() {
    return copy.pending
  },
  get APPROVED() {
    return copy.approved
  },
  get REJECTED() {
    return copy.rejected
  },
  get CANCELLED() {
    return copy.withdrawn
  },
}
let generation = 0
onBeforeUnmount(() => generation++)
watch(
  () => [form.period, form.startDate],
  () => {
    if (form.period !== 'FULL_DAY') form.endDate = form.startDate
  },
)
watch(
  () => props.request.version,
  async () => {
    const current = ++generation
    loading.value = true
    try {
      const data = await leave.adjustments(props.request.id)
      if (current === generation) entries.value = data
    } catch (cause) {
      if (current === generation) {
        error.value = () => leaveError(cause)
        stale.value = true
      }
    } finally {
      if (current === generation) loading.value = false
    }
  },
  { immediate: true },
)
async function save(action) {
  if (saving.value || loading.value || stale.value) return
  error.value = ''
  if (action === 'submit' && !form.reason.trim()) {
    error.value = () => copy.reasonRequired
    return
  }
  if (action === 'reject' && !note.value.trim()) {
    error.value = () => copy.rejectionRequired
    return
  }
  saving.value = true
  const current = generation
  try {
    if (action === 'submit') {
      await leave.adjust(props.request.id, props.request.version, {
        cancelAll: form.cancelAll,
        reason: form.reason.trim(),
        startDate: form.cancelAll ? null : form.startDate,
        endDate: form.cancelAll ? null : form.endDate,
        period: form.cancelAll ? null : form.period,
      })
    } else {
      await leave.decideAdjustment(
        props.request.id,
        pending.value.id,
        action,
        props.request.version,
        note.value.trim(),
      )
    }
    if (current === generation) emit('updated')
  } catch (cause) {
    if (current !== generation) return
    error.value = () => leaveError(cause)
    if ([0, 409, 412, 428].includes(cause.status) || cause.status >= 500) stale.value = true
  } finally {
    if (current === generation) saving.value = false
  }
}
</script>

<template>
  <section class="panel create-form leave-section">
    <h2>{{ copy.title }}</h2>
    <p class="muted">{{ copy.description }}</p>
    <p v-if="loading" role="status">{{ copy.loading }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="stale" class="muted">{{ copy.reloadHint }}</p>
    <ol v-if="entries.length" class="leave-history">
      <li v-for="entry in entries" :key="entry.id">
        <strong>{{ statuses[entry.status] }}</strong> · {{ timeLabel(entry.createdAt) }}
        <p>
          {{
            copy.approvedSummary({
              start: dateLabel(entry.originalStart),
              end: dateLabel(entry.originalEnd),
              duration: leavePeriods[entry.originalPeriod],
              days: entry.originalUnits / 2,
            })
          }}
        </p>
        <p v-if="entry.cancelAll">{{ copy.cancelSummary }}</p>
        <p v-else>
          {{
            copy.remainingSummary({
              start: dateLabel(entry.startDate),
              end: dateLabel(entry.endDate),
              duration: leavePeriods[entry.period],
              days: entry.totalUnits / 2,
            })
          }}
        </p>
        <p v-if="request.leaveType === 'ANNUAL'">
          {{ copy.refundSummary({ days: (entry.originalUnits - entry.totalUnits) / 2 }) }}
        </p>
        <p class="leave-text">{{ copy.reasonSummary({ reason: entry.reason }) }}</p>
        <p v-if="entry.reviewNote" class="leave-text">
          {{ copy.reviewSummary({ reviewNote: entry.reviewNote }) }}
        </p>
      </li>
    </ol>
    <form
      v-if="!loading && request.status === 'APPROVED' && own && !pending"
      @submit.prevent="save('submit')"
    >
      <fieldset class="form-fields" :disabled="saving || stale">
        <div class="field">
          <label for="adjust-kind">{{ copy.adjustmentType }}</label>
          <select id="adjust-kind" v-model="form.cancelAll">
            <option :value="true">{{ copy.cancelAll }}</option>
            <option :value="false">{{ copy.reducePeriod }}</option>
          </select>
        </div>
        <template v-if="!form.cancelAll">
          <p class="muted">{{ copy.periodHint }}</p>
          <div class="field">
            <label for="adjust-period">{{ copy.remainingDuration }}</label
            ><select id="adjust-period" v-model="form.period">
              <option v-for="(label, value) in leavePeriods" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="adjust-start">{{ copy.remainingFrom }}</label
            ><input
              id="adjust-start"
              v-model="form.startDate"
              type="date"
              :min="request.startDate"
              :max="request.endDate"
              required
            />
          </div>
          <div class="field">
            <label for="adjust-end">{{ copy.remainingTo }}</label
            ><input
              id="adjust-end"
              v-model="form.endDate"
              type="date"
              :min="form.startDate"
              :max="request.endDate"
              :disabled="form.period !== 'FULL_DAY'"
              required
            />
          </div>
        </template>
        <div class="field">
          <label for="adjust-reason">{{ copy.reasonLabel }}</label
          ><textarea
            id="adjust-reason"
            v-model="form.reason"
            rows="3"
            :maxlength="fieldLimits.leaveReason"
            required
          ></textarea>
        </div>
        <button type="submit" class="button button-primary">
          {{ saving ? common.submitting : copy.submit }}
        </button>
      </fieldset>
    </form>
    <template v-if="!loading && pending">
      <p v-if="own" class="muted">{{ copy.pendingHint }}</p>
      <button
        v-if="own"
        type="button"
        class="button button-secondary"
        :disabled="saving || stale"
        @click="save('cancel')"
      >
        {{ copy.withdraw }}
      </button>
      <form v-else-if="reviewer" @submit.prevent="save('approve')">
        <fieldset class="form-fields" :disabled="saving || stale">
          <div class="field">
            <label for="adjust-note">{{ copy.reviewNote }}</label
            ><textarea
              id="adjust-note"
              v-model="note"
              rows="3"
              :maxlength="fieldLimits.leaveReason"
            ></textarea>
          </div>
          <div class="button-row">
            <button type="submit" class="button button-primary">{{ copy.approve }}</button
            ><button type="button" class="button button-secondary" @click="save('reject')">
              {{ copy.reject }}
            </button>
          </div>
        </fieldset>
      </form>
    </template>
  </section>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
