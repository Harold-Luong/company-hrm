<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { activationInvitation as copy } from '@/content/hrm.js'

import { computed, onBeforeUnmount, ref } from 'vue'
import { hrm } from '@/hrm/api.js'
import { serviceError } from '@/api/errors.js'
import { formatDate } from '@/shared/date.js'

const props = defineProps({ employee: { type: Object, required: true } })
const invitation = ref(null)
const operation = ref('')
const error = useLocalizedMessage()
const message = useLocalizedMessage()
const remaining = ref(0)
const canResend = computed(() => props.employee.accountStatus === 'PENDING_ACTIVATION')
let cooldownTimer
let disposed = false
const statuses = {
    get PENDING() {
        return copy.queued
    },
    get SENT() {
        return copy.accepted
    },
    get FAILED() {
        return copy.failed
    },
    get CANCELLED() {
        return copy.cancelled
    },
}
function cooldown(seconds) {
    clearInterval(cooldownTimer)
    const until = Date.now() + seconds * 1000
    remaining.value = seconds
    cooldownTimer = setInterval(() => {
        remaining.value = Math.max(0, Math.ceil((until - Date.now()) / 1000))
        if (!remaining.value) clearInterval(cooldownTimer)
    }, 1000)
}
function failure(cause, sending) {
    if (cause.status === 429) return copy.cooldown
    if (cause.status === 404) return sending ? copy.accountNotFound : copy.invitationNotFound
    if (cause.status === 409) return copy.noLongerPending
    if (cause.status === 503)
        return cause.message === 'Account activation is disabled' ? copy.disabled : serviceError(cause)
    return serviceError(cause)
}
async function request(sending = false) {
    if (operation.value || disposed || (sending && (!canResend.value || remaining.value))) return
    operation.value = sending ? 'send' : 'check'
    error.value = ''
    message.value = ''
    try {
        const data = await (sending
            ? hrm.resendActivationInvitation(props.employee.id)
            : hrm.activationInvitation(props.employee.id))
        if (disposed) return
        invitation.value = data
        if (sending) {
            cooldown(60)
            message.value = () => copy.queuedNotice
        }
    } catch (cause) {
        if (disposed) return
        invitation.value = null
        if (cause.status === 429) {
            const seconds = Number(cause.retryAfter)
            cooldown(Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 60)
        }
        error.value = () => failure(cause, sending)
        if (
            sending &&
            (cause.status === 0 || cause.status >= 500) &&
            cause.message !== 'Account activation is disabled'
        ) {
            message.value = () => copy.uncertainResult
        }
    } finally {
        operation.value = ''
    }
}
onBeforeUnmount(() => {
    disposed = true
    clearInterval(cooldownTimer)
})
</script>

<template>
    <section class="activation-invitation request-lookup" :aria-busy="!!operation">
        <h3>{{ copy.title }}</h3>
        <p class="small muted">{{ copy.description }}</p>
        <button
class="button button-secondary full-width" type="button"
            :disabled="!!operation || !canResend || remaining > 0" @click="request(true)">
            {{
                operation === 'send'
                    ? copy.queueing
                    : remaining > 0
                        ? copy.resendCountdown({ remaining: remaining })
                        : copy.resend
            }}
        </button>
        <p v-if="!canResend" class="field-help">{{ copy.pendingOnly }}</p>
        <button class="button button-secondary full-width" type="button" :disabled="!!operation" @click="request()">
            {{ operation === 'check' ? copy.checking : copy.check }}
        </button>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <p v-if="message" class="info-strip" role="status">{{ message }}</p>
        <div v-if="invitation" class="small" role="status" aria-live="polite">
            <strong>{{ statuses[invitation.deliveryStatus] || invitation.deliveryStatus }}</strong>
            <p v-if="invitation.deliveryStatus === 'PENDING'">{{ copy.checkHint }}</p>
            <p v-if="invitation.deliveryStatus === 'SENT'">{{ copy.acceptedHint }}</p>
            <p v-if="invitation.deliveryStatus === 'FAILED'">{{ copy.failureHint }}</p>
            <p>
                {{ copy.invitationId }} <code>{{ invitation.invitationId }}</code>
            </p>
            <p v-if="invitation.expiresAt">
                {{ copy.expiresAt({ date: formatDate(invitation.expiresAt) }) }}
            </p>
            <p v-if="invitation.sentAt">{{ copy.acceptedAt({ date: formatDate(invitation.sentAt) }) }}</p>
            <p v-if="invitation.attempts != null">
                {{ copy.attempts({ attempts: invitation.attempts }) }}
            </p>
            <p v-if="invitation.errorCode" class="field-error">
                {{ copy.errorCode({ errorCode: invitation.errorCode }) }}
            </p>
        </div>
    </section>
</template>

<style scoped>
.activation-invitation {
    display: grid;
    gap: 12px;
    overflow-wrap: anywhere;
}
</style>
