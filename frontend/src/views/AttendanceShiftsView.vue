<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { attendanceError, shiftBody, shiftLabel, minutesLabel } from '@/attendance/helpers.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import PageControls from '@/components/PageControls.vue'
const result = ref({ content: [], page: 0, totalPages: 0, totalElements: 0 })
const busy = ref(false),
  error = ref(''),
  message = ref(''),
  stale = ref(false)
const editing = ref(null),
  history = ref([])
const defaults = () => ({
  name: '',
  overnight: false,
  intervals: [
    { period: 'MORNING', start: '08:00', end: '12:00' },
    { period: 'AFTERNOON', start: '13:30', end: '17:30' },
  ],
  checkInFrom: '06:00',
  checkOutUntil: '22:00',
})
const form = reactive(defaults())
const duration = computed(() =>
  form.intervals.reduce((sum, interval) => {
    const minutes = (time) => {
      const [h, m] = time.split(':').map(Number)
      return h * 60 + m
    }
    return (
      sum +
      Math.max(0, minutes(interval.end) - minutes(interval.start) + (form.overnight ? 1440 : 0))
    )
  }, 0),
)
function reset() {
  editing.value = null
  history.value = []
  stale.value = false
  Object.assign(form, defaults())
}
async function load(page = result.value.page) {
  busy.value = true
  error.value = ''
  try {
    result.value = await attendance.shifts(page)
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function edit(id) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const [shift, revisions] = await Promise.all([
      attendance.shift(id),
      attendance.shiftHistory(id),
    ])
    editing.value = shift
    Object.assign(form, structuredClone(shift.definition))
    history.value = revisions.content
    stale.value = false
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function save() {
  error.value = ''
  message.value = ''
  try {
    const body = shiftBody(form)
    busy.value = true
    await attendance.saveShift(editing.value?.id, body, editing.value?.version)
    reset()
    await load(0)
    message.value = 'Đã lưu ca. Vào Phân công ca để chọn nhân viên và ngày áp dụng.'
  } catch (cause) {
    stale.value = [412, 428].includes(cause.status)
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function archive(row) {
  if (
    !window.confirm(
      `Ngừng dùng ca “${row.definition.name}” cho các phân công mới? Lịch đã gán vẫn được giữ.`,
    )
  )
    return
  busy.value = true
  error.value = ''
  try {
    await attendance.archiveShift(row.id, row.version)
    if (editing.value?.id === row.id) reset()
    await load()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
function addInterval() {
  form.intervals.push({
    period: form.intervals.some((i) => i.period === 'MORNING') ? 'AFTERNOON' : 'MORNING',
    start: '13:30',
    end: '17:30',
  })
}
onMounted(() => load(0))
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">QUẢN LÝ CHẤM CÔNG</p>
        <h1>Ca làm việc</h1>
        <p class="muted">Tạo ca cố định theo giờ làm của công ty hoặc lịch part-time.</p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="load()">
        Tải lại danh sách
      </button>
    </div>
    <AttendanceNav />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form class="panel create-form" @submit.prevent="save">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ editing ? 'Chỉnh sửa ca' : 'Tạo ca làm việc' }}</h2>
          <p class="muted small">Thiết lập khung giờ và khoảng cho phép chấm công.</p>
        </div>
        <span class="attendance-badge">{{ editing ? 'Đang chỉnh sửa' : 'Ca mới' }}</span>
      </div>
      <fieldset :disabled="busy || stale || editing?.active === false">
        <div class="field">
          <label for="shift-name">Tên ca</label
          ><input
            id="shift-name"
            v-model="form.name"
            maxlength="100"
            placeholder="Ví dụ: Ca hành chính, Ca sáng…"
            required
          />
        </div>
        <label
          ><input v-model="form.overnight" type="checkbox" /> Ca qua đêm (kết thúc vào ngày hôm
          sau)</label
        >
        <p v-if="form.overnight" class="muted">
          Dùng một khoảng giờ, ví dụ 22:00–06:00. Ngày công là ngày bắt đầu ca.
        </p>
        <h3 class="attendance-form-title">Khung giờ làm việc</h3>
        <div v-for="(interval, index) in form.intervals" :key="index" class="attendance-interval">
          <div class="field">
            <label :for="`period-${index}`">Buổi {{ index + 1 }}</label
            ><select :id="`period-${index}`" v-model="interval.period">
              <option value="MORNING">Sáng</option>
              <option value="AFTERNOON">{{ form.overnight ? 'Ca đêm' : 'Chiều' }}</option>
            </select>
          </div>
          <div class="field">
            <label :for="`start-${index}`">Bắt đầu {{ index + 1 }}</label
            ><input :id="`start-${index}`" v-model="interval.start" type="time" required />
          </div>
          <div class="field">
            <label :for="`end-${index}`">Kết thúc {{ index + 1 }}</label
            ><input :id="`end-${index}`" v-model="interval.end" type="time" required />
          </div>
          <button
            class="button button-secondary"
            type="button"
            :disabled="form.intervals.length === 1"
            @click="form.intervals.splice(index, 1)"
          >
            Bỏ khoảng {{ index + 1 }}
          </button>
        </div>
        <button
          v-if="form.intervals.length < 2"
          class="text-button"
          type="button"
          @click="addInterval"
        >
          Thêm khoảng làm việc
        </button>
        <div class="attendance-duration">
          <span>Tổng thời lượng mỗi ngày</span
          ><strong>{{ Number.isFinite(duration) ? minutesLabel(duration) : '—' }}</strong
          ><small>Không bao gồm khoảng nghỉ giữa các buổi</small>
        </div>
        <div class="attendance-fields">
          <div class="field">
            <label for="window-from">Cho phép chấm công từ</label
            ><input id="window-from" v-model="form.checkInFrom" type="time" required />
          </div>
          <div class="field">
            <label for="window-until">Cho phép chấm công đến</label
            ><input id="window-until" v-model="form.checkOutUntil" type="time" required />
          </div>
        </div>
        <p class="muted">
          Các khoảng trống giữa giờ làm không tính công. Mọi giờ hiển thị theo giờ Việt Nam.
        </p>
        <div class="button-row">
          <button class="button button-primary">{{ busy ? 'Đang lưu…' : 'Lưu ca' }}</button>
        </div>
      </fieldset>
      <button
        v-if="editing"
        class="button button-secondary"
        type="button"
        :disabled="busy"
        @click="reset"
      >
        Tạo ca khác
      </button>
      <button
        v-if="stale && editing"
        class="button button-secondary"
        type="button"
        :disabled="busy"
        @click="edit(editing.id)"
      >
        Tải lại ca đang sửa
      </button>
      <p v-if="editing" class="muted small">
        Lưu sửa ca tạo phiên bản mới. Lịch đã phân công chỉ đổi khi bạn áp dụng phiên bản mới.
      </p>
      <details v-if="history.length">
        <summary>Lịch sử ca ({{ history.length }} phiên bản gần nhất)</summary>
        <ul>
          <li v-for="item in history" :key="item.id">
            Phiên bản {{ item.shiftVersion }} ·
            {{ new Date(item.occurredAt).toLocaleString('vi-VN') }} ·
            {{ item.active ? 'Hoạt động' : 'Đã ngừng' }}
          </li>
        </ul>
      </details>
    </form>
    <section class="panel create-form">
      <h2>Danh sách ca</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Tên ca</th>
              <th>Giờ làm</th>
              <th>Thời lượng</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in result.content" :key="row.id">
              <td>{{ row.definition.name }}</td>
              <td>{{ shiftLabel(row.definition) }}</td>
              <td>{{ minutesLabel(row.requiredMinutes) }}</td>
              <td>
                <span class="attendance-badge" :class="row.active ? 'status-open' : ''">{{
                  row.active ? 'Hoạt động' : 'Đã ngừng'
                }}</span>
              </td>
              <td>
                <div class="button-row">
                  <button class="text-button" :disabled="busy" @click="edit(row.id)">
                    Xem / Sửa</button
                  ><button
                    v-if="row.active"
                    class="text-button"
                    :disabled="busy"
                    @click="archive(row)"
                  >
                    Ngừng dùng
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!result.content.length">
              <td colspan="5">
                <div class="attendance-empty">
                  <strong>{{
                    busy
                      ? 'Đang tải danh sách ca…'
                      : error
                        ? 'Chưa tải được danh sách ca'
                        : 'Chưa có ca làm việc'
                  }}</strong
                  ><span>{{
                    error
                      ? 'Tải lại danh sách để thử lại.'
                      : 'Ca làm việc được tạo sẽ xuất hiện tại đây.'
                  }}</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls
        :page="result.page"
        :pages="result.totalPages"
        :total="result.totalElements"
        :busy="busy"
        @change="load"
      />
    </section>
  </div>
</template>
