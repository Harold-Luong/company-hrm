<script setup>
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { today, minutesLabel, attendanceError, attendanceRoles } from '@/attendance/helpers.js'
import { dateLabel } from '@/leave/helpers.js'
import { requestBody, requestStatuses, requestTypes } from '@/attendance/requests.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import AppIcon from '@/components/AppIcon.vue'
import PageControls from '@/components/PageControls.vue'
const defaults = () => ({
  requestType: 'LATE_ARRIVAL',
  workDate: today(),
  period: 'MORNING',
  expectedTime: '',
  reason: '',
})
const form = reactive(defaults()),
  rows = ref([]),
  plan = ref(null),
  editing = ref(null),
  cancelId = ref(null),
  filter = ref(''),
  inbox = ref(false)
const error = ref(''),
  message = ref(''),
  busy = ref(false),
  scheduleBusy = ref(false),
  formElement = ref(null),
  reasonElement = ref(null)
const pagination = ref({ page: 0, totalPages: 0, totalElements: 0 }),
  histories = reactive({})
const reviewer = computed(() => auth.hasRole(attendanceRoles))
let submitKey = '',
  sequence = 0
const interval = computed(() =>
  plan.value?.definition?.intervals.find((value) => value.period === form.period),
)
const isLate = computed(() => form.requestType === 'LATE_ARRIVAL')
const timeTitle = computed(() => (isLate.value ? 'Giờ đến dự kiến' : 'Giờ về dự kiến'))
const duration = computed(() => {
  try {
    return requestBody({ ...form, reason: form.reason || 'Xem trước' }, plan.value).requestedMinutes
  } catch {
    return null
  }
})
const filteredRows = computed(() => rows.value)
watch(
  form,
  () => {
    submitKey = ''
  },
  { deep: true },
)
async function reload() {
  await load()
  if (!error.value && editing.value) {
    const current = rows.value.find((row) => row.id === editing.value.id)
    if (current?.status === 'PENDING') await edit(current)
    else reset()
  }
  await loadSchedule()
}
async function loadSchedule() {
  const ticket = ++sequence,
    date = form.workDate
  plan.value = null
  if (!date) return
  scheduleBusy.value = true
  try {
    const result = await attendance.schedule(date, date)
    if (ticket !== sequence) return
    plan.value = result[0] || null
    if (!plan.value?.definition)
      error.value = 'Chưa được phân ca cho ngày đã chọn. Liên hệ HR trước khi gửi đơn.'
    else if (!plan.value.definition.intervals.some((i) => i.period === form.period))
      form.period = plan.value.definition.intervals[0].period
  } catch (cause) {
    if (ticket === sequence) error.value = attendanceError(cause)
  } finally {
    if (ticket === sequence) scheduleBusy.value = false
  }
}
watch(() => form.workDate, loadSchedule)
async function load(page = 0) {
  busy.value = true
  error.value = ''
  try {
    const result = await attendance.requests(inbox.value, filter.value, page)
    rows.value = result.content
    pagination.value = result
  } catch (cause) {
    rows.value = []
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
watch([filter, inbox], () => {
  reset()
  load()
})
function reset() {
  Object.assign(form, defaults())
  editing.value = null
  cancelId.value = null
  error.value = ''
  submitKey = ''
}
async function submit() {
  if (busy.value || scheduleBusy.value) return
  error.value = ''
  message.value = ''
  try {
    const body = requestBody(form, plan.value)
    busy.value = true
    submitKey ||= crypto.randomUUID()
    await attendance.saveRequest(editing.value?.id, body, editing.value?.version, submitKey)
    const updated = !!editing.value
    reset()
    await load()
    message.value = updated ? 'Đã cập nhật đơn chờ duyệt.' : 'Đã gửi đơn đến HR để xét duyệt.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function edit(row) {
  Object.assign(form, {
    requestType: row.requestType,
    workDate: row.workDate,
    period: row.period,
    expectedTime: row.expectedTime.slice(0, 5),
    reason: row.reason,
  })
  editing.value = row
  cancelId.value = null
  error.value = ''
  message.value = ''
  await loadSchedule()
  formElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  reasonElement.value?.focus({ preventScroll: true })
}
async function cancel(row) {
  busy.value = true
  error.value = ''
  try {
    await attendance.cancelRequest(row)
    reset()
    await load()
    message.value = 'Đã rút đơn chờ duyệt.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function decide(row, status) {
  busy.value = true
  error.value = ''
  try {
    await attendance.decideRequest(row, status, row.decisionNote?.trim() || null)
    await load()
    message.value = status === 'APPROVED' ? 'Đã duyệt đơn.' : 'Đã từ chối đơn.'
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function showHistory(row) {
  busy.value = true
  error.value = ''
  try {
    histories[row.id] = (await attendance.requestHistory(row.id)).content
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  load()
  loadSchedule()
})
</script>

<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CHẤM CÔNG</p>
        <h1>Xin đi trễ / về sớm</h1>
        <p class="muted">
          Đăng ký thời gian đến muộn hoặc về sớm và theo dõi đơn xin phép của bạn.
        </p>
      </div>
      <RouterLink to="/attendance" class="button button-secondary"
        ><AppIcon name="clock" :size="17" />Công của tôi</RouterLink
      >
    </div>
    <AttendanceNav />
    <div v-if="reviewer" class="button-row">
      <button class="button button-secondary" :disabled="busy || !inbox" @click="inbox = false">
        Đơn của tôi
      </button>
      <button class="button button-secondary" :disabled="busy || inbox" @click="inbox = true">
        Hàng chờ duyệt
      </button>
    </div>
    <p class="info-strip">
      Đơn được duyệt ghi nhận phần có phép; thời gian thiếu vẫn trừ công theo bước 15 phút. Không
      trừ số dư phép năm.
    </p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <button class="button button-secondary" :disabled="busy || scheduleBusy" @click="reload">
      Tải lại dữ liệu
    </button>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div v-if="!inbox" class="attendance-request-layout">
      <form ref="formElement" class="panel create-form" @submit.prevent="submit">
        <div class="attendance-section-heading">
          <div>
            <h2>{{ editing ? 'Chỉnh sửa đơn xin phép' : 'Tạo đơn xin phép' }}</h2>
            <p class="muted small">Các trường bên dưới đều bắt buộc.</p>
          </div>
          <span class="attendance-badge">{{ editing ? 'Đang chỉnh sửa' : 'Đơn mới' }}</span>
        </div>
        <fieldset class="attendance-request-types" :disabled="busy || scheduleBusy">
          <legend>Loại yêu cầu</legend>
          <label
            v-for="(label, value) in requestTypes"
            :key="value"
            :class="{ selected: form.requestType === value }"
            ><input
              v-model="form.requestType"
              type="radio"
              name="request-type"
              :value="value"
            /><AppIcon :name="value === 'LATE_ARRIVAL' ? 'clock' : 'logout'" /><span>{{
              label
            }}</span></label
          >
        </fieldset>
        <div class="attendance-fields">
          <div class="field">
            <label for="request-date">Ngày xin phép</label
            ><input
              id="request-date"
              v-model="form.workDate"
              :disabled="busy"
              type="date"
              :min="today()"
              required
            />
          </div>
          <div class="field">
            <label for="request-period">Buổi làm việc</label
            ><select id="request-period" v-model="form.period" :disabled="busy || scheduleBusy">
              <option
                v-for="item in plan?.definition?.intervals || []"
                :key="item.period"
                :value="item.period"
              >
                {{ item.period === 'MORNING' ? 'Buổi sáng' : 'Buổi chiều' }} ·
                {{ item.start.slice(0, 5) }}–{{ item.end.slice(0, 5) }}
              </option>
            </select>
          </div>
        </div>
        <div class="attendance-fields">
          <div class="field">
            <label for="request-time">{{ timeTitle }}</label
            ><input
              id="request-time"
              v-model="form.expectedTime"
              :disabled="busy || scheduleBusy"
              type="time"
              required
              aria-describedby="request-time-help"
            />
            <p id="request-time-help" class="field-help">
              Chọn giờ nằm trong buổi làm việc {{ interval?.start?.slice(0, 5) || '—' }}–{{
                interval?.end?.slice(0, 5) || '—'
              }}.
            </p>
          </div>
          <div class="attendance-request-duration">
            <span>{{ isLate ? 'Thời gian xin đi trễ' : 'Thời gian xin về sớm' }}</span
            ><strong>{{ duration == null ? '—' : minutesLabel(duration) }}</strong>
          </div>
        </div>
        <div class="field">
          <label for="request-reason">Lý do xin phép</label
          ><textarea
            id="request-reason"
            ref="reasonElement"
            v-model="form.reason"
            :disabled="busy"
            rows="4"
            maxlength="1000"
            required
            placeholder="Mô tả lý do và kế hoạch bàn giao công việc (nếu có)…"
          ></textarea>
          <p class="field-help attendance-character-count">
            {{ form.reason.length }} / 1.000 ký tự
          </p>
        </div>

        <div class="button-row">
          <button
            class="button button-primary"
            :disabled="busy || scheduleBusy || !plan?.definition"
          >
            <AppIcon name="check" :size="17" />{{ editing ? 'Lưu thay đổi' : 'Gửi đơn' }}</button
          ><button class="button button-secondary" type="button" :disabled="busy" @click="reset">
            {{ editing ? 'Hủy chỉnh sửa' : 'Nhập lại' }}
          </button>
        </div>
      </form>
      <aside class="panel create-form attendance-request-aside">
        <p class="eyebrow">THÔNG TIN ĐƠN</p>
        <h2>{{ requestTypes[form.requestType] }}</h2>
        <dl class="attendance-request-summary">
          <div>
            <dt>Ngày xin phép</dt>
            <dd>{{ dateLabel(form.workDate) || 'Chưa chọn' }}</dd>
          </div>
          <div>
            <dt>Ca tham chiếu</dt>
            <dd>{{ plan?.definition?.name || 'Chưa có ca' }}</dd>
          </div>
          <div>
            <dt>Buổi làm việc</dt>
            <dd>
              {{ interval?.start?.slice(0, 5) || '—' }}–{{ interval?.end?.slice(0, 5) || '—' }}
            </dd>
          </div>
          <div>
            <dt>{{ timeTitle }}</dt>
            <dd>{{ form.expectedTime || 'Chưa chọn' }}</dd>
          </div>
          <div>
            <dt>Thời lượng xin phép</dt>
            <dd>{{ duration == null ? 'Chưa xác định' : minutesLabel(duration) }}</dd>
          </div>
        </dl>
        <div class="attendance-guide-note">
          <AppIcon name="info" :size="18" /><span
            >Thời lượng xin phép dựa trên ca được phân công. Giờ chấm công thực tế được giữ nguyên;
            đơn duyệt chỉ giúp phân biệt phần có phép và chưa có phép.</span
          >
        </div>
        <RouterLink to="/leave" class="text-button attendance-related-link"
          >Cần nghỉ cả buổi / cả ngày? <AppIcon name="arrow" :size="16"
        /></RouterLink>
      </aside>
    </div>
    <section class="panel create-form">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ inbox ? 'Hàng chờ HR/Admin' : 'Đơn xin phép của tôi' }}</h2>
          <p class="muted small">
            {{ rows.filter((row) => row.status === 'PENDING').length }} đơn chờ duyệt trên trang
          </p>
        </div>
        <span class="attendance-badge">{{ filteredRows.length }} đơn</span>
      </div>
      <div class="attendance-table-tools">
        <div class="field">
          <label for="request-status">Trạng thái đơn</label
          ><select id="request-status" v-model="filter" :disabled="busy">
            <option value="">Tất cả trạng thái</option>
            <option v-for="(label, value) in requestStatuses" :key="value" :value="value">
              {{ label }}
            </option>
          </select>
        </div>
      </div>
      <div
        class="table-wrap attendance-request-table"
        tabindex="0"
        aria-label="Danh sách đơn xin đi trễ về sớm"
      >
        <table>
          <thead>
            <tr>
              <th scope="col">Ngày / Buổi</th>
              <th scope="col">Loại yêu cầu</th>
              <th scope="col">Giờ dự kiến</th>
              <th scope="col">Thời lượng</th>
              <th scope="col">Trạng thái</th>
              <th scope="col">Chi tiết / Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredRows" :key="row.id">
              <td class="attendance-date">
                {{ dateLabel(row.workDate)
                }}<small>{{ row.period === 'MORNING' ? 'Buổi sáng' : 'Buổi chiều' }}</small>
              </td>
              <td>{{ requestTypes[row.requestType] }}</td>
              <td>{{ row.expectedTime }}</td>
              <td>{{ minutesLabel(row.requestedMinutes) }}</td>
              <td>
                <span class="attendance-badge" :class="`request-${row.status.toLowerCase()}`">{{
                  requestStatuses[row.status]
                }}</span>
              </td>
              <td>
                <p v-if="inbox">{{ row.employeeName }} · {{ row.employeeCode }}</p>
                <details class="attendance-request-detail">
                  <summary>Lý do & phản hồi</summary>
                  <p>{{ row.reason }}</p>
                  <p v-if="row.reviewNote"><strong>Phản hồi:</strong> {{ row.reviewNote }}</p>
                  <p v-else class="muted small">
                    {{ row.status === 'CANCELLED' ? 'Đơn đã được rút.' : 'Chưa có phản hồi.' }}
                  </p>
                </details>
                <div v-if="row.status === 'PENDING' && !inbox" class="button-row">
                  <button type="button" class="text-button" :disabled="busy" @click="edit(row)">
                    Sửa đơn</button
                  ><button
                    type="button"
                    class="text-button attendance-cancel-link"
                    :disabled="busy"
                    @click="cancelId = row.id"
                  >
                    Rút đơn
                  </button>
                </div>
                <div v-if="inbox && row.status === 'PENDING'" class="create-form">
                  <label :for="`decision-${row.id}`">Phản hồi (bắt buộc khi từ chối)</label>
                  <textarea
                    :id="`decision-${row.id}`"
                    v-model="row.decisionNote"
                    maxlength="1000"
                    :disabled="busy"
                  ></textarea>
                  <div class="button-row">
                    <button
                      class="button button-primary"
                      type="button"
                      :disabled="busy || row.employeeId === auth.state.user?.employeeId"
                      @click="decide(row, 'APPROVED')"
                    >
                      Duyệt đơn
                    </button>
                    <button
                      class="button button-secondary"
                      type="button"
                      :disabled="
                        busy ||
                        !row.decisionNote?.trim() ||
                        row.employeeId === auth.state.user?.employeeId
                      "
                      @click="decide(row, 'REJECTED')"
                    >
                      Từ chối
                    </button>
                  </div>
                </div>
                <button
                  class="text-button"
                  type="button"
                  :disabled="busy"
                  @click="showHistory(row)"
                >
                  Lịch sử đơn
                </button>
                <ul v-if="histories[row.id]">
                  <li v-for="event in histories[row.id]" :key="event.id">
                    {{ event.action }} · {{ new Date(event.occurredAt).toLocaleString('vi-VN') }}
                  </li>
                </ul>
                <div v-if="cancelId === row.id" class="attendance-cancel-confirm">
                  <p>Rút đơn này khỏi danh sách chờ duyệt?</p>
                  <div class="button-row">
                    <button
                      type="button"
                      class="button button-danger-outline"
                      :disabled="busy"
                      @click="cancel(row)"
                    >
                      Xác nhận rút</button
                    ><button type="button" class="button button-secondary" @click="cancelId = null">
                      Giữ đơn
                    </button>
                  </div>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredRows.length">
              <td colspan="6">
                <div class="attendance-empty">
                  <AppIcon name="calendar" :size="28" /><strong>Chưa có đơn phù hợp</strong
                  ><span>Thử chọn trạng thái khác hoặc tạo đơn xin phép mới.</span
                  ><button v-if="filter" type="button" class="text-button" @click="filter = ''">
                    Xem tất cả đơn
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
