<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { leave } from '@/leave/api.js'
import { dateLabel, leaveTypes, leaveStatuses, leavePeriods, today } from '@/leave/helpers.js'

const year = Number(today().slice(0, 4))
const balance = ref(null)
const requests = ref([])
const loading = ref(false)
const balanceError = ref('')
const requestsError = ref('')
let generation = 0
onBeforeUnmount(() => generation++)
async function load() {
  const current = ++generation
  loading.value = true
  balanceError.value = ''
  requestsError.value = ''
  balance.value = null
  requests.value = []
  const results = await Promise.allSettled([leave.balance(year), leave.list(false)])
  if (current !== generation) return
  if (results[0].status === 'fulfilled') balance.value = results[0].value
  else balanceError.value = 'Chưa tải được số dư phép năm. Vui lòng thử lại.'
  if (results[1].status === 'fulfilled') requests.value = results[1].value.content.slice(0, 5)
  else requestsError.value = 'Chưa tải được đơn nghỉ phép. Vui lòng thử lại.'
  loading.value = false
}
onMounted(load)
</script>

<template>
  <section class="profile-module" aria-label="Thông tin nghỉ phép" :aria-busy="loading">
    <div class="profile-module-heading"><h3>Thông tin nghỉ phép</h3></div>
    <p v-if="loading" class="muted" role="status">Đang tải thông tin nghỉ phép…</p>
    <template v-else>
      <p v-if="balanceError" class="alert alert-error" role="alert">{{ balanceError }}</p>
      <template v-if="balance">
        <p class="muted">Phép năm {{ balance.year }}</p>
        <dl class="profile-module-stats">
          <div>
            <dt>Được hưởng</dt>
            <dd>{{ balance.entitledDays }} <small>ngày</small></dd>
          </div>
          <div>
            <dt>Đã dùng</dt>
            <dd>{{ balance.usedDays }} <small>ngày</small></dd>
          </div>
          <div>
            <dt>Còn lại</dt>
            <dd>{{ balance.remainingDays }} <small>ngày</small></dd>
          </div>
        </dl>
      </template>
      <h4 class="profile-module-list-title">Đơn nghỉ gần đây</h4>
      <p v-if="requestsError" class="alert alert-error" role="alert">{{ requestsError }}</p>
      <ul v-else-if="requests.length" class="profile-module-requests">
        <li v-for="request in requests" :key="request.id">
          <div>
            <strong>{{ leaveTypes[request.leaveType] || request.leaveType }}</strong>
            <p class="muted small">
              {{ dateLabel(request.startDate) }} – {{ dateLabel(request.endDate) }} ·
              {{ leavePeriods[request.period] || 'Cả ngày' }}
            </p>
          </div>
          <span
            class="status-chip"
            :class="{
              active: request.status === 'APPROVED',
              probation: request.status === 'PENDING',
              terminated: request.status === 'REJECTED',
            }"
            >{{ leaveStatuses[request.status] || request.status }}</span
          >
          <RouterLink class="text-button" :to="`/leave/requests/${request.id}`"
            >Xem đơn và lịch sử</RouterLink
          >
        </li>
      </ul>
      <p v-else class="muted">Bạn chưa có đơn nghỉ phép.</p>
      <button v-if="balanceError || requestsError" class="button button-secondary" @click="load">
        Thử lại
      </button>
    </template>
    <RouterLink class="text-button profile-module-more" to="/leave">Quản lý nghỉ phép →</RouterLink>
  </section>
</template>
