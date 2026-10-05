<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import {
    leaveTypes,
    leaveStatuses,
    leavePeriods,
    leaveReviewRoles,
    submission,
    today,
    dateLabel,
    leaveError,
} from '@/leave/helpers.js'
import { hrm, fullName } from '@/hrm/api.js'
import PageControls from '@/components/PageControls.vue'

const props = defineProps({ inbox: Boolean })
const router = useRouter()
const filters = ref('')
const result = ref({ content: [], page: 0, totalPages: 0, totalElements: 0 })
const names = ref({})
const balance = ref(null)
const busy = ref(false)
const sending = ref(false)
const error = ref('')
const formError = ref('')
const form = reactive({
    leaveType: 'ANNUAL',
    startDate: today(),
    endDate: today(),
    period: 'FULL_DAY',
    reason: '',
})
let generation = 0
onBeforeUnmount(() => generation++)
async function load(page = 0) {
    const current = ++generation
    busy.value = true
    error.value = ''
    result.value = { content: [], page, totalPages: 0, totalElements: 0 }
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
        if (current === generation) error.value = leaveError(cause)
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
        formError.value = leaveError(cause)
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
                <p class="eyebrow">NGHỈ PHÉP</p>
                <h1>{{ inbox ? 'Duyệt nghỉ phép' : 'Nghỉ phép của tôi' }}</h1>
                <p class="muted">
                    {{
                        inbox
                            ? 'Xem yêu cầu của nhân viên, duyệt hoặc phản hồi lý do từ chối.'
                            : 'Gửi đơn đến HR và theo dõi kết quả xử lý.'
                    }}
                </p>
            </div>
            <RouterLink v-if="inbox" to="/leave" class="button button-secondary">Đơn của tôi</RouterLink>
            <RouterLink v-else-if="auth.hasRole(leaveReviewRoles)" to="/leave/inbox" class="button button-secondary">
                Duyệt nghỉ phép</RouterLink>
        </div>
        <form v-if="!inbox" class="panel create-form leave-section" @submit.prevent="submit">
            <h2>Tạo đơn xin nghỉ</h2>
            <p v-if="balance" class="muted">
                Phép năm {{ balance.year }}: còn {{ balance.remainingDays }}/{{ balance.entitledDays }} ngày
                (đã dùng {{ balance.usedDays }} ngày).
            </p>
            <p class="muted">
                Nghỉ phép năm được trừ vào hạn mức 12 ngày/năm khi duyệt. Nghỉ không lương không trừ phép năm.
            </p>
            <fieldset class="form-fields" :disabled="sending">
                <div class="leave-form-grid">
                    <div class="field">
                        <label for="leave-type">Loại nghỉ</label><select id="leave-type" v-model="form.leaveType"
                            required>
                            <option v-for="(label, value) in leaveTypes" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field">
                        <label for="leave-period">Thời lượng</label><select id="leave-period" v-model="form.period"
                            required>
                            <option v-for="(label, value) in leavePeriods" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field">
                        <label for="leave-start">Từ ngày</label><input id="leave-start" v-model="form.startDate"
                            type="date" :min="today()" required />
                    </div>
                    <div class="field">
                        <label for="leave-end">Đến ngày</label><input id="leave-end" v-model="form.endDate" type="date"
                            :min="form.startDate || today()" :disabled="form.period !== 'FULL_DAY'" required />
                    </div>
                </div>
                <div class="field">
                    <label for="leave-reason">Lý do nghỉ</label><textarea id="leave-reason" v-model="form.reason"
                        rows="3" maxlength="2000" required placeholder="Ví dụ: Việc cá nhân, việc gia đình, khám bệnh…"></textarea>
                </div>
                <p v-if="formError" class="alert alert-error" role="alert">{{ formError }}</p>
                <button class="button button-primary" type="submit">
                    {{ sending ? 'Đang gửi…' : 'Gửi đơn đến HR' }}
                </button>
            </fieldset>
        </form>
        <section class="panel directory-panel" :aria-busy="busy">
            <form class="list-toolbar" @submit.prevent="load()">
                <h2>{{ inbox ? 'Yêu cầu nghỉ phép' : 'Đơn đã gửi' }}</h2>
                <div class="button-row">
                    <label for="leave-status">Trạng thái</label>
                    <select id="leave-status" v-model="filters" :disabled="busy" @change="load()">
                        <option value="">Tất cả</option>
                        <option v-for="(label, value) in leaveStatuses" :key="value" :value="value">
                            {{ label }}
                        </option>
                    </select>
                    <button class="button button-secondary" type="submit" :disabled="busy">Tải lại</button>
                </div>
            </form>
            <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
            <p v-if="busy" class="table-message" role="status">Đang tải đơn nghỉ…</p>
            <p v-else-if="!error && !result.content.length" class="table-message" role="status">
                Chưa có đơn nghỉ phù hợp.
            </p>
            <div v-if="!busy && !error && result.content.length" class="table-scroll" tabindex="0"
                aria-label="Danh sách đơn nghỉ">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th v-if="inbox">Nhân viên</th>
                            <th>Loại nghỉ</th>
                            <th>Thời gian nghỉ</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
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
                            </td>
                            <td>
                                <RouterLink :to="`/leave/requests/${row.id}`" class="record-link">Xem đơn</RouterLink>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <PageControls :page="result.page" :pages="result.totalPages" :total="result.totalElements" :busy="busy"
                @change="load($event)" />
        </section>
    </div>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
