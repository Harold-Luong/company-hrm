<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { refreshIntervals } from '@/config/runtime.js'

import { fieldLimits } from '@/config/validation.js'

import { calendarEventForm as copy } from '@/content/calendar.js'
import { common } from '@/content/common.js'

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ApiError } from '@/api/errors.js'
import { auth } from '@/auth/session.js'
import { calendarManagement } from '@/calendar/api.js'
import {
    calendarManagementRoles,
    eventTypes,
    holidayKinds,
    eventStatuses,
} from '@/calendar/constants.js'
import { displayEventRange } from '@/calendar/utils.js'
import {
    newEventForm,
    eventToForm,
    eventPayload,
    eventActions,
    calendarManagementError,
} from '@/calendar/management.js'

const props = defineProps({ id: { type: String, default: '' } })
const router = useRouter()
const form = reactive(newEventForm())
const baseline = ref(JSON.stringify(form))
const event = ref(null)
const loading = ref(!!props.id)
const loaded = ref(!props.id)
const busy = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
const blocked = ref(false)
const uncertain = ref(false)
const now = ref(Date.now())
const dialog = ref(null)
const action = ref('')
const cancelReason = ref('')
const dialogError = useLocalizedMessage()
const dirty = computed(() => JSON.stringify(form) !== baseline.value)
const allowed = computed(() => eventActions(event.value, now.value))
const canManage = computed(() => auth.hasRole(calendarManagementRoles))
const locked = computed(
    () => busy.value || loading.value || blocked.value || uncertain.value || !canManage.value,
)
const published = computed(() => event.value?.status === 'PUBLISHED')
const actionLabel = computed(
    () =>
        ({
            get publish() {
                return copy.publish
            },
            get cancel() {
                return copy.cancel
            },
            get remove() {
                return copy.deleteDraft
            },
        })[action.value],
)
let timer

watch(
    () => form.type,
    (type) => {
        if (type === 'HOLIDAY') {
            form.allDay = true
            if (!form.holidayKind) form.holidayKind = 'PUBLIC_HOLIDAY'
        } else form.holidayKind = ''
    },
    { flush: 'sync' },
)

function accept(data) {
    event.value = data
    Object.assign(form, eventToForm(data))
    baseline.value = JSON.stringify(form)
    blocked.value = false
    uncertain.value = false
    now.value = Date.now()
}
async function load() {
    if (busy.value || (loading.value && loaded.value)) return
    if (dirty.value && !window.confirm(copy.reloadConfirmation)) return
    loading.value = true
    error.value = ''
    success.value = ''
    try {
        accept(await calendarManagement.get(props.id))
        loaded.value = true
    } catch (cause) {
        error.value = () => calendarManagementError(cause)
        blocked.value = true
    } finally {
        loading.value = false
    }
}
function fail(cause, mutation = false) {
    error.value = () => calendarManagementError(cause)
    if (cause instanceof ApiError) {
        if ([401, 403, 404, 409, 412, 428].includes(cause.status)) blocked.value = true
        if (mutation && (cause.status === 0 || cause.status >= 500)) {
            uncertain.value = true
            error.value = () => copy.uncertainResult
        }
    }
}
async function save() {
    if (locked.value || !allowed.value.edit) return
    error.value = ''
    success.value = ''
    let payload
    try {
        payload = eventPayload(form)
        if (published.value && !payload.reason) throw new Error(copy.changeReasonRequired)
    } catch (cause) {
        fail(cause)
        return
    }
    busy.value = true
    try {
        const data = props.id
            ? await calendarManagement.update(props.id, event.value.version, payload)
            : await calendarManagement.create(payload)
        accept(data)
        if (!props.id) await router.replace(`/calendar-events/${data.id}`)
        else success.value = () => copy.saved
    } catch (cause) {
        fail(cause, true)
    } finally {
        busy.value = false
    }
}
async function openAction(value) {
    now.value = Date.now()
    if (locked.value || dirty.value || !allowed.value[value]) return
    action.value = value
    dialogError.value = ''
    cancelReason.value = ''
    error.value = ''
    await nextTick()
    dialog.value.showModal()
}
function closeDialog() {
    if (busy.value) return
    dialog.value.close()
    action.value = ''
}
async function performAction() {
    now.value = Date.now()
    if (locked.value || dirty.value) return
    if (!allowed.value[action.value]) {
        dialogError.value = () => copy.invalidTransition
        return
    }
    const selectedAction = action.value
    if (selectedAction === 'cancel' && !cancelReason.value.trim()) {
        dialogError.value = () => copy.cancellationReasonRequired
        return
    }
    busy.value = true
    error.value = ''
    success.value = ''
    try {
        const data = await calendarManagement[selectedAction](
            props.id,
            event.value.version,
            cancelReason.value.trim(),
        )
        dialog.value.close()
        action.value = ''
        if (selectedAction === 'remove') {
            baseline.value = JSON.stringify(form)
            // Permit the successful navigation through the unsaved-change guard.
            busy.value = false
            await router.replace('/calendar-events')
        } else {
            accept(data)
            success.value = () => (selectedAction === 'publish' ? copy.published : copy.cancelled)
        }
    } catch (cause) {
        dialog.value.close()
        action.value = ''
        fail(cause, true)
    } finally {
        busy.value = false
    }
}
function beforeUnload(event) {
    if (dirty.value || busy.value) {
        event.preventDefault()
        event.returnValue = ''
    }
}
onBeforeRouteLeave(() => {
    // Creation has succeeded once the server event is accepted.
    if (busy.value && !(event.value && !props.id && !dirty.value)) return false
    return !dirty.value || window.confirm(copy.leaveConfirmation)
})
onMounted(() => {
    if (props.id) load()
    timer = setInterval(() => {
        now.value = Date.now()
    }, refreshIntervals.eventState)
    window.addEventListener('beforeunload', beforeUnload)
})
onUnmounted(() => {
    clearInterval(timer)
    window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
    <div class="calendar-management">
        <RouterLink class="back-link" to="/calendar-events">{{ copy.back }}</RouterLink>
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ id ? copy.detailTitle : copy.createTitle }}</h1>
                <p class="muted">
                    {{ id ? copy.detailDescription : copy.createDescription }}
                </p>
            </div>
            <span v-if="event" class="event-status" :class="`status-${event.status}`">{{
                eventStatuses[event.status]
                }}</span>
        </div>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
        <p v-if="loading" class="panel create-form" role="status">{{ copy.loading }}</p>
        <div v-if="(blocked || uncertain || !loaded) && !loading" class="calendar-recovery">
            <button v-if="id" class="button button-secondary" :disabled="busy" @click="load">
                {{ copy.reload }}
            </button>
            <RouterLink v-else to="/calendar-events" class="button button-secondary">{{ copy.checkList }}
            </RouterLink>
        </div>
        <div v-if="loaded && !loading" class="calendar-editor-layout">
            <form class="panel create-form" :aria-busy="busy" @submit.prevent="save">
                <h2>{{ copy.formTitle }}</h2>
                <p v-if="event && !allowed.edit" class="field-help">
                    {{ event.status === 'CANCELLED' ? copy.cancelledReadOnly : copy.startedReadOnly }}
                </p>
                <fieldset class="form-fields form-grid" :disabled="locked || !allowed.edit">
                    <div class="field span-all">
                        <label for="event-title">{{ copy.eventTitle }}</label><input id="event-title"
                            v-model="form.title" required :maxlength="fieldLimits.eventTitle" pattern=".*\S.*" />
                    </div>
                    <div class="field">
                        <label for="event-type">{{ copy.eventType }}</label><select id="event-type" v-model="form.type"
                            :disabled="published">
                            <option v-for="(label, value) in eventTypes" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div v-if="form.type === 'HOLIDAY'" class="field">
                        <label for="event-holiday-kind">{{ copy.holidayType }}</label><select id="event-holiday-kind"
                            v-model="form.holidayKind" required :disabled="published">
                            <option v-for="(label, value) in holidayKinds" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field span-all">
                        <label class="calendar-checkbox" for="event-all-day"><input id="event-all-day"
                                v-model="form.allDay" type="checkbox"
                                :disabled="published || form.type === 'HOLIDAY'" />{{ copy.allDay }}</label>
                        <p class="field-help">{{ copy.timezoneHint }}</p>
                    </div>
                    <template v-if="form.allDay">
                        <div class="field">
                            <label for="event-start-date">{{ copy.startsOn }}</label><input id="event-start-date"
                                v-model="form.startDate" type="date" required />
                        </div>
                        <div class="field">
                            <label for="event-end-date">{{ copy.endsOn }}</label><input id="event-end-date"
                                v-model="form.endDate" type="date" :min="form.startDate" required />
                            <p class="field-help">{{ copy.inclusiveEnd }}</p>
                        </div>
                    </template>
                    <template v-else>
                        <div class="field">
                            <label for="event-start-time">{{ copy.startsAt }}</label><input id="event-start-time"
                                v-model="form.startTime" type="datetime-local" step="0.001" required />
                        </div>
                        <div class="field">
                            <label for="event-end-time">{{ copy.endsAt }}</label><input id="event-end-time"
                                v-model="form.endTime" type="datetime-local" step="0.001" :min="form.startTime"
                                required />
                        </div>
                    </template>
                    <div class="field span-all">
                        <label for="event-location">{{ copy.location }}</label><input id="event-location"
                            v-model="form.location" :maxlength="fieldLimits.eventLocation" />
                    </div>
                    <div class="field span-all">
                        <label for="event-description">{{ copy.description }}</label><textarea id="event-description"
                            v-model="form.description" rows="5" :maxlength="fieldLimits.eventDescription"></textarea>
                    </div>
                    <div v-if="published && allowed.edit" class="field span-all">
                        <label for="event-reason">{{ copy.changeReason }}</label><textarea id="event-reason"
                            v-model="form.reason" required :maxlength="fieldLimits.eventReason" rows="3"></textarea>
                        <p class="field-help">{{ copy.changeReasonHint }}</p>
                    </div>
                    <div v-if="allowed.edit" class="form-footer span-all">
                        <span class="field-help">{{ dirty ? copy.unsaved : copy.synchronized }}</span><button
                            class="button button-primary" type="submit" :disabled="!!id && !dirty">
                            {{ busy ? copy.processing : id ? common.saveChanges : copy.saveDraft }}
                        </button>
                    </div>
                </fieldset>
            </form>
            <aside class="calendar-editor-aside">
                <section class="panel create-form">
                    <h2>{{ copy.visibilityTitle }}</h2>
                    <p class="muted">
                        {{
                            !event || event.status === 'DRAFT'
                                ? copy.draftHint
                                : event.status === 'PUBLISHED'
                                    ? copy.publishedHint
                        : copy.cancelledHint
                        }}
                    </p>
                    <p v-if="event" class="small">{{ displayEventRange(event) }}</p>
                    <p class="field-help">{{ copy.audienceHint }}</p>
                    <template v-if="event">
                        <p v-if="dirty" class="field-help">{{ copy.saveBeforeAction }}</p>
                        <div class="calendar-lifecycle-actions">
                            <button v-if="event.status === 'DRAFT'" class="button button-primary"
                                :disabled="locked || dirty || !allowed.publish" @click="openAction('publish')">
                                {{ copy.publishAction }}
                            </button>
                            <p v-if="event.status === 'DRAFT' && !allowed.publish" class="field-help">
                                {{ copy.pastEventHint }}
                            </p>
                            <button v-if="allowed.cancel" class="button button-danger-outline"
                                :disabled="locked || dirty" @click="openAction('cancel')">
                                {{ copy.cancel }}
                            </button>
                            <button v-if="allowed.remove" class="button button-danger-outline"
                                :disabled="locked || dirty" @click="openAction('remove')">
                                {{ copy.deleteDraft }}
                            </button>
                            <button class="button button-secondary" :disabled="busy" @click="load">
                                {{ copy.reloadData }}
                            </button>
                        </div>
                    </template>
                </section>
            </aside>
        </div>
        <dialog ref="dialog" class="calendar-action-dialog" aria-labelledby="calendar-action-title"
            @cancel="busy ? $event.preventDefault() : (action = '')">
            <form @submit.prevent="performAction">
                <h2 id="calendar-action-title">{{ actionLabel }}</h2>
                <p v-if="dialogError" class="alert alert-error" role="alert">{{ dialogError }}</p>
                <p>
                    <strong>{{ event?.title }}</strong>
                </p>
                <p v-if="event" class="muted">{{ displayEventRange(event) }}</p>
                <p v-if="action === 'publish'">{{ copy.publishConfirmation }}</p>
                <p v-else-if="action === 'remove'">{{ copy.deleteConfirmation }}</p>
                <p v-else>{{ copy.cancelConfirmation }}</p>
                <fieldset class="form-fields" :disabled="busy">
                    <div v-if="action === 'cancel'" class="field">
                        <label for="cancel-reason">{{ copy.cancellationReason }}</label><textarea id="cancel-reason"
                            v-model="cancelReason" required :maxlength="fieldLimits.eventReason" rows="3"></textarea>
                    </div>
                    <div class="form-footer">
                        <button type="button" class="button button-secondary" @click="closeDialog">
                            {{ common.back }}</button><button type="submit" class="button"
                            :class="action === 'publish' ? 'button-primary' : 'button-danger'">
                            {{ busy ? copy.processing : actionLabel }}
                        </button>
                    </div>
                </fieldset>
            </form>
        </dialog>
    </div>
</template>

<style scoped src="@/assets/styles/calendar-management.css"></style>
