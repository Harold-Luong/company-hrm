<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { attendance } from '@/attendance/api.js'
import { today, weekdays, shiftLabel, minutesLabel, attendanceError } from '@/attendance/helpers.js'
import { hrm, fullName } from '@/hrm/api.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import ReferencePicker from '@/components/ReferencePicker.vue'
import PageControls from '@/components/PageControls.vue'
const shifts = ref([]),
  busy = ref(false),
  error = ref(''),
  message = ref(''),
  preview = ref(null),
  selectedEmployee = ref('')
const selectedNames = reactive({}),
  history = ref({ content: [], page: 0, totalPages: 0, totalElements: 0 })
const form = reactive({
  shiftId: '',
  scope: 'COMPANY_DEFAULT',
  employeeIds: [],
  from: today(),
  until: '',
  weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  reason: '',
})
let applyKey = ''
const selectedShift = computed(() => shifts.value.find((s) => s.id === form.shiftId))
watch(
  form,
  () => {
    preview.value = null
    applyKey = ''
  },
  { deep: true },
)
async function load() {
  busy.value = true
  error.value = ''
  preview.value = null
  try {
    const loaded = []
    for (let page = 0, pages = 1; page < pages; page++) {
      const result = await attendance.shifts(page)
      loaded.push(...result.content)
      pages = result.totalPages
    }
    shifts.value = loaded.filter((s) => s.active)
    if (!shifts.value.some((s) => s.id === form.shiftId)) form.shiftId = shifts.value[0]?.id || ''
    history.value = await attendance.scheduleHistory()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function loadHistory(page) {
  busy.value = true
  error.value = ''
  try {
    history.value = await attendance.scheduleHistory(page)
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function addEmployee() {
  if (!selectedEmployee.value || form.employeeIds.includes(selectedEmployee.value)) return
  busy.value = true
  error.value = ''
  try {
    const person = await hrm.get('employees', selectedEmployee.value)
    selectedNames[person.id] = `${person.employeeCode} · ${fullName(person)}`
    form.employeeIds.push(person.id)
    selectedEmployee.value = ''
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
function body() {
  if (!selectedShift.value || !form.weekdays.length || !form.reason.trim())
    throw new Error('Chọn ca, ngày làm việc và nhập lý do áp dụng.')
  if (form.scope === 'SELECTED_EMPLOYEES' && !form.employeeIds.length)
    throw new Error('Chọn ít nhất một nhân viên.')
  return {
    ...form,
    employeeIds: form.scope === 'SELECTED_EMPLOYEES' ? [...form.employeeIds] : [],
    shiftVersion: selectedShift.value.version,
    until: form.until || null,
    weekdays: [...form.weekdays],
    reason: form.reason.trim(),
  }
}
async function showPreview() {
  busy.value = true
  error.value = ''
  message.value = ''
  preview.value = null
  try {
    const request = body()
    const result = await attendance.preview(request)
    preview.value = { ...result, request }
    applyKey = crypto.randomUUID()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function apply() {
  if (!preview.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await attendance.apply(preview.value.request, preview.value.scheduleRevision, applyKey)
    message.value = 'Đã áp dụng phân công theo phạm vi và ngày hiệu lực đã chọn.'
    preview.value = null
    history.value = await attendance.scheduleHistory()
  } catch (cause) {
    if ([409, 412, 428].includes(cause.status)) preview.value = null
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
function batchSummary(value) {
  try {
    const request = JSON.parse(value)
    return `${request.from} → ${request.until || 'Không giới hạn'} · ${request.reason}`
  } catch {
    return ''
  }
}
onMounted(load)
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">QUẢN LÝ CHẤM CÔNG</p>
        <h1>Phân công ca</h1>
        <p class="muted">
          Phân công ca có giờ bắt đầu và kết thúc cụ thể. Lịch mặc định lặp hằng tuần; lịch riêng có
          ngày hiệu lực.
        </p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="load">Tải lại</button>
    </div>
    <AttendanceNav />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form class="panel create-form" @submit.prevent="showPreview">
      <div class="attendance-section-heading">
        <div>
          <h2>Ca mặc định và lịch riêng</h2>
          <p class="muted small">Hoàn thành thông tin bên dưới để xem trước lịch áp dụng.</p>
        </div>
        <span class="attendance-badge">{{ preview ? 'Sẵn sàng xem trước' : 'Phân công mới' }}</span>
      </div>
      <fieldset :disabled="busy">
        <h3 class="attendance-step"><span>1</span> Chọn ca và phạm vi</h3>
        <div class="attendance-fields">
          <div class="field">
            <label for="schedule-shift">Ca làm việc</label
            ><select id="schedule-shift" v-model="form.shiftId" required>
              <option value="">Chọn ca</option>
              <option v-for="s in shifts" :key="s.id" :value="s.id">
                {{ s.definition.name }} · {{ shiftLabel(s.definition) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="schedule-scope">Phạm vi áp dụng</label
            ><select id="schedule-scope" v-model="form.scope">
              <option value="COMPANY_DEFAULT">Mặc định công ty, giữ lịch riêng</option>
              <option value="SELECTED_EMPLOYEES">Một / vài nhân viên</option>
              <option value="ALL_EMPLOYEES">Toàn bộ nhân viên, thay cả lịch riêng</option>
            </select>
          </div>
        </div>
        <p v-if="form.scope === 'COMPANY_DEFAULT'" class="info-strip">
          Ca hành chính: chọn thứ Hai–thứ Sáu và để trống ngày kết thúc để tự lặp hằng tuần. Nhân
          viên mới đủ điều kiện chấm công tự dùng lịch này nếu chưa có lịch riêng.
        </p>
        <p v-if="form.scope === 'SELECTED_EMPLOYEES'" class="info-strip">
          Lịch riêng ưu tiên hơn mặc định công ty. Khi hết hiệu lực, nhân viên tự trở lại lịch mặc
          định.
        </p>
        <p v-if="selectedShift" class="muted">
          {{ minutesLabel(selectedShift.requiredMinutes) }} mỗi ngày được phân công.
        </p>
        <div v-if="form.scope === 'SELECTED_EMPLOYEES'">
          <ReferencePicker
            v-model="selectedEmployee"
            resource="employees"
            label="Chọn nhân viên"
          /><button
            class="button button-secondary"
            type="button"
            :disabled="!selectedEmployee || form.employeeIds.length >= 100"
            @click="addEmployee"
          >
            Thêm nhân viên
          </button>
          <div>
            <span v-for="id in form.employeeIds" :key="id" class="attendance-chip"
              >{{ selectedNames[id] || id
              }}<button
                class="text-button"
                type="button"
                :aria-label="`Bỏ ${selectedNames[id] || id}`"
                @click="form.employeeIds = form.employeeIds.filter((value) => value !== id)"
              >
                ×
              </button></span
            >
          </div>
        </div>
        <p v-if="form.scope === 'ALL_EMPLOYEES'" class="info-strip">
          Lịch riêng trùng phạm vi ngày đã chọn sẽ được thay thế. Mặc định công ty cũng được cập
          nhật cho nhân viên mới.
        </p>
        <h3 class="attendance-step"><span>2</span> Thời gian áp dụng</h3>
        <div class="attendance-fields">
          <div class="field">
            <label for="schedule-from">Ngày bắt đầu áp dụng</label
            ><input id="schedule-from" v-model="form.from" type="date" :min="today()" required />
          </div>
          <div class="field">
            <label for="schedule-until">Ngày kết thúc (không bắt buộc)</label
            ><input id="schedule-until" v-model="form.until" type="date" :min="form.from" />
          </div>
        </div>
        <div class="attendance-days" role="group" aria-label="Ngày làm việc">
          <label v-for="[value, label] in weekdays" :key="value"
            ><input v-model="form.weekdays" type="checkbox" :value="value" />{{ label }}</label
          >
        </div>
        <p class="muted small">
          Chỉ các thứ được chọn thay đổi. Lịch của các thứ khác được giữ nguyên.
        </p>
        <h3 class="attendance-step"><span>3</span> Ghi chú và xác nhận</h3>
        <div class="field">
          <label for="schedule-reason">Lý do áp dụng</label
          ><textarea
            id="schedule-reason"
            v-model="form.reason"
            required
            maxlength="1000"
            rows="3"
            placeholder="Ví dụ: Phân công ca hành chính cho tháng mới…"
          ></textarea>
        </div>
        <button class="button button-primary" :disabled="!shifts.length">
          {{ busy ? 'Đang xử lý…' : 'Xem trước phân công' }}
        </button>
        <RouterLink v-if="!shifts.length" to="/attendance/shifts">Tạo ca làm việc</RouterLink>
      </fieldset>
      <section v-if="preview" class="attendance-preview" aria-label="Kết quả xem trước">
        <h2>Xem trước phân công</h2>
        <p>
          {{ preview.shift.definition.name }} · {{ shiftLabel(preview.shift.definition) }} ·
          {{ minutesLabel(preview.shift.requiredMinutes) }}/ngày
        </p>
        <p>
          {{ preview.request.from }} → {{ preview.request.until || 'Không giới hạn ngày kết thúc' }}
        </p>
        <p>{{ preview.affectedOverrides.length }} lịch nhân viên riêng bị ảnh hưởng.</p>
        <p
          v-if="
            preview.recordedDayConflicts ||
            preview.approvedLeaveConflicts ||
            preview.approvedRequestConflicts ||
            preview.approvedOvertimeConflicts
          "
          class="alert alert-error"
          role="alert"
        >
          Có {{ preview.recordedDayConflicts }} ngày đã chấm công và
          {{ preview.approvedLeaveConflicts }} đơn phép đã duyệt,
          {{ preview.approvedRequestConflicts || 0 }} đơn đi trễ/về sớm và
          {{ preview.approvedOvertimeConflicts || 0 }} đơn OT đã duyệt cần đối soát. Chọn ngày khác
          trước khi áp dụng.
        </p>
        <button
          type="button"
          class="button button-primary"
          :disabled="
            busy ||
            preview.recordedDayConflicts > 0 ||
            preview.approvedLeaveConflicts > 0 ||
            preview.approvedRequestConflicts > 0 ||
            preview.approvedOvertimeConflicts > 0
          "
          @click="apply"
        >
          Áp dụng phân công
        </button>
      </section>
    </form>
    <section class="panel create-form">
      <h2>Lịch sử áp dụng</h2>
      <ul class="attendance-history">
        <li v-for="item in history.content" :key="item.id">
          {{ batchSummary(item.requestBody)
          }}<small> · {{ new Date(item.occurredAt).toLocaleString('vi-VN') }}</small>
        </li>
      </ul>
      <div v-if="!history.content.length" class="attendance-empty">
        <strong>{{ busy ? 'Đang tải lịch sử…' : 'Chưa có phân công' }}</strong
        ><span>Lịch sử sẽ hiển thị sau khi áp dụng phân công thành công.</span>
      </div>
      <PageControls
        :page="history.page"
        :pages="history.totalPages"
        :total="history.totalElements"
        :busy="busy"
        @change="loadHistory"
      />
    </section>
  </div>
</template>
