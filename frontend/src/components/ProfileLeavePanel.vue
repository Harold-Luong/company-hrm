<script setup>
import { computed, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import { dateLabel, leaveTypes, leaveStatuses, leavePeriods, today } from '@/leave/helpers.js'
import PageControls from '@/components/PageControls.vue'

const employeeId = computed(() => auth.state.user?.employeeId)
const year = ref(Number(today().slice(0, 4)))
const status = ref('')
const page = ref(0)
const balanceRetry = ref(0)
const requestsRetry = ref(0)
const balance = ref(null)
const result = ref(null)
const balanceLoading = ref(false)
const requestsLoading = ref(false)
const balanceError = ref('')
const requestsError = ref('')
const daysLabel = (value) => (value == null ? '—' : Number(value).toLocaleString('vi-VN'))

watch(
  [employeeId, year, balanceRetry],
  async ([id, selectedYear], _, onCleanup) => {
    let cancelled = false
    onCleanup(() => {
      cancelled = true
    })
    balance.value = null
    balanceError.value = ''
    balanceLoading.value = false
    if (!id) return
    if (!Number.isInteger(selectedYear) || selectedYear < 2000 || selectedYear > 9999) {
      balanceError.value = 'Chọn năm hợp lệ từ 2000 đến 9999.'
      return
    }
    balanceLoading.value = true
    try {
      const data = await leave.balance(selectedYear)
      if (!cancelled) balance.value = data
    } catch {
      if (!cancelled) balanceError.value = 'Chưa tải được số dư phép năm. Vui lòng thử lại.'
    } finally {
      if (!cancelled) balanceLoading.value = false
    }
  },
  { immediate: true },
)

watch(
  [employeeId, status, page, requestsRetry],
  async ([id, selectedStatus, selectedPage], _, onCleanup) => {
    let cancelled = false
    onCleanup(() => {
      cancelled = true
    })
    result.value = null
    requestsError.value = ''
    requestsLoading.value = false
    if (!id) return
    requestsLoading.value = true
    try {
      const data = await leave.list(false, selectedStatus, selectedPage, 5)
      if (!cancelled) result.value = data
    } catch {
      if (!cancelled) requestsError.value = 'Chưa tải được đơn nghỉ phép. Vui lòng thử lại.'
    } finally {
      if (!cancelled) requestsLoading.value = false
    }
  },
  { immediate: true },
)

function refresh() {
  balanceRetry.value++
  requestsRetry.value++
}
</script>

<template>
  <section class="profile-module profile-leave" aria-label="Thông tin nghỉ phép">
    <div class="profile-module-heading">
      <h3>Thông tin nghỉ phép</h3>
      <div v-if="employeeId" class="button-row">
        <button
          class="button button-secondary"
          :disabled="balanceLoading || requestsLoading"
          @click="refresh"
        >
          Tải lại
        </button>
        <RouterLink class="button button-primary" to="/leave">Tạo đơn xin nghỉ</RouterLink>
      </div>
    </div>
    <p v-if="!employeeId" class="muted" role="status">
      Tài khoản chưa được liên kết với hồ sơ nhân viên. Liên hệ bộ phận nhân sự để xem thông tin
      nghỉ phép.
    </p>
    <template v-else>
      <section aria-label="Số dư phép năm" :aria-busy="balanceLoading">
        <div class="profile-module-heading">
          <h4>Phép năm {{ year }}</h4>
          <div class="field">
            <label for="profile-leave-year">Năm phép</label>
            <input
              id="profile-leave-year"
              v-model.number="year"
              type="number"
              min="2000"
              max="9999"
              step="1"
              required
            />
          </div>
        </div>
        <p v-if="balanceLoading" class="muted" role="status">Đang tải số dư phép năm…</p>
        <div v-else-if="balanceError" class="alert alert-error" role="alert">
          <p>{{ balanceError }}</p>
          <button class="button button-secondary" @click="balanceRetry++">Thử lại</button>
        </div>
        <dl v-else-if="balance" class="profile-module-stats">
          <div>
            <dt>Được hưởng</dt>
            <dd>{{ daysLabel(balance.entitledDays) }} <small>ngày</small></dd>
          </div>
          <div>
            <dt>Đã dùng</dt>
            <dd>{{ daysLabel(balance.usedDays) }} <small>ngày</small></dd>
          </div>
          <div>
            <dt>Còn lại</dt>
            <dd>{{ daysLabel(balance.remainingDays) }} <small>ngày</small></dd>
          </div>
        </dl>
        <p class="muted small">
          Phép năm được trừ khi đơn được duyệt, bao gồm cả ngày nghỉ sắp tới. Đơn chờ duyệt và nghỉ
          không lương không trừ số dư phép năm.
        </p>
      </section>

      <section
        class="profile-leave-history"
        aria-label="Lịch sử nghỉ phép"
        :aria-busy="requestsLoading"
      >
        <div class="profile-module-heading">
          <div>
            <h4>Lịch sử nghỉ phép</h4>
            <p class="muted small">Đơn của bạn trong tất cả các năm, mới nhất trước.</p>
          </div>
          <div class="field">
            <label for="profile-leave-status">Trạng thái đơn</label>
            <select id="profile-leave-status" v-model="status" @change="page = 0">
              <option value="">Tất cả</option>
              <option v-for="(label, value) in leaveStatuses" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
        </div>
        <p v-if="requestsLoading" class="muted" role="status">Đang tải đơn nghỉ phép…</p>
        <div v-else-if="requestsError" class="alert alert-error" role="alert">
          <p>{{ requestsError }}</p>
          <button class="button button-secondary" @click="requestsRetry++">Thử lại</button>
        </div>
        <template v-else-if="result">
          <ul v-if="result.content.length" class="profile-module-requests">
            <li v-for="request in result.content" :key="request.id">
              <div>
                <strong>{{ leaveTypes[request.leaveType] || request.leaveType }}</strong>
                <p class="muted small">
                  {{ dateLabel(request.startDate) }} – {{ dateLabel(request.endDate) }} ·
                  {{ leavePeriods[request.period] || 'Cả ngày' }}
                  <template v-if="request.totalUnits != null">
                    · {{ daysLabel(request.totalUnits / 2) }} ngày</template
                  >
                </p>
              </div>
              <span
                class="status-chip"
                :class="{
                  active: request.status === 'APPROVED',
                  probation: request.status === 'PENDING',
                  terminated: request.status === 'REJECTED',
                  'profile-leave-cancelled': request.status === 'CANCELLED',
                }"
                >{{ leaveStatuses[request.status] || request.status }}</span
              >
              <RouterLink class="text-button" :to="`/leave/requests/${request.id}`"
                >Xem đơn và lịch sử</RouterLink
              >
            </li>
          </ul>
          <p v-else class="muted" role="status">
            {{ status ? 'Không có đơn nghỉ phép ở trạng thái này.' : 'Bạn chưa có đơn nghỉ phép.' }}
          </p>
          <PageControls
            v-if="result.totalPages > 1"
            :page="result.page"
            :pages="result.totalPages"
            :total="result.totalElements"
            :busy="requestsLoading"
            @change="page = $event"
          />
        </template>
      </section>
      <RouterLink class="text-button profile-module-more" to="/leave"
        >Quản lý nghỉ phép →</RouterLink
      >
    </template>
  </section>
</template>

<style scoped>
.profile-leave h4 {
  margin: 0;
  font-size: 15px;
}
.profile-leave-history {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.profile-leave-history .profile-module-heading p {
  margin: 4px 0 0;
}
.profile-leave-cancelled {
  background: #f1f3f5;
  color: var(--muted);
}
.profile-leave :deep(.pagination) {
  flex-wrap: wrap;
  gap: 12px;
}
</style>
