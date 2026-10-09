<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { errorMessage } from '@/auth/api.js'
import { formatDate } from '@/auth/format.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'

const router = useRouter()
const user = computed(() => auth.state.user)
const confirming = ref(false)
const busy = ref(false)
const error = ref('')

async function logoutAll() {
  if (busy.value) return

  busy.value = true
  error.value = ''

  try {
    await auth.logout(true)
    await router.replace({
      name: 'login',
      query: { logout: 'success' },
    })
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div v-if="user" class="profile-module">
    <div class="profile-module-heading">
      <h3>Thông tin tài khoản</h3>
      <span class="status-chip" :class="user.active ? 'active' : 'inactive'">
        {{ user.active ? 'Đang hoạt động' : 'Không hoạt động' }}
      </span>
    </div>

    <dl class="profile-fields">
      <div>
        <dt>Email đăng nhập</dt>
        <dd>{{ user.email }}</dd>
      </div>
      <div>
        <dt>Mã tài khoản</dt>
        <dd>#{{ user.id }}</dd>
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
    <p class="account-note">Để thay đổi quyền truy cập, vui lòng liên hệ quản trị viên.</p>

    <section class="account-security" aria-labelledby="account-security-title">
      <div class="profile-module-heading">
        <h3 id="account-security-title">Bảo mật phiên truy cập</h3>
      </div>
      <p class="muted">Thu hồi phiên đăng nhập trên các thiết bị, bao gồm phiên hiện tại.</p>
      <p class="small muted">
        Các phiên khác sẽ cần đăng nhập lại khi làm mới phiên. Quyền truy cập đã cấp có thể còn hiệu
        lực tối đa 15 phút theo cấu hình hiện tại.
      </p>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <div v-if="confirming" class="confirm-box">
        <p>Bạn muốn đăng xuất tất cả phiên?</p>
        <div class="button-row">
          <button class="button button-danger" :disabled="busy" @click="logoutAll">
            {{ busy ? 'Đang xử lý…' : 'Xác nhận đăng xuất' }}
          </button>
          <button class="button button-secondary" :disabled="busy" @click="confirming = false">
            Hủy
          </button>
        </div>
      </div>
      <button v-else class="button button-danger-outline" @click="confirming = true">
        <AppIcon name="logout" :size="17" />
        Đăng xuất tất cả phiên
      </button>
    </section>
  </div>
</template>

<style scoped>
.account-note {
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 14px;
}
.account-security {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.account-security .profile-module-heading {
  margin-bottom: 12px;
}
</style>
