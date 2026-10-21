<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'
import { fieldLimits } from '@/config/validation.js'
import { leaveRequestDetail as copy } from '@/content/leave.js'
import { common } from '@/content/common.js'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import { hrm } from '@/hrm/api.js'
import { fullName } from '@/hrm/utils.js'
import LeaveAdjustmentPanel from '@/components/LeaveAdjustmentPanel.vue'
import {
    leaveTypes,
    leaveStatuses,
    leavePeriods,
    leaveReviewRoles,
    historyActions,
} from '@/leave/constants.js'
import { dateLabel, dateTimeLabel as timeLabel } from '@/shared/date.js'
import { leaveError } from '@/leave/errors.js'
const props = defineProps({ id: { type: String, required: true } })
const request = ref(null)
const history = ref([])
const historyError = useLocalizedMessage()
const employeeName = ref('')
const note = ref('')
const busy = ref(false)
const saving = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
const stale = ref(false)
const isReviewer = computed(() => auth.hasRole(leaveReviewRoles))
const own = computed(
    () =>
        request.value &&
        (request.value.employeeId === auth.state.user?.employeeId ||
            request.value.requesterUserId === String(auth.state.user?.id)),
)
let generation = 0
onBeforeUnmount(() => generation++)
async function load() {
    const current = ++generation
    busy.value = true
    error.value = ''
    success.value = ''
    request.value = null
    history.value = []
    historyError.value = ''
    employeeName.value = ''
    try {
        const data = await leave.get(props.id)
        if (current !== generation) return
        request.value = data
        stale.value = false
        const results = await Promise.allSettled([
            leave.history(props.id),
            isReviewer.value ? hrm.get('employees', data.employeeId) : Promise.resolve(null),
        ])
        if (current !== generation) return
        if (results[0].status === 'fulfilled') history.value = results[0].value
        else historyError.value = () => copy.historyFailed
        if (results[1].status === 'fulfilled' && results[1].value)
            employeeName.value = fullName(results[1].value)
    } catch (cause) {
        if (current === generation) error.value = () => leaveError(cause)
    } finally {
        if (current === generation) busy.value = false
    }
}
async function decide(action) {
    if (saving.value || busy.value || stale.value) return
    if (action === 'reject' && !note.value.trim()) {
        error.value = () => copy.rejectionRequired
        return
    }
    saving.value = true
    const current = generation
    const requestId = props.id
    error.value = ''
    success.value = ''
    try {
        const updated = await leave.decide(requestId, action, request.value.version, note.value.trim())
        if (current !== generation) return
        request.value = updated
        success.value = () => (action === 'cancel' ? copy.withdrawn : copy.processed)
        note.value = ''
        try {
            const entries = await leave.history(requestId)
            if (current !== generation) return
            history.value = entries
            historyError.value = ''
        } catch {
            if (current !== generation) return
            historyError.value = () => copy.savedHistoryFailed
        }
    } catch (cause) {
        if (current !== generation) return
        error.value = () => leaveError(cause)
        // A timeout may mean the write committed; require fresh data before another action.
        if ([0, 409, 412, 428].includes(cause.status) || cause.status >= 500) stale.value = true
    } finally {
        saving.value = false
    }
}
watch(
    () => props.id,
    () => {
        note.value = ''
        load()
    },
    { immediate: true },
)
</script>

<template>
    <div class="leave-page">
        <RouterLink :to="isReviewer ? '/leave/inbox' : '/leave'" class="back-link">{{
            copy.back
        }}</RouterLink>
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ copy.title }}</h1>
            </div>
            <button class="button button-secondary" :disabled="busy || saving" @click="load">
                {{ common.reload }}
            </button>
        </div>
        <p v-if="busy" role="status">{{ copy.loading }}</p>
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <p v-if="success" class="alert alert-success" role="status">{{ success }}</p>
        <section v-if="request && !busy" class="panel create-form leave-section">
            <h2>{{ leaveTypes[request.leaveType] }}</h2>
            <span class="leave-badge" :class="`leave-${request.status}`">{{
                leaveStatuses[request.status]
            }}</span>
            <dl class="leave-details">
                <div>
                    <dt>{{ common.employee }}</dt>
                    <dd>{{ employeeName || (own ? auth.state.user?.email : request.employeeId) }}</dd>
                </div>
                <div>
                    <dt>{{ copy.period }}</dt>
                    <dd>
                        {{ dateLabel(request.startDate) }} – {{ dateLabel(request.endDate) }} ({{
                            leavePeriods[request.period || 'FULL_DAY'].toLowerCase()
                        }})
                    </dd>
                </div>
                <div>
                    <dt>{{ copy.reason }}</dt>
                    <dd class="leave-text">{{ request.reason }}</dd>
                </div>
                <div>
                    <dt>{{ copy.submittedAt }}</dt>
                    <dd>{{ timeLabel(request.createdAt) }}</dd>
                </div>
                <div v-if="request.reviewedAt">
                    <dt>{{ copy.reviewedAt }}</dt>
                    <dd>{{ timeLabel(request.reviewedAt) }}</dd>
                </div>
                <div v-if="request.reviewNote">
                    <dt>{{ copy.reviewNote }}</dt>
                    <dd class="leave-text">{{ request.reviewNote }}</dd>
                </div>
            </dl>
            <p class="muted">{{ copy.balanceHint }}</p>
            <template v-if="request.status === 'PENDING'">
                <p v-if="own && isReviewer" class="muted">{{ copy.selfReviewHint }}</p>
                <button
v-if="own" class="button button-secondary" :disabled="saving || stale"
                    @click="decide('cancel')">
                    {{ saving ? copy.processing : common.withdraw }}
                </button>
                <form v-else-if="isReviewer" @submit.prevent="decide('approve')">
                    <fieldset class="form-fields" :disabled="saving || stale">
                        <div class="field">
                            <label for="review-note">{{ copy.reviewNoteLabel }}</label><textarea
id="review-note"
                                v-model="note" rows="3" :maxlength="fieldLimits.leaveReason"></textarea>
                        </div>
                        <div class="button-row">
                            <button type="submit" class="button button-primary">{{ copy.approve }}</button><button
                                type="button" class="button button-secondary" @click="decide('reject')">
                                {{ common.reject }}
                            </button>
                        </div>
                    </fieldset>
                </form>
            </template>
        </section>
        <LeaveAdjustmentPanel
v-if="request && !busy && ['APPROVED', 'CANCELLED'].includes(request.status)"
            :key="request.id" :request="request" :own="!!own" :reviewer="isReviewer" @updated="load" />
        <section v-if="request && !busy" class="panel create-form">
            <h2>{{ copy.historyTitle }}</h2>
            <p v-if="historyError" class="alert alert-error" role="alert">{{ historyError }}</p>
            <ol class="leave-history">
                <li v-for="entry in history" :key="entry.id">
                    <strong>{{ historyActions[entry.action] }}</strong> · {{ timeLabel(entry.occurredAt)
                    }}<span class="cell-secondary">{{ copy.actor({ actorUserId: entry.actorUserId }) }}</span>
                    <p v-if="entry.note" class="leave-text">{{ entry.note }}</p>
                </li>
            </ol>
        </section>
    </div>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
