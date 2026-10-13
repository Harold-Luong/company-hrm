<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import { calendarManagementRoles } from '@/calendar/constants.js'
import { leaveReviewRoles } from '@/leave/helpers.js'
import { attendanceRoles } from '@/attendance/helpers.js'
import { leave } from '@/leave/api.js'
import { attendance } from '@/attendance/api.js'
import { subscribeNotifications } from '@/notifications/stream.js'
import { requestChangedEvent } from '@/notifications/changes.js'
import AppIcon from '@/components/AppIcon.vue'
import AccountResultToasts from '@/components/AccountResultToasts.vue'
import { startAccountNotifications } from '@/notifications/accountResults.js'
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const accountMenuOpen = ref(false)
const accountMenu = ref(null)
const accountTrigger = ref(null)
function closeMenus() {
  if (accountMenuOpen.value) {
    accountMenuOpen.value = false
    accountTrigger.value?.focus()
  }
  mobileOpen.value = false
}
function closeAccountMenuOutside(event) {
  if (!accountMenu.value?.contains(event.target)) accountMenuOpen.value = false
}
function closeAccountMenuOnBlur(event) {
  if (!accountMenu.value?.contains(event.relatedTarget)) accountMenuOpen.value = false
}
const busy = ref(false)
const pendingLeaveCount = ref(0)
const pendingAttendanceCount = ref(0)
let countLoading = false
let countQueued = false
let countTimer
let countStopped = false
let stopNotifications
let stopAccountNotifications
const initials = computed(() => auth.state.user?.email.slice(0, 2).toUpperCase() || 'CH')
const isRequestPage = computed(
  () =>
    ['/leave', '/attendance/requests', '/attendance/overtime', '/attendance/corrections'].includes(
      route.path,
    ) || route.path.startsWith('/leave/requests/'),
)
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
    accountMenuOpen.value = false
    refreshPendingCounts()
  },
)
async function refreshPendingCounts() {
  if (countStopped || !auth.hasRole(leaveReviewRoles)) return
  if (countLoading) {
    countQueued = true
    return
  }
  countLoading = true
  try {
    await Promise.allSettled(
      [
        [pendingLeaveCount, () => leave.pendingCount()],
        [pendingAttendanceCount, () => attendance.pendingRequestCount()],
      ].map(async ([target, fetchCount]) => {
        const result = await fetchCount()
        if (!countStopped)
          target.value = Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : 0
      }),
    )
  } finally {
    countLoading = false
    if (countQueued && !countStopped) {
      countQueued = false
      refreshPendingCounts()
    }
  }
}
async function schedulePendingCounts() {
  await refreshPendingCounts()
  if (!countStopped) countTimer = window.setTimeout(schedulePendingCounts, 30_000)
}
onMounted(() => {
  countStopped = false
  document.addEventListener('pointerdown', closeAccountMenuOutside)
  window.addEventListener(requestChangedEvent, refreshPendingCounts)
  schedulePendingCounts()
  if (auth.hasRole(leaveReviewRoles))
    stopNotifications = subscribeNotifications(refreshPendingCounts)
  if (auth.hasRole(accountCreationRoles)) stopAccountNotifications = startAccountNotifications()
})
onBeforeUnmount(() => {
  countStopped = true
  document.removeEventListener('pointerdown', closeAccountMenuOutside)
  window.removeEventListener(requestChangedEvent, refreshPendingCounts)
  window.clearTimeout(countTimer)
  stopNotifications?.()
  stopAccountNotifications?.()
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
  <div v-if="auth.state.user" class="workspace" @keydown.esc="closeMenus">
    <AccountResultToasts />
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
      <nav class="sidebar-nav" aria-label="Điều hướng chính">
        <section class="nav-group" aria-labelledby="nav-personal">
          <h2 id="nav-personal" class="nav-section">CÁ NHÂN</h2>
          <RouterLink to="/" class="nav-item" exact-active-class="is-active">
            <AppIcon name="grid" />Tổng quan
          </RouterLink>
          <RouterLink to="/calendar" class="nav-item" active-class="is-active">
            <AppIcon name="calendar" />Lịch nghỉ & sự kiện
          </RouterLink>
          <RouterLink to="/attendance" class="nav-item" exact-active-class="is-active">
            <AppIcon name="clock" />Chấm công của tôi
          </RouterLink>
          <details class="nav-requests" :open="isRequestPage">
            <summary class="nav-item" :class="{ 'is-active': isRequestPage }">
              <AppIcon name="mail" /><span>Yêu cầu của tôi</span>
              <AppIcon name="arrow" :size="14" class="nav-chevron" />
            </summary>
            <div class="nav-submenu">
              <RouterLink
                to="/leave"
                class="nav-item"
                :class="{
                  'is-active': route.path === '/leave' || route.path.startsWith('/leave/requests/'),
                }"
              >
                Nghỉ phép của tôi
              </RouterLink>
              <RouterLink to="/attendance/requests" class="nav-item" active-class="is-active">
                Xin đi trễ / về sớm
              </RouterLink>
              <RouterLink to="/attendance/overtime" class="nav-item" active-class="is-active">
                Tăng ca (OT)
              </RouterLink>
              <RouterLink to="/attendance/corrections" class="nav-item" active-class="is-active">
                Bổ sung / Điều chỉnh
              </RouterLink>
            </div>
          </details>
        </section>
        <section
          v-if="auth.hasRole(attendanceRoles)"
          class="nav-group"
          aria-labelledby="nav-attendance"
        >
          <h2 id="nav-attendance" class="nav-section">QUẢN LÝ CHẤM CÔNG</h2>
          <RouterLink to="/attendance/requests/inbox" class="nav-item" active-class="is-active">
            <AppIcon name="shield" /><span>Duyệt đi trễ / về sớm</span>
            <span
              v-if="pendingAttendanceCount > 0"
              class="nav-notification"
              :aria-label="`${pendingAttendanceCount} đơn đi trễ / về sớm đang chờ duyệt`"
              >{{ pendingAttendanceCount > 99 ? '99+' : pendingAttendanceCount }}</span
            >
          </RouterLink>
          <RouterLink to="/attendance/shifts" class="nav-item" active-class="is-active">
            <AppIcon name="clock" />Ca làm việc
          </RouterLink>
          <RouterLink to="/attendance/schedules" class="nav-item" active-class="is-active">
            <AppIcon name="calendar" /><span>Phân công & lịch làm việc</span>
          </RouterLink>
          <RouterLink to="/attendance/reports" class="nav-item" active-class="is-active">
            <AppIcon name="grid" />Bảng công
          </RouterLink>
        </section>
        <section
          v-if="auth.hasRole(leaveReviewRoles)"
          class="nav-group"
          aria-labelledby="nav-leave"
        >
          <h2 id="nav-leave" class="nav-section">QUẢN LÝ NGHỈ PHÉP</h2>
          <RouterLink to="/leave/inbox" class="nav-item" active-class="is-active">
            <AppIcon name="shield" /><span>Duyệt nghỉ phép</span
            ><span
              v-if="pendingLeaveCount > 0"
              class="nav-notification"
              :aria-label="`${pendingLeaveCount} đơn đang chờ duyệt`"
              >{{ pendingLeaveCount > 99 ? '99+' : pendingLeaveCount }}</span
            >
          </RouterLink>
        </section>
        <section
          v-if="auth.hasRole(personnelManagementRoles)"
          class="nav-group"
          aria-labelledby="nav-people"
        >
          <h2 id="nav-people" class="nav-section">QUẢN LÝ NHÂN SỰ</h2>
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
        </section>
        <section class="nav-group" aria-labelledby="nav-admin">
          <h2 id="nav-admin" class="nav-section">QUẢN TRỊ</h2>
          <RouterLink
            v-if="auth.hasRole(calendarManagementRoles)"
            to="/calendar-events"
            class="nav-item"
            :class="{
              'is-active':
                route.path === '/calendar-events' || route.path.startsWith('/calendar-events/'),
            }"
          >
            <AppIcon name="calendar" />Quản lý lịch
          </RouterLink>
          <RouterLink to="/services" class="nav-item" active-class="is-active">
            <AppIcon name="info" />Kết nối dịch vụ
          </RouterLink>
          <RouterLink
            v-if="auth.hasRole(accountCreationRoles)"
            to="/accounts/new"
            class="nav-item"
            active-class="is-active"
          >
            <AppIcon name="add" />Tạo tài khoản
          </RouterLink>
        </section>
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
        <div ref="accountMenu" class="topbar-account" @focusout="closeAccountMenuOnBlur">
          <button
            ref="accountTrigger"
            type="button"
            class="topbar-profile account-menu-trigger"
            aria-label="Mở menu tài khoản"
            aria-controls="account-dropdown"
            :aria-expanded="accountMenuOpen"
            @click="accountMenuOpen = !accountMenuOpen"
          >
            <span class="topbar-email">{{ auth.state.user.email }}</span>
            <span class="avatar">{{ initials }}</span>
          </button>
          <nav
            v-if="accountMenuOpen"
            id="account-dropdown"
            class="account-dropdown"
            aria-label="Tài khoản"
          >
            <RouterLink
              to="/account"
              class="account-dropdown-link"
              @click="accountMenuOpen = false"
            >
              <AppIcon name="user" :size="18" />Hồ sơ của tôi
            </RouterLink>
          </nav>
        </div>
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
.sidebar > .brand {
  margin-bottom: 24px;
}
.nav-group {
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.nav-group + .nav-group {
  margin-top: 18px;
}
.nav-item > svg {
  flex-shrink: 0;
}
.nav-requests > summary {
  cursor: pointer;
  list-style: none;
}
.nav-requests > summary::-webkit-details-marker {
  display: none;
}
.nav-chevron {
  margin-left: auto;
  transform: rotate(0deg);
}
.nav-requests[open] > summary .nav-chevron {
  transform: rotate(90deg);
}
.nav-submenu {
  margin: 4px 0 8px 23px;
  padding-left: 8px;
  border-left: 1px solid var(--border);
}
.nav-submenu .nav-item {
  min-height: 36px;
  padding: 8px 10px;
}
.topbar-account {
  position: relative;
  flex-shrink: 0;
}
.account-menu-trigger {
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  padding: 6px;
}
.account-menu-trigger:hover,
.account-menu-trigger[aria-expanded='true'] {
  background: #e7f2ed;
}
.account-dropdown {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  right: 0;
  width: 220px;
  max-width: calc(100vw - 32px);
  padding: 6px;
  background: white;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 8px 24px #24324a14;
}
.account-dropdown-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 6px;
}
.account-dropdown-link:hover,
.account-dropdown-link:focus-visible {
  background: #e7f2ed;
  color: var(--primary-dark);
}

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
