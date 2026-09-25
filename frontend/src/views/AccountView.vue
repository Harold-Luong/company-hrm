<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { errorMessage } from '@/auth/api.js'
import { formatDate } from '@/auth/format.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'
const user = computed(() => auth.state.user)
const router = useRouter()
const confirming = ref(false)
const busy = ref(false)
const error = ref('')
async function logoutAll() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await auth.logout(true)
    await router.replace({ name: 'login', query: { logout: 'success' } })
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div v-if="user">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CÁ NHÂN</p>
        <h1>Tài khoản của tôi</h1>
        <p class="muted">Thông tin tài khoản và quyền truy cập không gian làm việc.</p>
      </div>
    </div>
    <div class="account-grid">
      <section class="panel account-panel">
        <div class="panel-heading">
          <h2>Thông tin tài khoản</h2>
          <span class="status-badge"><span class="status-dot"></span>Đang hoạt động</span>
        </div>
        <div class="account-identity">
          <span class="avatar avatar-large">{{ user.email.slice(0, 2).toUpperCase() }}</span>
          <div>
            <h3>{{ user.email }}</h3>
            <p class="muted">Tài khoản nội bộ · #{{ user.id }}</p>
          </div>
        </div>
        <dl class="details-list">
          <div>
            <dt>Email đăng nhập</dt>
            <dd>{{ user.email }}</dd>
          </div>
          <div>
            <dt>Vai trò</dt>
            <dd class="role-list">
              <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
            </dd>
          </div>
          <div>
            <dt>Ngày tạo tài khoản</dt>
            <dd>{{ formatDate(user.createdAt) }}</dd>
          </div>
          <div>
            <dt>Cập nhật gần nhất</dt>
            <dd>{{ formatDate(user.updatedAt) }}</dd>
          </div>
          <div>
            <dt>Đăng nhập gần nhất</dt>
            <dd>{{ formatDate(user.lastLoginAt) }}</dd>
          </div>
        </dl>
      </section>
      <div class="account-aside">
        <section class="panel security-panel">
          <span class="metric-icon green"><AppIcon name="shield" :size="23" /></span>
          <h2>Bảo mật phiên truy cập</h2>
          <p class="muted">Thu hồi phiên đăng nhập trên các thiết bị, bao gồm phiên hiện tại.</p>
          <p class="small muted">
            Các phiên khác sẽ cần đăng nhập lại khi làm mới phiên. Quyền truy cập đã cấp có thể còn
            hiệu lực tối đa 15 phút theo cấu hình hiện tại.
          </p>
          <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
          <div v-if="confirming" class="confirm-box">
            <p>Bạn muốn đăng xuất tất cả phiên?</p>
            <div class="button-row">
              <button class="button button-danger" :disabled="busy" @click="logoutAll">
                {{ busy ? 'Đang xử lý…' : 'Xác nhận đăng xuất' }}</button
              ><button class="button button-secondary" :disabled="busy" @click="confirming = false">
                Hủy
              </button>
            </div>
          </div>
          <button v-else class="button button-danger-outline full-width" @click="confirming = true">
            <AppIcon name="logout" :size="17" />Đăng xuất tất cả phiên
          </button>
        </section>
        <div class="info-strip align-start">
          <AppIcon name="info" />
          <p>
            Để cập nhật thông tin hoặc thay đổi quyền truy cập, vui lòng liên hệ HR hoặc quản trị
            viên.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
