<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { attendanceError, attendanceRoles, today, shiftLabel } from '@/attendance/helpers.js'
import { correctionBody, localCorrectionTime } from '@/attendance/corrections.js'
import { requestStatuses } from '@/attendance/requests.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import PageControls from '@/components/PageControls.vue'
const route = useRoute()
const initialDate = /^\d{4}-\d{2}-\d{2}$/.test(route.query.date || '') ? route.query.date : today()
const form = reactive({
  workDate: initialDate,
  proposedCheckIn: '',
  proposedCheckOut: '',
  reason: '',
})
const plan = ref(null),
  day = ref(null),
  editing = ref(null),
  rows = ref([])
const inbox = ref(false),
  filter = ref(''),
  busy = ref(false),
  dayBusy = ref(false)
const error = ref(''),
  message = ref(''),
  cancelId = ref(null)
const pagination = ref({ page: 0, totalPages: 0, totalElements: 0 })
const histories = reactive({}),
  notes = reactive({})
const reviewer = computed(() => auth.hasRole(attendanceRoles))
const labels = {
  SUBMITTED: 'Gửi đơn',
  UPDATED: 'Cập nhật',
  CANCELLED: 'Rút đơn',
  APPROVED: 'Duyệt',
  REJECTED: 'Từ chối',
}
let sequence = 0,
  submitKey = ''
const stamp = (value) =>
  value
    ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
    : 'Chưa ghi nhận'
watch(
  form,
  () => {
    submitKey = ''
  },
  { deep: true, flush: 'sync' },
)
async function reload() {
  await load()
  await loadDay()
}
async function loadDay(fill = false) {
  const ticket = ++sequence,
    date = form.workDate
  plan.value = null
  day.value = null
  if (!date || date > today()) {
    dayBusy.value = false
    return
  }
  dayBusy.value = true
  try {
    const [plans, days] = await Promise.all([
      attendance.schedule(date, date),
      attendance.mine(date, date),
    ])
    if (ticket !== sequence) return
    plan.value = plans[0] || null
    day.value = days[0] || null
    if (fill) {
      form.proposedCheckIn = localCorrectionTime(day.value?.checkIn)
      form.proposedCheckOut = localCorrectionTime(day.value?.checkOut)
    }
  } catch (cause) {
    if (ticket === sequence) error.value = attendanceError(cause)
  } finally {
    if (ticket === sequence) dayBusy.value = false
  }
}
watch(
  () => form.workDate,
  () => loadDay(true),
)
async function load(page = 0) {
  busy.value = true
  error.value = ''
  try {
    const result = await attendance.corrections(inbox.value, filter.value, page)
    rows.value = result.content
    pagination.value = result
  } catch (cause) {
    rows.value = []
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
watch([inbox, filter], () => {
  editing.value = null
  cancelId.value = null
  load()
})
async function submit() {
  if (busy.value || dayBusy.value) return
  error.value = ''
  message.value = ''
  try {
    const body = correctionBody(form, plan.value, day.value)
    busy.value = true
    submitKey ||= crypto.randomUUID()
    await attendance.saveCorrection(editing.value?.id, body, editing.value?.version, submitKey)
    editing.value = null
    form.reason = ''
    submitKey = ''
    await load()
    await loadDay(true)
    message.value = 'Đã lưu đơn chờ HR/Admin xét duyệt. Bảng công chỉ thay đổi khi đơn được duyệt.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function edit(row) {
  editing.value = row
  error.value = ''
  message.value = ''
  // Load the current day before restoring the proposal, including when changing dates.
  form.workDate = row.workDate
  await nextTick()
  await loadDay()
  form.proposedCheckIn = localCorrectionTime(row.proposedCheckIn)
  form.proposedCheckOut = localCorrectionTime(row.proposedCheckOut)
  form.reason = row.reason
}
async function stopEditing() {
  editing.value = null
  form.reason = ''
  await loadDay(true)
}
async function cancel(row) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await attendance.cancelCorrection(row)
    cancelId.value = null
    if (editing.value?.id === row.id) await stopEditing()
    await load()
    message.value = 'Đã rút đơn.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function decide(row, status) {
  error.value = ''
  message.value = ''
  const note = notes[row.id]?.trim() || null
  if (status === 'REJECTED' && !note) {
    error.value = 'Nhập lý do từ chối đơn.'
    return
  }
  busy.value = true
  try {
    await attendance.decideCorrection(row, status, note)
    delete histories[row.id]
    await load()
    message.value = status === 'APPROVED' ? 'Đã duyệt và cập nhật bảng công.' : 'Đã từ chối đơn.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function history(row, page = 0) {
  busy.value = true
  error.value = ''
  try {
    const result = await attendance.correctionHistory(row.id, page)
    histories[row.id] = {
      ...result,
      content: result.content.map((event) => ({ ...event, data: JSON.parse(event.snapshot) })),
    }
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  load()
  loadDay(true)
})
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CHẤM CÔNG</p>
        <h1>Bổ sung / Điều chỉnh công</h1>
        <p class="muted">
          Gửi giờ thực tế khi quên chấm công hoặc ghi nhận nhầm, sau khi ca kết thúc.
        </p>
      </div>
      <button class="button button-secondary" :disabled="busy || dayBusy" @click="reload">
        Tải lại
      </button>
    </div>
    <AttendanceNav />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div v-if="reviewer" class="button-row">
      <button
        class="button button-secondary"
        :aria-pressed="!inbox"
        :disabled="busy"
        @click="inbox = false"
      >
        Đơn của tôi
      </button>
      <button
        class="button button-secondary"
        :aria-pressed="inbox"
        :disabled="busy"
        @click="inbox = true"
      >
        Xét duyệt
      </button>
    </div>
    <form v-if="!inbox" class="panel create-form" @submit.prevent="submit">
      <h2>{{ editing ? 'Sửa đơn chờ duyệt' : 'Tạo đơn bổ sung / điều chỉnh' }}</h2>
      <p class="muted">
        Nhập đủ giờ vào và giờ ra theo giờ Việt Nam. Với ca qua đêm, ngày công là ngày bắt đầu ca;
        giờ ra thuộc ngày hôm sau.
      </p>
      <fieldset :disabled="busy || dayBusy" class="attendance-correction-fields">
        <div class="attendance-fields">
          <div class="field">
            <label for="correction-date">Ngày công</label
            ><input
              id="correction-date"
              v-model="form.workDate"
              type="date"
              :max="today()"
              required
            />
          </div>
          <div class="field">
            <label for="correction-in">Giờ vào đề nghị</label
            ><input
              id="correction-in"
              v-model="form.proposedCheckIn"
              type="datetime-local"
              step="1"
              required
            />
          </div>
          <div class="field">
            <label for="correction-out">Giờ ra đề nghị</label
            ><input
              id="correction-out"
              v-model="form.proposedCheckOut"
              type="datetime-local"
              step="1"
              required
            />
          </div>
        </div>
        <p v-if="dayBusy" class="muted">Đang tải ngày công…</p>
        <template v-else-if="plan?.definition">
          <p class="muted">{{ plan.definition.name }} · {{ shiftLabel(plan.definition) }}</p>
          <p>Giờ đang tính công: {{ stamp(day?.checkIn) }} → {{ stamp(day?.checkOut) }}</p>
        </template>
        <p v-else class="muted">Không có lịch làm việc cho ngày đã chọn.</p>
        <div class="field">
          <label for="correction-reason">Lý do bổ sung / điều chỉnh</label
          ><textarea
            id="correction-reason"
            v-model="form.reason"
            maxlength="1000"
            rows="3"
            required
          />
        </div>
        <div class="button-row">
          <button class="button button-primary" :disabled="dayBusy || !plan?.definition">
            {{ editing ? 'Lưu thay đổi' : 'Gửi đơn' }}
          </button>
          <button v-if="editing" type="button" class="button button-secondary" @click="stopEditing">
            Hủy sửa
          </button>
        </div>
      </fieldset>
    </form>
    <section class="panel create-form" :aria-busy="busy">
      <h2>{{ inbox ? 'Đơn cần xét duyệt' : 'Đơn đã gửi' }}</h2>
      <div class="field">
        <label for="correction-status">Trạng thái đơn</label
        ><select id="correction-status" v-model="filter" :disabled="busy">
          <option value="">Tất cả</option>
          <option v-for="(label, value) in requestStatuses" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>
      <p v-if="busy" class="muted">Đang xử lý…</p>
      <p v-else-if="!rows.length" class="muted">Chưa có đơn phù hợp.</p>
      <article v-for="row in rows" :key="row.id" class="panel create-form">
        <h3>
          {{ row.workDate }} · {{ row.employeeName }}
          <span class="attendance-badge">{{ requestStatuses[row.status] }}</span>
        </h3>
        <p>Trước điều chỉnh: {{ stamp(row.beforeCheckIn) }} → {{ stamp(row.beforeCheckOut) }}</p>
        <p>
          <strong
            >Đề nghị: {{ stamp(row.proposedCheckIn) }} → {{ stamp(row.proposedCheckOut) }}</strong
          >
        </p>
        <p>Lý do: {{ row.reason }}</p>
        <p v-if="row.reviewedAt" class="muted">
          Xét duyệt bởi {{ row.reviewedBy }} · {{ stamp(row.reviewedAt) }}
        </p>
        <p v-if="row.reviewNote">Ghi chú xét duyệt: {{ row.reviewNote }}</p>
        <div
          v-if="inbox && row.status === 'PENDING' && row.employeeId !== auth.state.user?.employeeId"
          class="create-form"
        >
          <div class="field">
            <label :for="`review-${row.id}`">Ghi chú xét duyệt (bắt buộc khi từ chối)</label
            ><textarea
              :id="`review-${row.id}`"
              v-model="notes[row.id]"
              maxlength="1000"
              :disabled="busy"
            />
          </div>
          <div class="button-row">
            <button class="button button-primary" :disabled="busy" @click="decide(row, 'APPROVED')">
              Duyệt và cập nhật công</button
            ><button
              class="button button-danger-outline"
              :disabled="busy"
              @click="decide(row, 'REJECTED')"
            >
              Từ chối
            </button>
          </div>
        </div>
        <div class="button-row">
          <template v-if="!inbox && row.status === 'PENDING'"
            ><button class="button button-secondary" :disabled="busy || dayBusy" @click="edit(row)">
              Sửa đơn</button
            ><button
              class="button button-danger-outline"
              :disabled="busy"
              @click="cancelId = row.id"
            >
              Rút đơn
            </button></template
          >
          <button class="text-button" :disabled="busy" @click="history(row)">Lịch sử đơn</button>
        </div>
        <div v-if="cancelId === row.id" class="info-strip">
          <p>Rút đơn này khỏi danh sách chờ duyệt?</p>
          <button class="button button-danger-outline" :disabled="busy" @click="cancel(row)">
            Xác nhận rút
          </button>
          <button class="button button-secondary" :disabled="busy" @click="cancelId = null">
            Giữ đơn
          </button>
        </div>
        <template v-if="histories[row.id]">
          <ul>
            <li v-for="event in histories[row.id].content" :key="event.id">
              {{ labels[event.action] || event.action }} · {{ stamp(event.occurredAt) }} ·
              {{ event.actorUserId }}
              <p>
                {{ stamp(event.data.beforeCheckIn) }} → {{ stamp(event.data.beforeCheckOut) }} ⇒
                {{ stamp(event.data.proposedCheckIn) }} → {{ stamp(event.data.proposedCheckOut) }}
              </p>
              <p>
                {{ event.data.reason
                }}<span v-if="event.data.reviewNote"> · {{ event.data.reviewNote }}</span>
              </p>
            </li>
          </ul>
          <PageControls
            :page="histories[row.id].page"
            :pages="histories[row.id].totalPages"
            :total="histories[row.id].totalElements"
            :busy="busy"
            @change="history(row, $event)"
          />
        </template>
      </article>
      <PageControls
        :page="pagination.page"
        :pages="pagination.totalPages"
        :total="pagination.totalElements"
        :busy="busy"
        @change="load"
      />
    </section>
  </div>
</template>
<style scoped>
.attendance-correction-fields {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
  display: grid;
  gap: 1rem;
}
</style>
