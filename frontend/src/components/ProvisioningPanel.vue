<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { provisioningLookup } from '@/config/runtime.js'

import { getDisplayLocale } from '@/i18n/index.js'

import { fieldLimits } from '@/config/validation.js'

import { provisioning as copy } from '@/content/hrm.js'
import { common } from '@/content/common.js'

import { onBeforeUnmount, onMounted, ref } from 'vue'
import { hrm } from '@/hrm/api.js'
import { uuidPattern } from '@/hrm/utils.js'
import { requestStatuses, accountStatuses } from '@/hrm/constants.js'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { serviceError } from '@/api/errors.js'
import ActivationInvitationPanel from '@/components/ActivationInvitationPanel.vue'
import {
  trackAccountRequest,
  subscribeAccountResults,
  provisioningFailures as failures,
} from '@/notifications/accountResults.js'
const props = defineProps({ employee: { type: Object, required: true } })
const emit = defineEmits(['updated'])
const requesterId = String(auth.state.user.id)
const storageKey = `company-hrm.provisioning.${requesterId}.${props.employee.id}`
const email = ref(props.employee.personalEmail ?? '')
const lookupId = ref('')
const attempt = ref(null)
const result = ref(null)
const busy = ref(false)
const operation = ref('')
const checkedAt = ref('')
const error = useLocalizedMessage()
const note = ref('')
let timer
let disposed = false
let polls = 0
let stopAccountResults
function persist() {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(attempt.value))
  } catch {
    note.value = copy.storageUnavailable
  }
}
function accept(data, submitted = false) {
  if (disposed) {
    // The POST can finish after navigation. Keep its fallback tracking alive in the layout.
    if (submitted && attempt.value && String(auth.state.user?.id ?? '') === requesterId) {
      attempt.value.requestId = data.requestId
      persist()
      trackAccountRequest(props.employee.id, data)
    }
    return
  }
  if (
    result.value?.requestId === data.requestId &&
    result.value.provisioningStatus !== 'PENDING' &&
    data.provisioningStatus === 'PENDING'
  )
    return
  clearTimeout(timer)
  result.value = data
  lookupId.value = data.requestId
  if (submitted && attempt.value) {
    attempt.value.requestId = data.requestId
    persist()
  }
  emit('updated', data.accountStatus)
  const owned = submitted || attempt.value?.requestId === data.requestId
  if (owned) trackAccountRequest(props.employee.id, data)
  if (!owned && data.provisioningStatus === 'PENDING' && polls < provisioningLookup.maxAttempts) {
    timer = setTimeout(() => {
      polls++
      check(true)
    }, provisioningLookup.intervalMs)
  } else if (!owned && data.provisioningStatus === 'PENDING') {
    note.value = copy.stillPending
  }
}
async function send() {
  if (
    !auth.hasRole(accountCreationRoles) ||
    busy.value ||
    result.value?.provisioningStatus === 'PENDING'
  )
    return
  clearTimeout(timer)
  error.value = ''
  note.value = ''
  busy.value = true
  operation.value = 'send'
  polls = 0
  try {
    if (!attempt.value) {
      attempt.value = {
        key: crypto.randomUUID(),
        email: email.value.trim().toLowerCase(),
        requestId: null,
      }
      persist()
    }
    accept(await hrm.provision(props.employee.id, attempt.value.email, attempt.value.key), true)
  } catch (cause) {
    error.value = () => serviceError(cause)
    if ([400, 403, 404, 409].includes(cause.status)) {
      attempt.value = null
      sessionStorage.removeItem(storageKey)
    } else note.value = copy.uncertainResult
  } finally {
    busy.value = false
    operation.value = ''
  }
}
async function check(automatic = false) {
  if (busy.value || disposed) return
  clearTimeout(timer)
  if (!automatic) {
    polls = 0
    note.value = ''
    result.value = null
    checkedAt.value = ''
  }
  if (!uuidPattern.test(lookupId.value.trim())) {
    error.value = () => copy.invalidRequestId
    return
  }
  error.value = ''
  busy.value = true
  operation.value = 'lookup'
  try {
    accept(await hrm.request(props.employee.id, lookupId.value.trim()))
    checkedAt.value = new Date()
  } catch (cause) {
    error.value = () => (cause.status === 404 ? copy.requestNotFound : serviceError(cause))
  } finally {
    busy.value = false
    operation.value = ''
  }
}
function newRequest() {
  clearTimeout(timer)
  attempt.value = null
  result.value = null
  lookupId.value = ''
  error.value = ''
  note.value = ''
  checkedAt.value = ''
  sessionStorage.removeItem(storageKey)
}
onMounted(() => {
  stopAccountResults = subscribeAccountResults((data) => {
    if (data.employeeId !== props.employee.id) return
    emit('updated', data.accountStatus)
    if (data.requestId === lookupId.value || data.requestId === attempt.value?.requestId)
      accept(data)
  })
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey))
    if (saved && uuidPattern.test(saved.key) && typeof saved.email === 'string') {
      attempt.value = saved
      email.value = saved.email
      if (saved.requestId && uuidPattern.test(saved.requestId)) {
        lookupId.value = saved.requestId
        check()
      } else note.value = copy.retryPending
    }
  } catch {
    sessionStorage.removeItem(storageKey)
  }
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
  stopAccountResults?.()
})
</script>
<template>
  <section class="panel create-form provisioning-panel" :aria-busy="busy">
    <p class="eyebrow">{{ copy.eyebrow }}</p>
    <h2>{{ copy.title }}</h2>
    <p class="muted small">{{ copy.description }}</p>
    <p v-if="note" class="info-strip" role="status">{{ note }}</p>
    <p v-if="!auth.hasRole(accountCreationRoles)" class="muted small">{{ copy.adminOnly }}</p>
    <form v-if="auth.hasRole(accountCreationRoles) && !result" @submit.prevent="send">
      <fieldset class="form-fields" :disabled="busy">
        <label for="provision-email">{{ copy.emailLabel }}</label
        ><input
          id="provision-email"
          v-model="email"
          type="email"
          required
          :maxlength="fieldLimits.email"
          :readonly="!!attempt"
        />
        <button
          type="submit"
          class="button button-primary full-width"
          :disabled="employee.accountStatus !== 'NOT_CREATED' && !attempt"
        >
          {{ operation === 'send' ? common.submitting : attempt ? copy.retry : copy.submit }}
        </button>
      </fieldset>
    </form>
    <form class="request-lookup" @submit.prevent="check()">
      <fieldset class="form-fields" :disabled="busy">
        <label for="request-id">{{ copy.lookupLabel }}</label
        ><input
          id="request-id"
          v-model="lookupId"
          required
          :placeholder="copy.lookupPlaceholder"
          :readonly="result?.provisioningStatus === 'PENDING'"
        />
        <button class="button button-secondary full-width" type="submit">
          {{ operation === 'lookup' ? common.checking : copy.lookup }}
        </button>
      </fieldset>
    </form>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
    <p v-if="operation === 'lookup'" class="info-strip" role="status">{{ copy.lookupLoading }}</p>
    <div v-if="result" class="request-result" role="status" aria-live="polite" aria-atomic="true">
      <p v-if="checkedAt" class="small muted">
        {{ copy.checkedAt({ checkedAt: checkedAt.toLocaleTimeString(getDisplayLocale()) }) }}
      </p>
      <strong>{{ requestStatuses[result.provisioningStatus] || result.provisioningStatus }}</strong>
      <p class="small muted">{{ accountStatuses[result.accountStatus] || result.accountStatus }}</p>
      <p
        v-if="result.provisioningStatus === 'PENDING' && attempt?.requestId === result.requestId"
        class="small"
      >
        {{ copy.pendingNotice }}
      </p>
      <p v-else-if="result.provisioningStatus === 'PENDING'" class="small">
        {{ copy.pollingNotice }}
      </p>
      <p
        v-if="
          result.provisioningStatus === 'SUCCEEDED' && result.accountStatus === 'PENDING_ACTIVATION'
        "
        class="small"
      >
        {{ copy.successNotice }}
      </p>
      <p v-if="result.errorCode" class="field-error">
        {{ failures[result.errorCode] || result.errorCode }}
      </p>
      <p class="small">
        {{ copy.requestId }} <code>{{ result.requestId }}</code>
      </p>
      <button
        v-if="auth.hasRole(accountCreationRoles) && result.provisioningStatus === 'FAILED'"
        class="button button-secondary"
        :disabled="busy"
        @click="newRequest"
      >
        {{ copy.newRequest }}
      </button>
    </div>
    <p class="field-help">{{ copy.lookupHint }}</p>
    <ActivationInvitationPanel :key="employee.id" :employee="employee" />
  </section>
</template>
