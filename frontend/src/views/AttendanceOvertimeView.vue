<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { attendanceError, attendanceRoles, minutesLabel, today } from '@/attendance/helpers.js'
import { requestStatuses } from '@/attendance/requests.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import PageControls from '@/components/PageControls.vue'
const reviewer = computed(() => auth.hasRole(attendanceRoles))
const inbox = ref(false),
    status = ref(''),
    busy = ref(false),
    error = ref(''),
    message = ref('')
const result = ref({ content: [], page: 0, totalPages: 0, totalElements: 0 })
const form = reactive({ start: `${today()}T18:00`, end: `${today()}T20:00`, reason: '' })
const notes = reactive({})
let key = ''
watch(
    form,
    () => {
        key = ''
    },
    { deep: true },
)
watch([inbox, status], () => load())
const dateTime = (value) =>
    value
        ? new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'short',
            timeStyle: 'short',
            timeZone: 'Asia/Ho_Chi_Minh',
        }).format(new Date(value))
        : '—'
async function load(page = 0) {
    busy.value = true
    error.value = ''
    try {
        result.value = await attendance.overtime(inbox.value, status.value, page)
    } catch (cause) {
        error.value = attendanceError(cause)
    } finally {
        busy.value = false
    }
}
async function submit() {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
        if (!form.reason.trim() || form.end <= form.start)
            throw new Error('Nhập lý do và thời gian kết thúc sau thời gian bắt đầu.')
        key ||= crypto.randomUUID()
        await attendance.createOvertime({ ...form, reason: form.reason.trim() }, key)
        key = ''
        message.value = 'Đã gửi đăng ký OT. Chỉ bắt đầu OT sau khi được duyệt.'
        await load()
    } catch (cause) {
        error.value = attendanceError(cause)
    } finally {
        busy.value = false
    }
}
async function action(row, operation) {
    busy.value = true
    error.value = ''
    message.value = ''
    try {
        if (operation === 'APPROVED' || operation === 'REJECTED') {
            if (operation === 'REJECTED' && !notes[row.id]?.trim()) throw new Error('Nhập lý do từ chối.')
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
        message.value = 'Đã cập nhật OT.'
        await load(result.value.page)
    } catch (cause) {
        error.value = attendanceError(cause)
    } finally {
        busy.value = false
    }
}
onMounted(load)
</script>
<template>
    <div class="attendance-page">
        <div class="page-heading">
            <div>
                <p class="eyebrow">CHẤM CÔNG</p>
                <h1>Tăng ca (OT)</h1>
                <p class="muted">
                    Đăng ký trước khi làm, HR/Admin duyệt, sau đó ghi nhận bắt đầu và kết thúc qua mạng công
                    ty.
                </p>
            </div>
            <button class="button button-secondary" :disabled="busy" @click="load()">Tải lại</button>
        </div>
        <AttendanceNav />
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <p v-if="message" class="info-strip" role="status">{{ message }}</p>
        <form class="panel create-form" @submit.prevent="submit">
            <h2>Đăng ký OT của tôi</h2>
            <p class="muted">
                Giờ Việt Nam. OT được tính riêng theo số phút thực tế trong khoảng đã duyệt, không cộng vào
                công thường. Thiếu giờ kết thúc thì chưa tính OT. Có thể đăng ký qua đêm hoặc ngày nghỉ.
            </p>
            <fieldset :disabled="busy">
                <div class="attendance-fields">
                    <div class="field">
                        <label for="ot-start">Bắt đầu</label><input id="ot-start" v-model="form.start"
                            type="datetime-local" required />
                    </div>
                    <div class="field">
                        <label for="ot-end">Kết thúc</label><input id="ot-end" v-model="form.end" type="datetime-local"
                            :min="form.start" required />
                    </div>
                </div>
                <div class="field">
                    <label for="ot-reason">Lý do tăng ca</label><textarea id="ot-reason" v-model="form.reason" required
                        maxlength="1000" />
                </div>
                <button class="button button-primary">Gửi đăng ký OT</button>
            </fieldset>
        </form>
        <section class="panel create-form">
            <h2>{{ inbox ? 'Duyệt OT' : 'OT của tôi' }}</h2>
            <div v-if="reviewer" class="button-row">
                <button class="button button-secondary" :disabled="busy || !inbox" @click="inbox = false">
                    Của tôi</button><button class="button button-secondary" :disabled="busy || inbox"
                    @click="inbox = true">
                    Hộp duyệt HR
                </button>
            </div>
            <div class="field">
                <label for="ot-status">Trạng thái</label><select id="ot-status" v-model="status" :disabled="busy">
                    <option value="">Tất cả</option>
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
                    OT đã duyệt: {{ minutesLabel(row.approvedMinutes) }} · Đã ghi nhận:
                    {{ minutesLabel(row.countedMinutes) }}
                </p>
                <p>
                    Bắt đầu thực tế: {{ dateTime(row.checkIn) }} · Kết thúc thực tế:
                    {{ dateTime(row.checkOut) }}
                </p>
                <p v-if="row.reviewedAt">Duyệt lúc {{ dateTime(row.reviewedAt) }} · {{ row.reviewNote }}</p>
                <template v-if="inbox && row.status === 'PENDING'">
                    <div class="field">
                        <label :for="`ot-note-${row.id}`">Ghi chú / lý do từ chối</label><textarea
                            :id="`ot-note-${row.id}`" v-model="notes[row.id]" maxlength="1000" :disabled="busy" />
                    </div>
                    <div class="button-row">
                        <button class="button button-primary" :disabled="busy" @click="action(row, 'APPROVED')">
                            Duyệt OT</button><button class="button button-secondary" :disabled="busy"
                            @click="action(row, 'REJECTED')">
                            Từ chối
                        </button>
                    </div>
                </template>
                <button v-if="!inbox && row.status === 'PENDING'" class="button button-secondary" :disabled="busy"
                    @click="action(row, 'cancel')">
                    Rút đăng ký
                </button>
                <div v-if="!inbox && row.status === 'APPROVED'" class="button-row">
                    <button class="button button-primary" :disabled="busy || !!row.checkIn"
                        @click="action(row, 'check-in')">
                        Bắt đầu OT</button><button class="button button-secondary"
                        :disabled="busy || !row.checkIn || !!row.checkOut" @click="action(row, 'check-out')">
                        Kết thúc OT
                    </button>
                </div>
            </article>
            <p v-if="!result.content.length" class="muted">Chưa có đăng ký OT phù hợp.</p>
            <PageControls :page="result.page" :pages="result.totalPages" :total="result.totalElements" :busy="busy"
                @change="load" />
        </section>
    </div>
</template>
