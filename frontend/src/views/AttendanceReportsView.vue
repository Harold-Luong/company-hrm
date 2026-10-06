<script setup>
import { onMounted, reactive, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { today, attendanceError } from '@/attendance/helpers.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import AttendanceTable from '@/components/AttendanceTable.vue'
import AttendanceStats from '@/components/AttendanceStats.vue'
import ReferencePicker from '@/components/ReferencePicker.vue'
const filters = reactive({
  from: `${today().slice(0, 7)}-01`,
  until: today(),
  employeeId: '',
  departmentId: '',
})
const rows = ref([]),
  busy = ref(false),
  error = ref(''),
  loaded = ref(null)
async function load() {
  busy.value = true
  error.value = ''
  rows.value = []
  loaded.value = null
  try {
    const request = { ...filters }
    rows.value = await attendance.report(request)
    loaded.value = request
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function download() {
  if (!loaded.value || busy.value) return
  busy.value = true
  error.value = ''
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
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function refresh(row) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const updated = await attendance.refresh(row)
    rows.value = rows.value.map((value) =>
      value.employeeId === updated.employeeId && value.workDate === updated.workDate
        ? updated
        : value,
    )
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
        <p class="eyebrow">QUẢN LÝ CHẤM CÔNG</p>
        <h1>Bảng công</h1>
        <p class="muted">Đối chiếu giờ thực tế, nghỉ phép và thời lượng tính công.</p>
      </div>
      <button
        class="button button-primary"
        :disabled="busy || !loaded || !rows.length"
        @click="download"
      >
        Xuất CSV
      </button>
    </div>
    <AttendanceNav />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <form class="panel create-form" @submit.prevent="load">
      <fieldset :disabled="busy">
        <div class="attendance-fields">
          <div class="field">
            <label for="report-from">Từ ngày</label
            ><input id="report-from" v-model="filters.from" type="date" :max="today()" required />
          </div>
          <div class="field">
            <label for="report-until">Đến ngày</label
            ><input
              id="report-until"
              v-model="filters.until"
              type="date"
              :min="filters.from"
              :max="today()"
              required
            />
          </div>
          <ReferencePicker
            v-model="filters.employeeId"
            resource="employees"
            label="Nhân viên (bỏ trống để xem tất cả)"
          /><ReferencePicker
            v-model="filters.departmentId"
            resource="departments"
            label="Phòng ban (không bắt buộc)"
          />
        </div>
        <button class="button button-secondary">{{ busy ? 'Đang tải…' : 'Xem bảng công' }}</button>
      </fieldset>
    </form>
    <AttendanceStats :rows="rows" :busy="busy" />
    <section class="panel create-form" :aria-busy="busy">
      <h2>Bảng công tạm tính</h2>
      <p v-if="loaded" class="muted">
        {{ loaded.from }} → {{ loaded.until }} · {{ rows.length }} dòng
      </p>
      <p class="info-strip">
        Chưa chốt kỳ. Với ngày đã chấm công, dùng “Cập nhật phép/lịch” khi có đơn được duyệt sau đó.
        Ngày thiếu giờ vào/ra chưa có kết quả công cuối cùng.
      </p>
      <AttendanceTable :rows="rows" management :busy="busy" :failed="!!error" @refresh="refresh" />
    </section>
  </div>
</template>
