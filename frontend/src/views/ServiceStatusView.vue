<script setup>
import { onMounted, ref } from 'vue'
import { serviceError } from '@/auth/api.js'
import { auth } from '@/auth/session.js'
import { getCalendarHealth } from '@/calendar/api.js'
const busy = ref(false)
const checkedAt = ref('')
const services = ref([
  {
    name: 'Xác thực',
    request: () => auth.request('/api/v1/auth/health-check'),
    requiresDatabaseStatus: true,
    status: '',
    error: '',
  },
  {
    name: 'Nhân sự',
    request: () => auth.request('/api/v1/employees/health-check'),
    requiresDatabaseStatus: true,
    status: '',
    error: '',
  },
  { name: 'Lịch nghỉ & sự kiện', request: getCalendarHealth, status: '', error: '' },
  {
    name: 'Chấm công',
    request: () => auth.request('/api/v1/attendance/health-check'),
    requiresDatabaseStatus: true,
    status: '',
    error: '',
  },
  {
    name: 'Nghỉ phép',
    request: () => auth.request('/api/v1/leave/health-check'),
    status: '',
    error: '',
  },
])
async function check() {
  if (busy.value) return
  busy.value = true
  await Promise.all(
    services.value.map(async (service) => {
      service.error = ''
      service.status = ''
      try {
        const data = await service.request()
        service.status =
          data.status === 'UP' && (!service.requiresDatabaseStatus || data.database === 'UP')
            ? 'Hoạt động bình thường'
            : 'Dịch vụ chưa sẵn sàng'
      } catch (cause) {
        service.error = serviceError(cause)
      }
    }),
  )
  checkedAt.value = new Date().toLocaleString('vi-VN')
  busy.value = false
}
onMounted(check)
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">HỆ THỐNG</p>
        <h1>Kết nối dịch vụ</h1>
        <p class="muted">Kiểm tra khả năng phản hồi và kết nối cơ sở dữ liệu.</p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="check">
        {{ busy ? 'Đang kiểm tra…' : 'Kiểm tra lại' }}
      </button>
    </div>
    <div class="quick-grid" :aria-busy="busy">
      <section v-for="service in services" :key="service.name" class="panel create-form">
        <h2>{{ service.name }}</h2>
        <p v-if="service.error" class="alert alert-error" role="alert">{{ service.error }}</p>
        <p v-else role="status">{{ service.status || 'Đang kiểm tra…' }}</p>
      </section>
    </div>
    <p v-if="checkedAt" class="muted small section-block">Kiểm tra lúc {{ checkedAt }}</p>
    <div class="info-strip">
      <p>
        Kết quả này kiểm tra Auth, Employee, Calendar, Leave và Attendance. Tiến độ cấp tài khoản
        qua Kafka được theo dõi trong hồ sơ nhân viên. Hệ thống chưa có API kiểm tra riêng kết nối
        Kafka.
      </p>
    </div>
  </div>
</template>
