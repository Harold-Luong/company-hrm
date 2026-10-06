<script setup>
import { computed, reactive, ref } from 'vue'
import { auth } from '@/auth/session.js'
import { today, minutesLabel } from '@/attendance/helpers.js'
import { dateLabel } from '@/leave/helpers.js'
import {
  previewShift,
  requestBody,
  requestPreview,
  requestStatuses,
  requestTypes,
} from '@/attendance/requests.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import AppIcon from '@/components/AppIcon.vue'

const defaults = () => ({
  requestType: 'LATE_ARRIVAL',
  workDate: today(),
  period: 'MORNING',
  expectedTime: '',
  reason: '',
})
const form = reactive(defaults())
const rows = requestPreview(auth.state.user.id)
const editing = ref(null)
const cancelId = ref(null)
const filter = ref('')
const error = ref('')
const message = ref('')
const formElement = ref(null)
const reasonElement = ref(null)
const interval = computed(() =>
  previewShift.definition.intervals.find((value) => value.period === form.period),
)
const isLate = computed(() => form.requestType === 'LATE_ARRIVAL')
const timeTitle = computed(() => (isLate.value ? 'Giờ đến dự kiến' : 'Giờ về dự kiến'))
const duration = computed(() => {
  try {
    return requestBody({ ...form, reason: form.reason || 'Xem trước' }).requestedMinutes
  } catch {
    return null
  }
})
const filteredRows = computed(() =>
  rows.filter((row) => !filter.value || row.status === filter.value),
)
function reset() {
  Object.assign(form, defaults())
  editing.value = null
  error.value = ''
}
function submit() {
  error.value = ''
  message.value = ''
  try {
    const body = requestBody(form)
    if (
      rows.some(
        (row) =>
          row.id !== editing.value &&
          ['PENDING', 'APPROVED'].includes(row.status) &&
          row.workDate === body.workDate &&
          row.period === body.period &&
          row.requestType === body.requestType,
      )
    )
      throw new Error(
        'Đã có đơn chờ duyệt hoặc đã duyệt cùng ngày, buổi và loại yêu cầu. Bạn có thể sửa đơn đang chờ duyệt.',
      )
    const existing = rows.find((row) => row.id === editing.value)
    if (existing) Object.assign(existing, body)
    else
      rows.unshift({
        ...body,
        id: crypto.randomUUID(),
        status: 'PENDING',
        createdAt: new Date().toISOString(),
        reviewNote: '',
      })
    filter.value = ''
    message.value = existing
      ? 'Đã cập nhật đơn mô phỏng. Chưa gửi đến HR.'
      : 'Đã tạo đơn mô phỏng ở trạng thái chờ duyệt. Chưa gửi đến HR.'
    reset()
  } catch (cause) {
    error.value = cause.message
  }
}
function edit(row) {
  Object.assign(form, {
    requestType: row.requestType,
    workDate: row.workDate,
    period: row.period,
    expectedTime: row.expectedTime,
    reason: row.reason,
  })
  editing.value = row.id
  cancelId.value = null
  error.value = ''
  message.value = ''
  formElement.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
  reasonElement.value.focus({ preventScroll: true })
}
function cancel(row) {
  row.status = 'CANCELLED'
  cancelId.value = null
  if (editing.value === row.id) reset()
  message.value = 'Đã rút đơn trong bản mô phỏng. Bảng công thực tế không thay đổi.'
}
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
    <div class="attendance-demo-note">
      <AppIcon name="info" :size="20" />
      <div>
        <strong>Bản xem trước · Dữ liệu mô phỏng</strong>
        <p>
          Ca và đơn bên dưới là dữ liệu mẫu. Thao tác chưa gửi đến HR, chưa cập nhật bảng công; tải
          lại trang sẽ đặt lại dữ liệu.
        </p>
      </div>
    </div>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div class="attendance-request-layout">
      <form ref="formElement" class="panel create-form" @submit.prevent="submit">
        <div class="attendance-section-heading">
          <div>
            <h2>{{ editing ? 'Chỉnh sửa đơn xin phép' : 'Tạo đơn xin phép' }}</h2>
            <p class="muted small">Các trường bên dưới đều bắt buộc.</p>
          </div>
          <span class="attendance-badge">{{ editing ? 'Đang chỉnh sửa' : 'Đơn mới' }}</span>
        </div>
        <fieldset class="attendance-request-types">
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
            ><input id="request-date" v-model="form.workDate" type="date" :min="today()" required />
          </div>
          <div class="field">
            <label for="request-period">Buổi làm việc</label
            ><select id="request-period" v-model="form.period">
              <option value="MORNING">Buổi sáng · 08:00–12:00</option>
              <option value="AFTERNOON">Buổi chiều · 13:30–17:30</option>
            </select>
          </div>
        </div>
        <div class="attendance-fields">
          <div class="field">
            <label for="request-time">{{ timeTitle }}</label
            ><input
              id="request-time"
              v-model="form.expectedTime"
              type="time"
              required
              aria-describedby="request-time-help"
            />
            <p id="request-time-help" class="field-help">
              Chọn giờ nằm trong buổi làm việc {{ interval.start }}–{{ interval.end }}.
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
            rows="4"
            maxlength="1000"
            required
            placeholder="Mô tả lý do và kế hoạch bàn giao công việc (nếu có)…"
          ></textarea>
          <p class="field-help attendance-character-count">
            {{ form.reason.length }} / 1.000 ký tự
          </p>
        </div>
        <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
        <div class="button-row">
          <button class="button button-primary">
            <AppIcon name="check" :size="17" />{{
              editing ? 'Lưu thay đổi thử' : 'Gửi thử đơn'
            }}</button
          ><button class="button button-secondary" type="button" @click="reset">
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
            <dd>{{ previewShift.definition.name }}</dd>
          </div>
          <div>
            <dt>Buổi làm việc</dt>
            <dd>{{ interval.start }}–{{ interval.end }}</dd>
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
            >Thời lượng trên là thời gian xin phép, chưa phải kết quả tính công. Đơn mẫu được duyệt
            cũng không thay đổi giờ chấm công.</span
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
          <h2>Đơn xin phép của tôi</h2>
          <p class="muted small">
            {{ rows.filter((row) => row.status === 'PENDING').length }} đơn chờ duyệt · Dữ liệu mô
            phỏng
          </p>
        </div>
        <span class="attendance-badge">{{ filteredRows.length }} đơn</span>
      </div>
      <div class="attendance-table-tools">
        <div class="field">
          <label for="request-status">Trạng thái đơn</label
          ><select id="request-status" v-model="filter">
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
                <details class="attendance-request-detail">
                  <summary>Lý do & phản hồi</summary>
                  <p>{{ row.reason }}</p>
                  <p v-if="row.reviewNote"><strong>Phản hồi mẫu:</strong> {{ row.reviewNote }}</p>
                  <p v-else class="muted small">
                    {{
                      row.status === 'CANCELLED'
                        ? 'Đơn đã được rút trong bản mô phỏng.'
                        : 'Chưa có phản hồi.'
                    }}
                  </p>
                </details>
                <div v-if="row.status === 'PENDING'" class="button-row">
                  <button type="button" class="text-button" @click="edit(row)">Sửa đơn</button
                  ><button
                    type="button"
                    class="text-button attendance-cancel-link"
                    @click="cancelId = row.id"
                  >
                    Rút đơn
                  </button>
                </div>
                <div v-if="cancelId === row.id" class="attendance-cancel-confirm">
                  <p>Rút đơn này khỏi danh sách chờ duyệt?</p>
                  <div class="button-row">
                    <button type="button" class="button button-danger-outline" @click="cancel(row)">
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
    </section>
  </div>
</template>
