<script setup>
import { computed, ref, watch } from 'vue'
import { minutesLabel } from '@/attendance/helpers.js'
import AppIcon from './AppIcon.vue'
import PageControls from './PageControls.vue'
const props = defineProps({
  employees: { type: Array, required: true },
  busy: Boolean,
  failed: Boolean,
})
const emit = defineEmits(['select', 'retry'])
const search = ref('')
const scope = ref('all')
const sort = ref('review')
const page = ref(0)
const heading = ref(null)
const employeeButtons = new Map()
const filtered = computed(() =>
  props.employees
    .filter((person) => {
      const matches = `${person.employeeCode} ${person.employeeName}`
        .toLocaleLowerCase('vi')
        .includes(search.value.trim().toLocaleLowerCase('vi'))
      return (
        matches &&
        (scope.value === 'all' ||
          (scope.value === 'review' && person.reviewDays > 0) ||
          (scope.value === 'incomplete' && person.incompleteDays > 0) ||
          (scope.value === 'late' && person.lateEarlyDays > 0))
      )
    })
    .toSorted(
      (a, b) =>
        (sort.value === 'review' ? b.reviewDays - a.reviewDays : 0) ||
        a.employeeName.localeCompare(b.employeeName, 'vi') ||
        a.employeeCode.localeCompare(b.employeeCode, 'vi'),
    ),
)
const visible = computed(() => filtered.value.slice(page.value * 10, page.value * 10 + 10))
watch([search, scope, sort], () => {
  page.value = 0
})
watch(
  () => props.employees,
  () => {
    page.value = Math.min(page.value, Math.max(0, Math.ceil(filtered.value.length / 10) - 1))
  },
)
function clearFilters() {
  search.value = ''
  scope.value = 'all'
}
function showReview() {
  scope.value = 'review'
  heading.value?.focus()
}
function focusEmployee(id) {
  ;(employeeButtons.get(id) ?? heading.value)?.focus()
}
defineExpose({ showReview, focusEmployee })
</script>
<template>
  <section class="panel report-summary" :aria-busy="busy" aria-labelledby="report-summary-title">
    <div class="attendance-section-heading">
      <div>
        <h2 id="report-summary-title" ref="heading" tabindex="-1">Bảng công tạm tính</h2>
        <p class="muted">Mỗi nhân viên một dòng. Mở chi tiết để đối soát từng ngày.</p>
      </div>
      <span class="report-tag">{{ employees.length }} nhân viên</span>
    </div>
    <div class="report-toolbar">
      <div class="field">
        <label for="report-search">Tìm nhân viên trong bảng</label>
        <input
          id="report-search"
          v-model="search"
          type="search"
          placeholder="Tên hoặc mã nhân viên…"
          :disabled="busy"
        />
      </div>
      <div class="field">
        <label for="report-scope">Lọc nhân viên</label>
        <select id="report-scope" v-model="scope" :disabled="busy">
          <option value="all">Tất cả nhân viên</option>
          <option value="review">Cần kiểm tra</option>
          <option value="incomplete">Công chưa đầy đủ</option>
          <option value="late">Có đi trễ / về sớm</option>
        </select>
      </div>
      <div class="field">
        <label for="report-sort">Sắp xếp</label>
        <select id="report-sort" v-model="sort" :disabled="busy">
          <option value="review">Cần kiểm tra trước</option>
          <option value="name">Tên nhân viên A–Z</option>
        </select>
      </div>
    </div>
    <div class="report-table-wrap" tabindex="0" aria-label="Tổng hợp công theo nhân viên">
      <table class="report-table">
        <thead>
          <tr>
            <th scope="col">Nhân viên</th>
            <th scope="col">Giờ tính công</th>
            <th scope="col">Phép / Không lương</th>
            <th scope="col" title="Phút đã làm tròn, gồm cả phần đã xin phép">Trễ / Sớm</th>
            <th scope="col">Tăng ca</th>
            <th scope="col">Kiểm tra</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="person in busy ? [] : visible" :key="person.employeeId">
            <td class="report-person" data-label="Nhân viên">
              <button
                :ref="
                  (el) =>
                    el
                      ? employeeButtons.set(person.employeeId, el)
                      : employeeButtons.delete(person.employeeId)
                "
                class="text-button report-detail-button"
                :aria-label="`Xem chi tiết ${person.employeeName}`"
                @click="emit('select', person.employeeId)"
              >
                <strong>{{ person.employeeName }}</strong> <AppIcon name="arrow" :size="14" />
              </button>
              <small>{{ person.employeeCode }} · {{ person.days.length }} ngày trong kỳ</small>
            </td>
            <td data-label="Giờ tính công">
              <strong>{{
                person.workMinutes == null ? '—' : minutesLabel(person.workMinutes)
              }}</strong>
              <small v-if="person.incompleteDays" class="report-warning"
                >Chưa đủ · {{ person.incompleteDays }} ngày</small
              >
              <small v-else-if="person.workMinutes != null">Đã tính các ngày có ca</small>
              <small v-if="person.noScheduleDays"
                >{{ person.noScheduleDays }} ngày không có ca</small
              >
            </td>
            <td data-label="Phép / Không lương">
              <span>{{ minutesLabel(person.annualMinutes) }} phép năm</span>
              <small>{{ minutesLabel(person.unpaidMinutes) }} không lương</small>
            </td>
            <td data-label="Trễ / Sớm">
              <span
                >{{ person.lateMinutes == null ? '—' : `${person.lateMinutes}p` }} /
                {{ person.earlyMinutes == null ? '—' : `${person.earlyMinutes}p` }}</span
              >
              <small>{{ person.lateEarlyDays }} ngày đã xác định</small>
            </td>
            <td data-label="Tăng ca">
              <strong>{{ minutesLabel(person.otCountedMinutes) }}</strong>
              <small>Duyệt {{ minutesLabel(person.otApprovedMinutes) }}</small>
            </td>
            <td data-label="Kiểm tra">
              <span v-if="person.reviewDays" class="report-tag report-tag-warning"
                >{{ person.reviewDays }} ngày cần kiểm tra</span
              >
              <span v-else class="report-tag">Chưa có cảnh báo</span>
              <small v-if="person.issues.record"
                >{{ person.issues.record }} ngày thiếu / sai giờ</small
              >
              <small v-if="person.issues.conflict"
                >{{ person.issues.conflict }} ngày xung đột</small
              >
              <small v-if="person.pendingRequests"
                >{{ person.pendingRequests }} đơn chờ duyệt</small
              >
              <small v-if="person.ongoingDays">{{ person.ongoingDays }} ca chưa kết thúc</small>
            </td>
          </tr>
          <tr v-if="busy || !filtered.length" class="report-empty-row">
            <td colspan="6">
              <div class="attendance-empty" role="status">
                <AppIcon :name="busy ? 'clock' : 'info'" :size="28" />
                <strong>{{
                  busy
                    ? 'Đang tải bảng công…'
                    : failed
                      ? 'Chưa tải được bảng công'
                      : 'Không có nhân viên phù hợp'
                }}</strong>
                <span>{{
                  failed
                    ? 'Thử tải lại khi dịch vụ sẵn sàng.'
                    : 'Chọn phạm vi báo cáo hoặc thay đổi bộ lọc để xem dữ liệu.'
                }}</span>
                <button v-if="failed && !busy" class="text-button" @click="emit('retry')">
                  Tải lại bảng công
                </button>
                <button
                  v-else-if="!busy && (search || scope !== 'all')"
                  class="text-button"
                  @click="clearFilters"
                >
                  Xóa bộ lọc bảng
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="filtered.length" class="muted small report-count" role="status">
      Hiển thị {{ filtered.length }} / {{ employees.length }} nhân viên · Tổng quan phía trên tính
      trên toàn bộ kỳ đã tải.
    </p>
    <PageControls
      v-if="filtered.length"
      :page="page"
      :pages="Math.ceil(filtered.length / 10)"
      :total="filtered.length"
      :busy="busy"
      @change="page = $event"
    />
  </section>
</template>
