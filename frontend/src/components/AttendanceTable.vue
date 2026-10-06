<script setup>
import { computed, ref, watch } from 'vue'
import { statuses, minutesLabel, secondsLabel, timeLabel } from '@/attendance/helpers.js'
import AppIcon from './AppIcon.vue'
import PageControls from './PageControls.vue'
const props = defineProps({
  rows: { type: Array, default: () => [] },
  management: Boolean,
  busy: Boolean,
  failed: Boolean,
})
defineEmits(['refresh'])
const status = ref('')
const search = ref('')
const page = ref(0)
function clearFilters() {
  status.value = ''
  search.value = ''
}
const filtered = computed(() =>
  props.rows
    .filter(
      (row) =>
        (!status.value || row.status === status.value) &&
        (!props.management ||
          `${row.employeeName} ${row.employeeCode}`
            .toLocaleLowerCase('vi')
            .includes(search.value.trim().toLocaleLowerCase('vi'))),
    )
    .toSorted((a, b) => b.workDate.localeCompare(a.workDate)),
)
const visible = computed(() => filtered.value.slice(page.value * 10, page.value * 10 + 10))
watch([status, search, () => props.rows], () => {
  page.value = 0
})
function dateLabel(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T12:00:00`))
}
</script>
<template>
  <div class="attendance-table-tools">
    <div v-if="management" class="field attendance-search">
      <label for="attendance-search">Tìm nhân viên trong bảng</label>
      <input
        id="attendance-search"
        v-model="search"
        type="search"
        placeholder="Tên hoặc mã nhân viên…"
        :disabled="busy"
      />
    </div>
    <div class="field">
      <label for="attendance-status">Trạng thái công</label>
      <select id="attendance-status" v-model="status" :disabled="busy">
        <option value="">Tất cả trạng thái</option>
        <option v-for="(label, value) in statuses" :key="value" :value="value">{{ label }}</option>
      </select>
    </div>
    <span class="muted small">{{ filtered.length }} bản ghi phù hợp</span>
  </div>
  <div
    class="table-wrap attendance-table"
    tabindex="0"
    aria-label="Chi tiết công theo ngày"
    :aria-busy="busy"
  >
    <table>
      <thead>
        <tr>
          <th v-if="management">Nhân viên</th>
          <th>Ngày</th>
          <th>Vào / Ra</th>
          <th>Giờ thực tế</th>
          <th>Giờ tính công</th>
          <th>Phép</th>
          <th>Trễ / Sớm quy đổi</th>
          <th>Trạng thái</th>
          <th v-if="management">Đối soát</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in busy ? [] : visible" :key="`${row.employeeId}-${row.workDate}`">
          <td v-if="management">
            {{ row.employeeName }}<small>{{ row.employeeCode }}</small>
          </td>
          <td class="attendance-date">{{ dateLabel(row.workDate) }}</td>
          <td>
            {{ timeLabel(row.checkIn) }}<small>{{ timeLabel(row.checkOut) }}</small>
          </td>
          <td>{{ secondsLabel(row.workedActualSeconds) }}</td>
          <td>{{ minutesLabel(row.workMinutesCounted) }}</td>
          <td>
            {{ row.leaveDays }} ngày<small v-if="row.annualLeaveMinutes"
              >Phép năm {{ minutesLabel(row.annualLeaveMinutes) }}</small
            ><small v-if="row.unpaidLeaveMinutes"
              >Không lương {{ minutesLabel(row.unpaidLeaveMinutes) }}</small
            >
          </td>
          <td>{{ row.roundedLateMinutes ?? '—' }} / {{ row.roundedEarlyMinutes ?? '—' }} phút</td>
          <td>
            <span class="attendance-badge" :class="`status-${row.status.toLowerCase()}`">{{
              statuses[row.status] || row.status
            }}</span
            ><small v-if="row.pendingLeaveIds?.length"
              >{{ row.pendingLeaveIds.length }} đơn chờ duyệt</small
            >
          </td>
          <td v-if="management">
            <button
              v-if="row.recordVersion != null"
              type="button"
              class="text-button"
              :disabled="busy"
              @click="$emit('refresh', row)"
            >
              Cập nhật phép/lịch</button
            ><small v-if="row.sourceObservedAt"
              >Nguồn lúc
              {{
                new Date(row.sourceObservedAt).toLocaleString('vi-VN', {
                  timeZone: 'Asia/Ho_Chi_Minh',
                })
              }}</small
            >
          </td>
        </tr>
        <tr v-if="busy || !filtered.length">
          <td :colspan="management ? 9 : 7">
            <div class="attendance-empty">
              <AppIcon :name="busy ? 'clock' : failed ? 'info' : 'calendar'" :size="30" />
              <strong>{{
                busy
                  ? 'Đang tải bảng công…'
                  : failed
                    ? 'Chưa tải được bảng công'
                    : 'Không có bản ghi phù hợp'
              }}</strong>
              <span>{{
                busy
                  ? 'Vui lòng chờ trong giây lát.'
                  : failed
                    ? 'Dùng nút tải lại để thử kết nối lại.'
                    : 'Thử đổi khoảng ngày hoặc bộ lọc trạng thái để xem dữ liệu.'
              }}</span>
              <button v-if="!busy && (status || search)" class="text-button" @click="clearFilters">
                Xóa bộ lọc bảng
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <PageControls
    v-if="filtered.length"
    :page="page"
    :pages="Math.ceil(filtered.length / 10)"
    :total="filtered.length"
    :busy="busy"
    @change="page = $event"
  />
</template>
