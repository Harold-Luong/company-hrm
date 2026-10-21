<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { leaveRequests as copy } from '@/content/leave.js'
import { common } from '@/content/common.js'

import { emptyPage } from '@/api/pagination.js'
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import { leaveTypes, leaveStatuses, leavePeriods, leaveReviewRoles } from '@/leave/constants.js'
import { submission } from '@/leave/helpers.js'
import { today, dateLabel } from '@/shared/date.js'
import { leaveError } from '@/leave/errors.js'
import { hrm } from '@/hrm/api.js'
import { fullName } from '@/hrm/utils.js'
import PageControls from '@/components/PageControls.vue'

const props = defineProps({ inbox: Boolean })
const router = useRouter()
const filters = ref('')
const result = ref(emptyPage())
const names = ref({})
const balance = ref(null)
const busy = ref(false)
const sending = ref(false)
const error = useLocalizedMessage()
const formError = useLocalizedMessage()
const estimate = ref(null)
const estimating = ref(false)
const estimateError = useLocalizedMessage()
let estimateGeneration = 0
async function previewLeave() {
    const current = ++estimateGeneration
    estimate.value = null
    estimateError.value = ''
    estimating.value = true
    try {
        const result = await leave.estimate(form.startDate, form.endDate, form.period)
        if (current === estimateGeneration) estimate.value = result
    } catch (cause) {
        if (current === estimateGeneration) estimateError.value = () => leaveError(cause)
    } finally {
        if (current === estimateGeneration) estimating.value = false
    }
}

const form = reactive({
    leaveType: 'ANNUAL',
    startDate: today(),
    endDate: today(),
    period: 'FULL_DAY',
    reason: '',
})
watch(
    () => [form.startDate, form.endDate, form.period],
    () => {
        estimateGeneration++
        estimate.value = null
        estimating.value = false
        estimateError.value = ''
    },
)
onBeforeUnmount(() => estimateGeneration++)
let generation = 0
onBeforeUnmount(() => generation++)
async function load(page = 0) {
    const current = ++generation
    busy.value = true
    error.value = ''
    result.value = emptyPage(page)
    try {
        const data = await leave.list(props.inbox, filters.value, page)
        if (current !== generation) return
        result.value = data
        if (!props.inbox) {
            try {
                const annualBalance = await leave.balance(Number(today().slice(0, 4)))
                if (current !== generation) return
                balance.value = annualBalance
            } catch {
                if (current !== generation) return
                balance.value = null
            }
        }
        if (props.inbox) {
            const ids = [...new Set(data.content.map((row) => row.employeeId))]
            const employees = await Promise.allSettled(ids.map((id) => hrm.get('employees', id)))
            if (current !== generation) return
            names.value = Object.fromEntries(
                employees.flatMap((entry, i) =>
                    entry.status === 'fulfilled'
                        ? [[ids[i], `${fullName(entry.value)} · ${entry.value.employeeCode}`]]
                        : [],
                ),
            )
        }
    } catch (cause) {
        if (current === generation) error.value = () => leaveError(cause)
    } finally {
        if (current === generation) busy.value = false
    }
}
async function submit() {
    if (sending.value) return
    formError.value = ''
    let body
    try {
        body = submission(form)
    } catch (cause) {
        formError.value = cause.message
        return
    }
    sending.value = true
    try {
        const created = await leave.submit(body)
        await router.push(`/leave/requests/${created.id}`)
    } catch (cause) {
        formError.value = () => leaveError(cause)
    } finally {
        sending.value = false
    }
}
watch(
    () => props.inbox,
    () => {
        filters.value = props.inbox ? 'PENDING' : ''
        names.value = {}
        balance.value = null
        formError.value = ''
        load()
    },
    { immediate: true },
)
watch(
    () => [form.period, form.startDate],
    () => {
        if (form.period !== 'FULL_DAY') form.endDate = form.startDate
    },
)
</script>

<template>
    <div class="leave-page">
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ inbox ? copy.reviewTitle : copy.title }}</h1>
                <p class="muted">
                    {{ inbox ? copy.reviewDescription : copy.description }}
                </p>
            </div>
            <RouterLink v-if="inbox" to="/leave" class="button button-secondary">{{
                copy.mine
                }}</RouterLink>
            <RouterLink v-else-if="auth.hasRole(leaveReviewRoles)" to="/leave/inbox" class="button button-secondary">
                {{ copy.reviewTitle }}</RouterLink>
        </div>
        <form v-if="!inbox" class="panel create-form leave-section" @submit.prevent="submit">
            <h2>{{ copy.create }}</h2>
            <p v-if="balance" class="muted">
                {{
                    copy.balanceSummary({
                        year: balance.year,
                        remainingDays: balance.remainingDays,
                        entitledDays: balance.entitledDays,
                        usedDays: balance.usedDays,
                })
                }}
            </p>
            <p class="muted">{{ copy.balanceHint }}</p>
            <fieldset class="form-fields" :disabled="sending">
                <div class="leave-form-grid">
                    <div class="field">
                        <label for="leave-type">{{ copy.type }}</label><select
id="leave-type" v-model="form.leaveType"
                            required>
                            <option v-for="(label, value) in leaveTypes" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field">
                        <label for="leave-period">{{ copy.duration }}</label><select
id="leave-period"
                            v-model="form.period" required>
                            <option v-for="(label, value) in leavePeriods" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field">
                        <label for="leave-start">{{ common.fromDate }}</label><input
id="leave-start"
                            v-model="form.startDate" type="date" :min="today()" required />
                    </div>
                    <div class="field">
                        <label for="leave-end">{{ common.toDate }}</label><input
id="leave-end" v-model="form.endDate"
                            type="date" :min="form.startDate || today()" :disabled="form.period !== 'FULL_DAY'"
                            required />
                    </div>
                </div>
                <div class="field">
                    <label for="leave-reason">{{ copy.reason }}</label><textarea
id="leave-reason" v-model="form.reason"
                        rows="3" :maxlength="fieldLimits.leaveReason" required
                        :placeholder="copy.reasonPlaceholder"></textarea>
                </div>
                <p v-if="formError" class="alert alert-error" role="alert">{{ formError }}</p>
                <p class="muted">{{ copy.calculationHint }}</p>
                <button
class="button button-secondary" type="button" :disabled="estimating || sending"
                    @click="previewLeave">
                    {{ estimating ? copy.estimating : copy.estimate }}
                </button>
                <p v-if="estimate" role="status">
                    {{ copy.estimatedDays }} <strong>{{ estimate.totalDays }}</strong>{{ copy.estimateHint }}
                </p>
                <p v-if="estimateError" role="alert">{{ estimateError }}</p>
                <button class="button button-primary" type="submit">
                    {{ sending ? common.submitting : copy.submit }}
                </button>
            </fieldset>
        </form>
        <section class="panel directory-panel" :aria-busy="busy">
            <form class="list-toolbar" @submit.prevent="load()">
                <h2>{{ inbox ? copy.inboxTitle : copy.mineTitle }}</h2>
                <div class="button-row">
                    <label for="leave-status">{{ common.status }}</label>
                    <select id="leave-status" v-model="filters" :disabled="busy" @change="load()">
                        <option value="">{{ common.all }}</option>
                        <option v-if="inbox" value="ADJUSTMENT_PENDING">{{ copy.adjustments }}</option>
                        <option v-for="(label, value) in leaveStatuses" :key="value" :value="value">
                            {{ label }}
                        </option>
                    </select>
                    <button class="button button-secondary" type="submit" :disabled="busy">
                        {{ common.reload }}
                    </button>
                </div>
            </form>
            <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
            <p v-if="busy" class="table-message" role="status">{{ copy.loading }}</p>
            <p v-else-if="!error && !result.content.length" class="table-message" role="status">
                {{ copy.empty }}
            </p>
            <div
v-if="!busy && !error && result.content.length" class="table-scroll" tabindex="0"
                :aria-label="copy.tableLabel">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th v-if="inbox">{{ common.employee }}</th>
                            <th>{{ copy.type }}</th>
                            <th>{{ copy.period }}</th>
                            <th>{{ common.status }}</th>
                            <th>{{ common.actions }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in result.content" :key="row.id">
                            <td v-if="inbox">{{ names[row.employeeId] || row.employeeId }}</td>
                            <td>{{ leaveTypes[row.leaveType] }}</td>
                            <td>
                                {{ dateLabel(row.startDate) }} – {{ dateLabel(row.endDate) }} ·
                                {{ leavePeriods[row.period || 'FULL_DAY'] }}
                            </td>
                            <td>
                                <span class="leave-badge" :class="`leave-${row.status}`">{{
                                    leaveStatuses[row.status]
                                    }}</span>
                                <span v-if="row.adjustmentPending" class="cell-secondary">{{
                                    copy.adjustmentPending
                                    }}</span>
                            </td>
                            <td>
                                <RouterLink :to="`/leave/requests/${row.id}`" class="record-link">{{
                                    copy.viewRequest
                                    }}</RouterLink>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <PageControls
:page="result.page" :pages="result.totalPages" :total="result.totalElements" :busy="busy"
                @change="load($event)" />
        </section>
    </div>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
