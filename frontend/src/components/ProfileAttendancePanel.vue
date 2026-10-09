<script setup>
import { computed, ref, watch } from 'vue'
import { attendance } from '@/attendance/api.js'
import { attendanceError, minutesLabel, today } from '@/attendance/helpers.js'

const month = ref(today().slice(0, 7))
const retry = ref(0)
const rows = ref([])
const loading = ref(false)
const error = ref('')
const monthLabel = computed(() => month.value.split('-').reverse().join('/'))
const total = (values) =>
  values.some((value) => value != null)
    ? values.reduce((sum, value) => sum + Number(value ?? 0), 0)
    : null
const stats = computed(() => [
  ['Công thực tế', minutesLabel(total(rows.value.map((row) => row.workMinutesCounted)))],
  [
    'Ngày phép',
    total(rows.value.map((row) => row.leaveDays))?.toLocaleString('vi-VN') ?? 'Chưa xác định',
  ],
  ['Tăng ca', minutesLabel(total(rows.value.map((row) => row.overtime?.countedMinutes)))],
])
watch(
  [month, retry],
  async ([selected], _, onCleanup) => {
    let cancelled = false
    onCleanup(() => {
      cancelled = true
    })
    rows.value = []
    error.value = ''
    loading.value = false
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(selected) || selected > today().slice(0, 7)) {
      error.value = 'Chọn tháng hợp lệ, không sau tháng hiện tại.'
      return
    }
    loading.value = true
    const [year, monthNumber] = selected.split('-').map(Number)
    const end = new Date(Date.UTC(year, monthNumber, 0)).toISOString().slice(0, 10)
    try {
      const data = await attendance.mine(`${selected}-01`, end > today() ? today() : end)
      if (!cancelled) rows.value = data
    } catch (cause) {
      if (!cancelled) error.value = attendanceError(cause)
    } finally {
      if (!cancelled) loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="profile-module" aria-label="Tổng hợp chấm công" :aria-busy="loading">
    <div class="profile-module-heading">
      <h3>Chấm công tháng {{ monthLabel }}</h3>
      <div class="field">
        <label for="profile-attendance-month">Tháng chấm công</label>
        <input
          id="profile-attendance-month"
          v-model="month"
          type="month"
          :max="today().slice(0, 7)"
          required
        />
      </div>
    </div>
    <p v-if="loading" class="muted" role="status">Đang tải chấm công…</p>
    <div v-else-if="error" class="alert alert-error" role="alert">
      <p>{{ error }}</p>
      <button class="button button-secondary" @click="retry++">Thử lại</button>
    </div>
    <template v-else-if="rows.length">
      <dl class="profile-module-stats">
        <div v-for="[label, value] in stats" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <p class="muted small">
        Số liệu tạm tính từ bảng công: giờ công và tăng ca đã ghi nhận, ngày phép gồm phép năm và
        nghỉ không lương. Những ngày chưa xác định công chưa được cộng vào tổng.
      </p>
    </template>
    <p v-else class="muted">Chưa có dữ liệu chấm công trong tháng này.</p>
    <RouterLink class="text-button" to="/attendance">Xem bảng công chi tiết →</RouterLink>
  </section>
</template>
