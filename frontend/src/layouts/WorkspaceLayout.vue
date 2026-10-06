<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { calendarManagementRoles } from '@/calendar/constants.js'
import { leaveReviewRoles } from '@/leave/helpers.js'
import { attendanceRoles } from '@/attendance/helpers.js'
import { leave } from '@/leave/api.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const busy = ref(false)
const pendingLeaveCount = ref(0)
let countLoading = false
let countTimer
let countStopped = false
const initials = computed(() => auth.state.user?.email.slice(0, 2).toUpperCase() || 'CH')
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
    refreshPendingLeaveCount()
  },
)
async function refreshPendingLeaveCount() {
  if (!auth.hasRole(leaveReviewRoles) || countLoading) return
  countLoading = true
  try {
    const result = await leave.pendingCount()
    pendingLeaveCount.value =
      Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : 0
  } catch {
    // The menu remains usable if Leave is temporarily unavailable.
  } finally {
    countLoading = false
  }
}
async function schedulePendingLeaveCount() {
  await refreshPendingLeaveCount()
  if (!countStopped) countTimer = window.setTimeout(schedulePendingLeaveCount, 30_000)
}
onMounted(() => {
  countStopped = false
  schedulePendingLeaveCount()
})
onBeforeUnmount(() => {
  countStopped = true
  window.clearTimeout(countTimer)
})
async function signOut() {
  busy.value = true
  let warning = false
  try {
    await auth.logout()
  } catch {
    warning = true
  }
  await router.replace({
    name: 'login',
    query: warning ? { logout: 'local' } : { logout: 'success' },
  })
  busy.value = false
}
</script>

<template>
  <div v-if="auth.state.user" class="workspace" @keydown.esc="mobileOpen = false">
    <a class="skip-link" href="#main-content">Đến nội dung chính</a>
    <button
      v-if="mobileOpen"
      class="sidebar-overlay"
      aria-label="Đóng menu"
      @click="mobileOpen = false"
    ></button>
    <aside id="workspace-sidebar" class="sidebar" :class="{ 'is-open': mobileOpen }" :inert="busy">
      <RouterLink to="/" class="brand"
        ><span class="brand-icon">C<span>h</span></span
        ><span
          >company<span class="brand-light">hrm</span><small>WORKSPACE</small></span
        ></RouterLink
      >
      <button
        class="mobile-sidebar-close icon-button"
        aria-label="Đóng menu"
        @click="mobileOpen = false"
      >
        <AppIcon name="close" />
      </button>
      <div class="workspace-label">
        <span class="workspace-symbol">C</span>
        <div>Company workspace<small>Không gian nội bộ</small></div>
        <span class="status-dot"></span>
      </div>
      <nav class="sidebar-nav" aria-label="Điều hướng chính">
        <p class="nav-section">KHÔNG GIAN LÀM VIỆC</p>
        <RouterLink to="/" class="nav-item" exact-active-class="is-active">
          <AppIcon name="grid" />Tổng quan
        </RouterLink>
        <RouterLink to="/account" class="nav-item" active-class="is-active">
          <AppIcon name="user" />Tài khoản của tôi
        </RouterLink>
        <RouterLink to="/calendar" class="nav-item" active-class="is-active">
          <AppIcon name="calendar" />Lịch nghỉ & sự kiện
        </RouterLink>
        <RouterLink to="/attendance" class="nav-item" exact-active-class="is-active"
          ><AppIcon name="calendar" />Công của tôi</RouterLink
        >
        <RouterLink to="/attendance/requests" class="nav-item" active-class="is-active"
          ><AppIcon name="clock" />Xin đi trễ / về sớm</RouterLink
        >
        <p class="nav-section management-label">QUẢN LÝ NHÂN SỰ</p>
        <RouterLink
          v-if="auth.hasRole(attendanceRoles)"
          to="/attendance/shifts"
          class="nav-item"
          active-class="is-active"
          ><AppIcon name="calendar" />Ca làm việc</RouterLink
        >
        <RouterLink
          v-if="auth.hasRole(attendanceRoles)"
          to="/attendance/schedules"
          class="nav-item"
          active-class="is-active"
          ><AppIcon name="calendar" />Phân công ca</RouterLink
        >
        <RouterLink
          v-if="auth.hasRole(attendanceRoles)"
          to="/attendance/reports"
          class="nav-item"
          active-class="is-active"
          ><AppIcon name="grid" />Bảng công</RouterLink
        >
        <RouterLink
          v-if="auth.hasRole(calendarManagementRoles)"
          to="/calendar-events"
          class="nav-item"
          active-class="is-active"
        >
          <AppIcon name="calendar" />Quản lý lịch
        </RouterLink>
        <RouterLink
          to="/leave"
          class="nav-item"
          active-class="is-active"
          exact-active-class="is-active"
        >
          <AppIcon name="calendar" />Nghỉ phép của tôi
        </RouterLink>
        <RouterLink
          v-if="auth.hasRole(leaveReviewRoles)"
          to="/leave/inbox"
          class="nav-item"
          active-class="is-active"
        >
          <AppIcon name="calendar" /><span>Duyệt nghỉ phép</span
          ><span
            v-if="pendingLeaveCount > 0"
            class="nav-notification"
            :aria-label="`${pendingLeaveCount} đơn đang chờ duyệt`"
            >{{ pendingLeaveCount > 99 ? '99+' : pendingLeaveCount }}</span
          >
        </RouterLink>
        <RouterLink
          to="/employees"
          class="nav-item"
          :class="{
            'is-active': route.path === '/employees' || route.path.startsWith('/employees/'),
          }"
        >
          <AppIcon name="user" />Nhân viên
        </RouterLink>
        <RouterLink
          to="/departments"
          class="nav-item"
          :class="{
            'is-active': route.path === '/departments' || route.path.startsWith('/departments/'),
          }"
        >
          <AppIcon name="grid" />Phòng ban
        </RouterLink>
        <RouterLink
          to="/positions"
          class="nav-item"
          :class="{
            'is-active': route.path === '/positions' || route.path.startsWith('/positions/'),
          }"
        >
          <AppIcon name="shield" />Chức danh
        </RouterLink>
        <RouterLink to="/services" class="nav-item" active-class="is-active">
          <AppIcon name="info" />Kết nối dịch vụ
        </RouterLink>
        <template v-if="auth.hasRole(accountCreationRoles)">
          <p class="nav-section management-label">QUẢN TRỊ TRUY CẬP</p>
          <RouterLink to="/accounts/new" class="nav-item" active-class="is-active">
            <AppIcon name="add" />Tạo tài khoản
          </RouterLink>
        </template>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-note">
          <AppIcon name="shield" />
          <p>Truy cập theo vai trò<small>Không gian được cá nhân hóa theo quyền của bạn.</small></p>
        </div>
        <button class="nav-item logout-button" :disabled="busy" @click="signOut">
          <AppIcon name="logout" />{{ busy ? 'Đang đăng xuất…' : 'Đăng xuất' }}
        </button>
      </div>
    </aside>
    <div class="workspace-body" :inert="mobileOpen">
      <header class="topbar">
        <div class="breadcrumb">
          <button
            class="icon-button mobile-menu"
            aria-label="Mở menu"
            aria-controls="workspace-sidebar"
            :aria-expanded="mobileOpen"
            @click="mobileOpen = true"
          >
            <AppIcon name="menu" /></button
          ><span>Workspace</span><span class="breadcrumb-divider">/</span
          ><strong>{{ route.meta.title }}</strong>
        </div>
        <RouterLink to="/account" class="topbar-profile" aria-label="Mở tài khoản của tôi"
          ><span class="topbar-email">{{ auth.state.user.email }}</span
          ><span class="avatar">{{ initials }}</span></RouterLink
        >
      </header>
      <main id="main-content" class="page-content" tabindex="-1">
        <RouterView :key="route.path" />
      </main>
      <footer class="workspace-footer">
        <span>© {{ new Date().getFullYear() }} Company HRM</span
        ><span>Không gian làm việc nội bộ</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.nav-notification {
  min-width: 1.35rem;
  height: 1.35rem;
  margin-left: auto;
  padding: 0 0.35rem;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1.35rem;
  text-align: center;
}
</style>
