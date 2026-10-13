<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { today, attendanceError, minutesLabel } from '@/attendance/helpers.js'
import { attendanceOverview, reviewReasons, summarizeAttendance } from '@/attendance/report.js'
import AttendanceTable from '@/components/AttendanceTable.vue'
import AttendanceReportSummary from '@/components/AttendanceReportSummary.vue'
import ReferencePicker from '@/components/ReferencePicker.vue'
import AppIcon from '@/components/AppIcon.vue'
import '@/assets/styles/attendance-report.css'

const filters = reactive({
    from: `${today().slice(0, 7)}-01`,
    until: today(),
    employeeId: '',
    departmentId: '',
})
const rows = ref([])
const loading = ref(false)
const exporting = ref(false)
const refreshing = ref(false)
const error = ref('')
const actionError = ref('')
const notice = ref('')
const loaded = ref(null)
const selectedId = ref(null)
const onlyReviewDays = ref(false)
const summary = ref(null)
const detailHeading = ref(null)
const busy = computed(() => loading.value || exporting.value || refreshing.value)
const employees = computed(() => summarizeAttendance(rows.value))
const overview = computed(() => attendanceOverview(employees.value))
const selected = computed(() =>
    employees.value.find((person) => person.employeeId === selectedId.value),
)
const detailRows = computed(
    () =>
        selected.value?.days.filter((day) => !onlyReviewDays.value || reviewReasons(day).length) ?? [],
)
const dirty = computed(
    () => loaded.value && Object.keys(filters).some((key) => filters[key] !== loaded.value[key]),
)
const time = (value) => (value == null ? '—' : minutesLabel(value))
const dateLabel = (value) => value?.split('-').reverse().join('/')
const cards = computed(() => [
    {
        label: 'Nhân viên trong kỳ',
        value: overview.value.employees,
        note: 'Có dữ liệu trong phạm vi đã chọn',
        icon: 'user',
        tone: 'blue',
    },
    {
        label: 'Nhân viên cần kiểm tra',
        value: overview.value.reviewEmployees,
        note: `${overview.value.reviewDays} ngày · ${overview.value.pendingRequests} đơn chờ duyệt`,
        icon: 'info',
        tone: 'amber',
        review: true,
    },
    {
        label: 'Giờ tính công đã xác định',
        value: time(overview.value.workMinutes),
        note: overview.value.incompleteDays
            ? `Chưa đầy đủ · ${overview.value.incompleteDays} ngày chưa tính được công`
            : 'Giờ công thường, tách riêng tăng ca',
        icon: 'clock',
        tone: 'green',
    },
    {
        label: 'Tăng ca ghi nhận',
        value: time(overview.value.otCountedMinutes),
        note: `${time(overview.value.otApprovedMinutes)} đã được duyệt trong kỳ`,
        icon: 'check',
        tone: 'purple',
    },
])
async function load() {
    if (busy.value) return
    loading.value = true
    error.value = ''
    actionError.value = ''
    notice.value = ''
    rows.value = []
    loaded.value = null
    selectedId.value = null
    try {
        const request = { ...filters }
        rows.value = await attendance.report(request)
        loaded.value = request
    } catch (cause) {
        error.value = attendanceError(cause)
    } finally {
        loading.value = false
    }
}
async function download() {
    if (!loaded.value || busy.value) return
    exporting.value = true
    actionError.value = ''
    try {
        const blob = await attendance.export(loaded.value)
        const url = URL.createObjectURL(blob),
            anchor = document.createElement('a')
        anchor.href = url
        anchor.download = `bang-cong-${loaded.value.from}-${loaded.value.until}.csv`
        document.body.append(anchor)
        anchor.click()
        anchor.remove()
        setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (cause) {
        actionError.value = attendanceError(cause)
    } finally {
        exporting.value = false
    }
}
async function refresh(row) {
    if (busy.value) return
    refreshing.value = true
    actionError.value = ''
    notice.value = ''
    try {
        const updated = await attendance.refresh(row)
        rows.value = rows.value.map((value) =>
            value.employeeId === updated.employeeId && value.workDate === updated.workDate
                ? updated
                : value,
        )
        notice.value = `Đã cập nhật phép/lịch ngày ${dateLabel(row.workDate)} và số liệu tổng hợp.`
    } catch (cause) {
        actionError.value = attendanceError(cause)
    } finally {
        refreshing.value = false
    }
}
async function openDetails(id) {
    selectedId.value = id
    onlyReviewDays.value = false
    notice.value = ''
    actionError.value = ''
    await nextTick()
    detailHeading.value?.focus()
    detailHeading.value?.scrollIntoView({ block: 'start' })
}
async function closeDetails() {
    const id = selectedId.value
    selectedId.value = null
    notice.value = ''
    actionError.value = ''
    await nextTick()
    summary.value?.focusEmployee(id)
}
async function showReview() {
    selectedId.value = null
    await nextTick()
    summary.value?.showReview()
}
onMounted(load)
</script>
<template>
    <div class="attendance-page report-page">
        <div class="page-heading">
            <div>
                <p class="eyebrow">QUẢN LÝ CHẤM CÔNG</p>
                <h1>Bảng công <span class="report-tag">Tạm tính</span></h1>
                <p class="muted">
                    Nắm tổng công từng nhân viên, kiểm tra bất thường và xem chi tiết khi cần.
                </p>
            </div>
            <button class="button button-primary" :disabled="busy || !loaded || !rows.length" @click="download">
                {{ exporting ? 'Đang xuất…' : 'Xuất CSV theo ngày' }}
            </button>
        </div>
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <form class="panel report-filters" @submit.prevent="load">
            <fieldset :disabled="busy">
                <div class="attendance-fields">
                    <div class="field">
                        <label for="report-from">Từ ngày</label><input id="report-from" v-model="filters.from"
                            type="date" :max="today()" required />
                    </div>
                    <div class="field">
                        <label for="report-until">Đến ngày</label><input id="report-until" v-model="filters.until"
                            type="date" :min="filters.from" :max="today()" required />
                    </div>
                    <ReferencePicker v-model="filters.employeeId" resource="employees"
                        label="Nhân viên (tất cả nếu bỏ trống)" />
                    <ReferencePicker v-model="filters.departmentId" resource="departments"
                        label="Phòng ban (tất cả nếu bỏ trống)" />
                </div>
                <div class="report-filter-footer">
                    <button class="button button-secondary">
                        {{ loading ? 'Đang tải…' : 'Xem bảng công' }}
                    </button>
                </div>
            </fieldset>
        </form>
        <div class="report-period" aria-live="polite">
            <p v-if="loaded">
                <AppIcon name="calendar" :size="17" />
                <strong>{{ dateLabel(loaded.from) }} – {{ dateLabel(loaded.until) }}</strong><span>· {{ employees.length
                    }} nhân viên</span>
            </p>
            <p v-else>{{ loading ? 'Đang tải kỳ báo cáo…' : 'Chưa có kỳ báo cáo được tải' }}</p>
            <span v-if="dirty" class="report-warning">Bộ lọc đã đổi. Bấm “Xem bảng công” để áp dụng.</span>
        </div>
        <div class="attendance-stats" aria-label="Tổng hợp kỳ đang xem" :aria-busy="loading">
            <article v-for="card in cards" :key="card.label" class="panel attendance-stat report-stat"
                :class="{ 'report-stat-attention': card.review && overview.reviewEmployees }">
                <span class="attendance-stat-icon" :class="card.tone">
                    <AppIcon :name="card.icon" />
                </span>
                <p>{{ card.label }}</p>
                <strong>{{ loading || !loaded ? '—' : card.value }}</strong>
                <small>{{ loading || !loaded ? 'Chờ dữ liệu báo cáo' : card.note }}</small>
                <button v-if="card.review" class="text-button" :disabled="busy || !overview.reviewEmployees"
                    @click="showReview">
                    Xem người cần kiểm tra
                    <AppIcon name="arrow" :size="14" />
                </button>
            </article>
        </div>
        <div class="report-explanation">
            <AppIcon name="info" :size="18" />
            <p>
                Công chưa chốt kỳ. Tổng giờ chỉ cộng các ngày đã xác định; ngày thiếu dữ liệu không được coi
                là 0 giờ. Ngày không có ca được ghi riêng. CSV xuất chi tiết theo ngày của toàn bộ phạm vi
                đã tải.
                <RouterLink class="text-button" to="/help/features#work-time">Xem công thức tính công →</RouterLink>
            </p>
        </div>
        <p v-if="actionError" class="alert alert-error" role="alert">{{ actionError }}</p>
        <p v-if="notice" class="alert alert-success" role="status">{{ notice }}</p>
        <AttendanceReportSummary v-show="!selected" ref="summary" :employees="employees" :busy="loading"
            :failed="!!error" @select="openDetails" @retry="load" />
        <section v-if="selected" class="panel report-detail" aria-labelledby="report-detail-title"
            :aria-busy="refreshing">
            <button class="text-button report-back" :disabled="refreshing" @click="closeDetails">
                ← Quay lại tổng hợp nhân viên
            </button>
            <div class="attendance-section-heading">
                <div>
                    <p class="eyebrow">CHI TIẾT CÔNG THEO NGÀY · {{ selected.employeeCode }}</p>
                    <h2 id="report-detail-title" ref="detailHeading" tabindex="-1">
                        {{ selected.employeeName }}
                    </h2>
                    <p class="muted">
                        {{ dateLabel(loaded.from) }} – {{ dateLabel(loaded.until) }} ·
                        {{ selected.days.length }} ngày trong kỳ
                    </p>
                </div>
                <span class="report-tag" :class="{ 'report-tag-warning': selected.reviewDays }">{{ selected.reviewDays
                    }} ngày cần kiểm tra</span>
            </div>
            <div class="report-detail-metrics">
                <div>
                    <span>Giờ tính công</span><strong>{{ time(selected.workMinutes) }}</strong><small
                        v-if="selected.incompleteDays" class="report-warning">Chưa đủ · {{ selected.incompleteDays }}
                        ngày</small>
                </div>
                <div>
                    <span>Phép năm / Không lương</span><strong>{{ time(selected.annualMinutes) }} / {{
                        time(selected.unpaidMinutes) }}</strong>
                </div>
                <div>
                    <span>Trễ / Sớm đã làm tròn</span><strong>{{ selected.lateMinutes == null ? '—' :
                        `${selected.lateMinutes}p` }} /
                        {{ selected.earlyMinutes == null ? '—' : `${selected.earlyMinutes}p` }}</strong><small>{{
                        selected.lateEarlyDays }} ngày có phát sinh</small>
                </div>
                <div>
                    <span>OT ghi nhận / Được duyệt</span><strong>{{ time(selected.otCountedMinutes) }} / {{
                        time(selected.otApprovedMinutes) }}</strong>
                </div>
            </div>
            <p class="muted small">
                {{ selected.ongoingDays }} ca chưa kết thúc · {{ selected.noScheduleDays }} ngày không có ca
                · {{ selected.pendingRequests }} đơn chờ duyệt. Trễ/sớm gồm cả phần đã xin phép. Dùng “Cập
                nhật phép/lịch” nếu nguồn thay đổi sau khi chấm công.
            </p>
            <label class="report-review-toggle"><input v-model="onlyReviewDays" type="checkbox"
                    :disabled="refreshing" />Chỉ xem ngày cần
                kiểm tra</label>
            <AttendanceTable :key="selected.employeeId" :rows="detailRows" management single-employee :busy="refreshing"
                @refresh="refresh" />
        </section>
    </div>
</template>
