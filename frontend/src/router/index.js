import { createRouter, createWebHistory } from 'vue-router'
import { calendarManagementRoles } from '@/calendar/constants.js'
import { leaveReviewRoles } from '@/leave/helpers.js'
import { attendanceRoles } from '@/attendance/helpers.js'
import { auth } from '@/auth/session.js'
import { ApiError } from '@/auth/api.js'
import { accountCreationRoles, safeDestination } from '@/auth/navigation.js'
export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: 'Đăng nhập' },
  },
  {
    path: '/connection-error',
    name: 'connection-error',
    component: () => import('@/views/ConnectionErrorView.vue'),
    meta: { title: 'Kết nối gián đoạn' },
  },
  {
    path: '/',
    component: () => import('@/layouts/WorkspaceLayout.vue'),
    meta: { title: 'Không gian làm việc', requiresAuth: true },
    children: [
      {
        path: 'attendance',
        name: 'attendance-mine',
        component: () => import('@/views/AttendanceView.vue'),
        meta: { title: 'Công của tôi' },
      },
      {
        path: 'attendance/overtime',
        name: 'attendance-overtime',
        component: () => import('@/views/AttendanceOvertimeView.vue'),
        meta: { title: 'Tăng ca (OT)' },
      },
      {
        path: 'attendance/corrections',
        name: 'attendance-corrections',
        component: () => import('@/views/AttendanceCorrectionsView.vue'),
        meta: { title: 'Bổ sung / Điều chỉnh công' },
      },
      {
        path: 'attendance/requests',
        name: 'attendance-requests',
        component: () => import('@/views/AttendanceRequestsView.vue'),
        meta: { title: 'Xin đi trễ / về sớm' },
      },
      {
        path: 'attendance/shifts',
        name: 'attendance-shifts',
        component: () => import('@/views/AttendanceShiftsView.vue'),
        meta: { title: 'Ca làm việc', roles: attendanceRoles },
      },
      {
        path: 'attendance/schedules',
        name: 'attendance-schedules',
        component: () => import('@/views/AttendanceSchedulesView.vue'),
        meta: { title: 'Phân công ca', roles: attendanceRoles },
      },
      {
        path: 'attendance/reports',
        name: 'attendance-reports',
        component: () => import('@/views/AttendanceReportsView.vue'),
        meta: { title: 'Bảng công', roles: attendanceRoles },
      },
      {
        path: 'leave',
        name: 'leave-mine',
        component: () => import('@/views/LeaveRequestsView.vue'),
        props: { inbox: false },
        meta: { title: 'Nghỉ phép của tôi' },
      },
      {
        path: 'leave/inbox',
        name: 'leave-inbox',
        component: () => import('@/views/LeaveRequestsView.vue'),
        props: { inbox: true },
        meta: { title: 'Duyệt nghỉ phép', roles: leaveReviewRoles },
      },
      {
        path: 'leave/requests/:id([0-9a-fA-F-]{36})',
        name: 'leave-detail',
        component: () => import('@/views/LeaveRequestDetailView.vue'),
        props: true,
        meta: { title: 'Chi tiết đơn nghỉ' },
      },
      {
        path: 'calendar-events',
        name: 'calendar-management',
        component: () => import('@/views/CalendarManagementView.vue'),
        meta: { title: 'Quản lý lịch', roles: calendarManagementRoles },
      },
      {
        path: 'calendar-events/new',
        name: 'calendar-event-new',
        component: () => import('@/views/CalendarEventFormView.vue'),
        meta: { title: 'Tạo sự kiện', roles: calendarManagementRoles },
      },
      {
        path: 'calendar-events/:id(\\d+)',
        name: 'calendar-event-detail',
        component: () => import('@/views/CalendarEventFormView.vue'),
        props: true,
        meta: { title: 'Chi tiết sự kiện', roles: calendarManagementRoles },
      },
      {
        path: 'calendar',
        name: 'calendar',
        component: () => import('@/views/EventCalendarView.vue'),
        meta: { title: 'Lịch nghỉ & sự kiện' },
      },
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: 'Tổng quan' },
      },
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/AccountView.vue'),
        meta: { title: 'Tài khoản của tôi' },
      },
      {
        path: 'accounts/new',
        name: 'create-account',
        component: () => import('@/views/CreateAccountView.vue'),
        meta: { title: 'Tạo tài khoản', roles: accountCreationRoles },
      },
      ...['employees', 'departments', 'positions'].flatMap((resource) => {
        const title = { employees: 'Nhân viên', departments: 'Phòng ban', positions: 'Chức danh' }[
          resource
        ]
        const component =
          resource === 'employees'
            ? () => import('@/views/EmployeeFormView.vue')
            : () => import('@/views/CatalogFormView.vue')
        return [
          {
            path: resource,
            name: resource,
            component: () => import('@/views/DirectoryView.vue'),
            props: { resource },
            meta: { title },
          },
          {
            path: `${resource}/new`,
            name: `${resource}-new`,
            component,
            props: { resource },
            meta: { title: `Thêm ${title.toLowerCase()}` },
          },
          {
            path: `${resource}/:id([0-9a-fA-F-]{36})`,
            name: `${resource}-detail`,
            component,
            props: (route) => ({ resource, id: route.params.id }),
            meta: { title: `Chi tiết ${title.toLowerCase()}` },
          },
        ]
      }),
      {
        path: 'services',
        name: 'services',
        component: () => import('@/views/ServiceStatusView.vue'),
        meta: { title: 'Kết nối dịch vụ' },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: 'Quyền truy cập' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: 'Không tìm thấy trang' },
      },
    ],
  },
]
export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
  if (to.name === 'connection-error') return true
  try {
    const wasInitialized = auth.state.initialized
    await auth.initialize()
    // Re-read current roles at each protected navigation, not from a JWT/local cache.
    if (wasInitialized && auth.authenticated.value && (to.meta.requiresAuth || to.name === 'login'))
      await auth.loadUser()
  } catch (error) {
    if (!(error instanceof ApiError) || ![401, 403, 404].includes(error.status)) {
      return { name: 'connection-error', query: { redirect: safeDestination(to.path) } }
    }
  }
  if (to.meta.requiresAuth && !auth.authenticated.value) {
    return { name: 'login', query: { redirect: safeDestination(to.path) } }
  }
  if (to.name === 'login' && auth.authenticated.value) return safeDestination(to.query.redirect)
  if (to.meta.roles && !auth.hasRole(to.meta.roles)) return { name: 'forbidden' }
  return true
})
router.afterEach((to) => {
  document.title = `${to.meta.title} | Company HRM`
})
export default router
