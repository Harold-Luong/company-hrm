import { routeTitles as copy } from '../content/workspace.js'
import { common } from '../content/common.js'
import { calendarManagementRoles } from '@/calendar/constants.js'
import { leaveReviewRoles } from '@/leave/constants.js'
import { attendanceRoles } from '@/attendance/constants.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import { accountCreationRoles } from '@/auth/navigation.js'

export const routes = [
  {
    path: '/activate',
    name: 'activate',
    component: () => import('@/views/ActivationView.vue'),
    meta: { title: () => copy.activation },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: () => copy.login },
  },
  {
    path: '/connection-error',
    name: 'connection-error',
    component: () => import('@/views/ConnectionErrorView.vue'),
    meta: { title: () => copy.connectionError },
  },
  {
    path: '/',
    component: () => import('@/layouts/WorkspaceLayout.vue'),
    meta: { title: () => copy.workspace, requiresAuth: true },
    children: [
      {
        path: 'help/guide',
        name: 'help-guide',
        component: () => import('@/views/UserGuideView.vue'),
        meta: { title: () => copy.userGuide },
      },
      {
        path: 'help/features',
        name: 'help-features',
        component: () => import('@/views/FeatureReferenceView.vue'),
        meta: { title: () => copy.featureReference },
      },
      {
        path: 'attendance',
        name: 'attendance-mine',
        component: () => import('@/views/AttendanceView.vue'),
        meta: { title: () => copy.attendance },
      },
      {
        path: 'attendance/overtime',
        name: 'attendance-overtime',
        component: () => import('@/views/AttendanceOvertimeView.vue'),
        meta: { title: () => copy.overtime },
      },
      {
        path: 'attendance/corrections',
        name: 'attendance-corrections',
        component: () => import('@/views/AttendanceCorrectionsView.vue'),
        meta: { title: () => copy.corrections },
      },
      {
        path: 'attendance/requests',
        name: 'attendance-requests',
        component: () => import('@/views/AttendanceRequestsView.vue'),
        props: { inbox: false },
        meta: { title: () => copy.requests },
      },
      {
        path: 'attendance/requests/inbox',
        name: 'attendance-requests-inbox',
        component: () => import('@/views/AttendanceRequestsView.vue'),
        props: { inbox: true },
        meta: { title: () => copy.reviewRequests, roles: attendanceRoles },
      },
      {
        path: 'attendance/shifts',
        name: 'attendance-shifts',
        component: () => import('@/views/AttendanceShiftsView.vue'),
        meta: { title: () => copy.shifts, roles: attendanceRoles },
      },
      {
        path: 'attendance/schedules',
        name: 'attendance-schedules',
        component: () => import('@/views/AttendanceSchedulesView.vue'),
        meta: { title: () => copy.schedules, roles: attendanceRoles },
      },
      {
        path: 'attendance/reports',
        name: 'attendance-reports',
        component: () => import('@/views/AttendanceReportsView.vue'),
        meta: { title: () => copy.reports, roles: attendanceRoles },
      },
      {
        path: 'leave',
        name: 'leave-mine',
        component: () => import('@/views/LeaveRequestsView.vue'),
        props: { inbox: false },
        meta: { title: () => copy.myLeave },
      },
      {
        path: 'leave/inbox',
        name: 'leave-inbox',
        component: () => import('@/views/LeaveRequestsView.vue'),
        props: { inbox: true },
        meta: { title: () => copy.reviewLeave, roles: leaveReviewRoles },
      },
      {
        path: 'leave/requests/:id([0-9a-fA-F-]{36})',
        name: 'leave-detail',
        component: () => import('@/views/LeaveRequestDetailView.vue'),
        props: true,
        meta: { title: () => copy.leaveDetail },
      },
      {
        path: 'calendar-events',
        name: 'calendar-management',
        component: () => import('@/views/CalendarManagementView.vue'),
        meta: { title: () => copy.manageCalendar, roles: calendarManagementRoles },
      },
      {
        path: 'calendar-events/new',
        name: 'calendar-event-new',
        component: () => import('@/views/CalendarEventFormView.vue'),
        meta: { title: () => copy.createEvent, roles: calendarManagementRoles },
      },
      {
        path: 'calendar-events/:id(\\d+)',
        name: 'calendar-event-detail',
        component: () => import('@/views/CalendarEventFormView.vue'),
        props: true,
        meta: { title: () => copy.eventDetail, roles: calendarManagementRoles },
      },
      {
        path: 'calendar',
        name: 'calendar',
        component: () => import('@/views/EventCalendarView.vue'),
        meta: { title: () => copy.calendar },
      },
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: () => copy.home },
      },
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/AccountView.vue'),
        meta: { title: () => copy.account },
      },
      {
        path: 'accounts/new',
        name: 'create-account',
        component: () => import('@/views/CreateAccountView.vue'),
        meta: { title: () => copy.createAccount, roles: accountCreationRoles },
      },
      ...['employees', 'departments', 'positions'].flatMap((resource) => {
        const title = () =>
          ({
            get employees() {
              return common.employee
            },
            get departments() {
              return common.department
            },
            get positions() {
              return common.position
            },
          })[resource]
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
            meta: { title, roles: personnelManagementRoles },
          },
          {
            path: `${resource}/new`,
            name: `${resource}-new`,
            component,
            props: { resource },
            meta: {
              title: () => copy.createResource({ resource: title().toLowerCase() }),
              roles: personnelManagementRoles,
            },
          },
          {
            path: `${resource}/:id([0-9a-fA-F-]{36})`,
            name: `${resource}-detail`,
            component,
            props: (route) => ({ resource, id: route.params.id }),
            meta: {
              title: () => copy.resourceDetail({ resource: title().toLowerCase() }),
              roles: personnelManagementRoles,
            },
          },
        ]
      }),
      {
        path: 'services',
        name: 'services',
        component: () => import('@/views/ServiceStatusView.vue'),
        meta: { title: () => copy.services },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: () => copy.forbidden },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: () => copy.notFound },
      },
    ],
  },
]
