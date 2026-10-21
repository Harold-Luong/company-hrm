import { computed } from 'vue'
import { translate } from '../i18n/index.js'
// User-facing rules verified against the current UI and backend.
// Keep formulas aligned with AttendanceCalculator, AttendancePermissionService,
// OvertimeService, LeaveService and src/attendance/report.js when policies change.
export const helpGroups = [
  {
    id: 'personal',
    get label() {
      return translate('help.groups.personal')
    },
  },
  {
    id: 'attendance',
    get label() {
      return translate('help.groups.attendance')
    },
  },
  {
    id: 'leave',
    get label() {
      return translate('help.groups.leave')
    },
  },
  {
    id: 'people',
    get label() {
      return translate('help.groups.people')
    },
  },
  {
    id: 'system',
    get label() {
      return translate('help.groups.system')
    },
  },
]

export const features = [
  {
    id: 'overview',
    groupId: 'personal',
    get group() {
      return translate('help.groups.personal')
    },
    get title() {
      return translate('help.features.overview.title')
    },
    path: '/',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.overview.description')
    },
    get rules() {
      return [
        translate('help.features.overview.rules.0'),
        translate('help.features.overview.rules.1'),
      ]
    },
  },
  {
    id: 'profile',
    groupId: 'personal',
    get group() {
      return translate('help.groups.personal')
    },
    get title() {
      return translate('help.features.profile.title')
    },
    path: '/account',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.profile.description')
    },
    get rules() {
      return [
        translate('help.features.profile.rules.0'),
        translate('help.features.profile.rules.1'),
        translate('help.features.profile.rules.2'),
      ]
    },
    formulaIds: ['work-time', 'leave-days', 'leave-balance'],
  },
  {
    id: 'session',
    groupId: 'personal',
    get group() {
      return translate('help.groups.personal')
    },
    get title() {
      return translate('help.features.session.title')
    },
    path: '/account',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.session.description')
    },
    get rules() {
      return [
        translate('help.features.session.rules.0'),
        translate('help.features.session.rules.1'),
      ]
    },
  },
  {
    id: 'attendance',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.attendance.title')
    },
    path: '/attendance',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.attendance.description')
    },
    get rules() {
      return [
        translate('help.features.attendance.rules.0'),
        translate('help.features.attendance.rules.1'),
        translate('help.features.attendance.rules.2'),
      ]
    },
    formulaIds: ['work-time', 'actual-time', 'rounding', 'report-totals'],
  },
  {
    id: 'permissions',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.permissions.title')
    },
    path: '/attendance/requests',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.permissions.description')
    },
    get rules() {
      return [
        translate('help.features.permissions.rules.0'),
        translate('help.features.permissions.rules.1'),
        translate('help.features.permissions.rules.2'),
      ]
    },
    formulaIds: ['permission-time', 'rounding'],
  },
  {
    id: 'permission-review',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.permission-review.title')
    },
    path: '/attendance/requests/inbox',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.permission-review.description')
    },
    get rules() {
      return [
        translate('help.features.permission-review.rules.0'),
        translate('help.features.permission-review.rules.1'),
      ]
    },
    formulaIds: ['permission-time'],
  },
  {
    id: 'corrections',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.corrections.title')
    },
    path: '/attendance/corrections',
    get access() {
      return translate('help.features.corrections.access')
    },
    get description() {
      return translate('help.features.corrections.description')
    },
    get rules() {
      return [
        translate('help.features.corrections.rules.0'),
        translate('help.features.corrections.rules.1'),
      ]
    },
    formulaIds: ['work-time'],
  },
  {
    id: 'overtime',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.overtime.title')
    },
    path: '/attendance/overtime',
    get access() {
      return translate('help.features.overtime.access')
    },
    get description() {
      return translate('help.features.overtime.description')
    },
    get rules() {
      return [
        translate('help.features.overtime.rules.0'),
        translate('help.features.overtime.rules.1'),
      ]
    },
    formulaIds: ['overtime-time'],
  },
  {
    id: 'shifts',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.shifts.title')
    },
    path: '/attendance/shifts',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.shifts.description')
    },
    get rules() {
      return [
        translate('help.features.shifts.rules.0'),
        translate('help.features.shifts.rules.1'),
        translate('help.features.shifts.rules.2'),
      ]
    },
    formulaIds: ['work-time'],
  },
  {
    id: 'schedules',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.schedules.title')
    },
    path: '/attendance/schedules',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.schedules.description')
    },
    get rules() {
      return [
        translate('help.features.schedules.rules.0'),
        translate('help.features.schedules.rules.1'),
        translate('help.features.schedules.rules.2'),
      ]
    },
  },
  {
    id: 'reports',
    groupId: 'attendance',
    get group() {
      return translate('help.groups.attendance')
    },
    get title() {
      return translate('help.features.reports.title')
    },
    path: '/attendance/reports',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.reports.description')
    },
    get rules() {
      return [
        translate('help.features.reports.rules.0'),
        translate('help.features.reports.rules.1'),
        translate('help.features.reports.rules.2'),
      ]
    },
    formulaIds: ['work-time', 'report-totals'],
  },
  {
    id: 'leave',
    groupId: 'leave',
    get group() {
      return translate('help.groups.leave')
    },
    get title() {
      return translate('help.features.leave.title')
    },
    path: '/leave',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.leave.description')
    },
    get rules() {
      return [translate('help.features.leave.rules.0'), translate('help.features.leave.rules.1')]
    },
    formulaIds: ['leave-days', 'leave-balance'],
  },
  {
    id: 'leave-review',
    groupId: 'leave',
    get group() {
      return translate('help.groups.leave')
    },
    get title() {
      return translate('help.features.leave-review.title')
    },
    path: '/leave/inbox',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.leave-review.description')
    },
    get rules() {
      return [
        translate('help.features.leave-review.rules.0'),
        translate('help.features.leave-review.rules.1'),
      ]
    },
    formulaIds: ['leave-balance'],
  },
  {
    id: 'leave-adjustments',
    groupId: 'leave',
    get group() {
      return translate('help.groups.leave')
    },
    get title() {
      return translate('help.features.leave-adjustments.title')
    },
    path: '/leave',
    get access() {
      return translate('help.features.leave-adjustments.access')
    },
    get description() {
      return translate('help.features.leave-adjustments.description')
    },
    get rules() {
      return [
        translate('help.features.leave-adjustments.rules.0'),
        translate('help.features.leave-adjustments.rules.1'),
      ]
    },
    formulaIds: ['leave-balance'],
  },
  {
    id: 'employees',
    groupId: 'people',
    get group() {
      return translate('help.groups.people')
    },
    get title() {
      return translate('help.features.employees.title')
    },
    path: '/employees',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.employees.description')
    },
    get rules() {
      return [translate('help.features.employees.rules.0')]
    },
  },
  {
    id: 'departments',
    groupId: 'people',
    get group() {
      return translate('help.groups.people')
    },
    get title() {
      return translate('help.features.departments.title')
    },
    path: '/departments',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.departments.description')
    },
    get rules() {
      return [translate('help.features.departments.rules.0')]
    },
  },
  {
    id: 'positions',
    groupId: 'people',
    get group() {
      return translate('help.groups.people')
    },
    get title() {
      return translate('help.features.positions.title')
    },
    path: '/positions',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.positions.description')
    },
    get rules() {
      return [translate('help.features.positions.rules.0')]
    },
  },
  {
    id: 'accounts',
    groupId: 'personal',
    get group() {
      return translate('help.groups.personal')
    },
    get title() {
      return translate('help.features.accounts.title')
    },
    path: '/accounts/new',
    get access() {
      return translate('help.features.accounts.access')
    },
    roles: ['ADMIN'],
    restricted: true,
    get description() {
      return translate('help.features.accounts.description')
    },
    get rules() {
      return [
        translate('help.features.accounts.rules.0'),
        translate('help.features.accounts.rules.1'),
      ]
    },
  },
  {
    id: 'calendar',
    groupId: 'people',
    get group() {
      return translate('help.groups.people')
    },
    get title() {
      return translate('help.features.calendar.title')
    },
    path: '/calendar',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.calendar.description')
    },
    get rules() {
      return [
        translate('help.features.calendar.rules.0'),
        translate('help.features.calendar.rules.1'),
        translate('help.features.calendar.rules.2'),
      ]
    },
  },
  {
    id: 'calendar-management',
    groupId: 'people',
    get group() {
      return translate('help.groups.people')
    },
    get title() {
      return translate('help.features.calendar-management.title')
    },
    path: '/calendar-events',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.calendar-management.description')
    },
    get rules() {
      return [translate('help.features.calendar-management.rules.0')]
    },
  },
  {
    id: 'notifications',
    groupId: 'system',
    get group() {
      return translate('help.groups.system')
    },
    get title() {
      return translate('help.features.notifications.title')
    },
    path: '/leave/inbox',
    get access() {
      return translate('help.access.reviewers')
    },
    restricted: true,
    get description() {
      return translate('help.features.notifications.description')
    },
    get rules() {
      return [
        translate('help.features.notifications.rules.0'),
        translate('help.features.notifications.rules.1'),
      ]
    },
  },
  {
    id: 'services',
    groupId: 'system',
    get group() {
      return translate('help.groups.system')
    },
    get title() {
      return translate('help.features.services.title')
    },
    path: '/services',
    get access() {
      return translate('help.access.personal')
    },
    get description() {
      return translate('help.features.services.description')
    },
    get rules() {
      return [translate('help.features.services.rules.0')]
    },
  },
]

export const formulas = [
  {
    id: 'work-time',
    get title() {
      return translate('help.formulas.work-time.title')
    },
    get expression() {
      return translate('help.formulas.work-time.expression')
    },
    get explanation() {
      return translate('help.formulas.work-time.explanation')
    },
    get example() {
      return translate('help.formulas.work-time.example')
    },
    get notes() {
      return [
        translate('help.formulas.work-time.notes.0'),
        translate('help.formulas.work-time.notes.1'),
        translate('help.formulas.work-time.notes.2'),
      ]
    },
  },
  {
    id: 'rounding',
    get title() {
      return translate('help.formulas.rounding.title')
    },
    get expression() {
      return translate('help.formulas.rounding.expression')
    },
    get explanation() {
      return translate('help.formulas.rounding.explanation')
    },
    get example() {
      return translate('help.formulas.rounding.example')
    },
    get notes() {
      return [translate('help.formulas.rounding.notes.0')]
    },
  },
  {
    id: 'actual-time',
    get title() {
      return translate('help.formulas.actual-time.title')
    },
    get expression() {
      return translate('help.formulas.actual-time.expression')
    },
    get explanation() {
      return translate('help.formulas.actual-time.explanation')
    },
    get example() {
      return translate('help.formulas.actual-time.example')
    },
    get notes() {
      return [translate('help.formulas.actual-time.notes.0')]
    },
  },
  {
    id: 'permission-time',
    get title() {
      return translate('help.formulas.permission-time.title')
    },
    get expression() {
      return translate('help.formulas.permission-time.expression')
    },
    get explanation() {
      return translate('help.formulas.permission-time.explanation')
    },
    get example() {
      return translate('help.formulas.permission-time.example')
    },
    get notes() {
      return [translate('help.formulas.permission-time.notes.0')]
    },
  },
  {
    id: 'overtime-time',
    get title() {
      return translate('help.formulas.overtime-time.title')
    },
    get expression() {
      return translate('help.formulas.overtime-time.expression')
    },
    get explanation() {
      return translate('help.formulas.overtime-time.explanation')
    },
    get example() {
      return translate('help.formulas.overtime-time.example')
    },
    get notes() {
      return [
        translate('help.formulas.overtime-time.notes.0'),
        translate('help.formulas.overtime-time.notes.1'),
      ]
    },
  },
  {
    id: 'leave-days',
    get title() {
      return translate('help.formulas.leave-days.title')
    },
    get expression() {
      return translate('help.formulas.leave-days.expression')
    },
    get explanation() {
      return translate('help.formulas.leave-days.explanation')
    },
    get example() {
      return translate('help.formulas.leave-days.example')
    },
    get notes() {
      return [
        translate('help.formulas.leave-days.notes.0'),
        translate('help.formulas.leave-days.notes.1'),
      ]
    },
  },
  {
    id: 'leave-balance',
    get title() {
      return translate('help.formulas.leave-balance.title')
    },
    get expression() {
      return translate('help.formulas.leave-balance.expression')
    },
    get explanation() {
      return translate('help.formulas.leave-balance.explanation')
    },
    get example() {
      return translate('help.formulas.leave-balance.example')
    },
    get notes() {
      return [
        translate('help.formulas.leave-balance.notes.0'),
        translate('help.formulas.leave-balance.notes.1'),
      ]
    },
  },
  {
    id: 'report-totals',
    get title() {
      return translate('help.formulas.report-totals.title')
    },
    get expression() {
      return translate('help.formulas.report-totals.expression')
    },
    get explanation() {
      return translate('help.formulas.report-totals.explanation')
    },
    get example() {
      return translate('help.formulas.report-totals.example')
    },
    get notes() {
      return [
        translate('help.formulas.report-totals.notes.0'),
        translate('help.formulas.report-totals.notes.1'),
        translate('help.formulas.report-totals.notes.2'),
        translate('help.formulas.report-totals.notes.3'),
      ]
    },
  },
]

export const guides = [
  {
    id: 'getting-started',
    get title() {
      return translate('help.guides.getting-started.title')
    },
    icon: 'user',
    path: '/account',
    get steps() {
      return [
        translate('help.guides.getting-started.steps.0'),
        translate('help.guides.getting-started.steps.1'),
        translate('help.guides.getting-started.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.getting-started.note')
    },
  },
  {
    id: 'daily-attendance',
    get title() {
      return translate('help.guides.daily-attendance.title')
    },
    icon: 'clock',
    path: '/attendance',
    get steps() {
      return [
        translate('help.guides.daily-attendance.steps.0'),
        translate('help.guides.daily-attendance.steps.1'),
        translate('help.guides.daily-attendance.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.daily-attendance.note')
    },
  },
  {
    id: 'request-leave',
    get title() {
      return translate('help.guides.request-leave.title')
    },
    icon: 'calendar',
    path: '/leave',
    get steps() {
      return [
        translate('help.guides.request-leave.steps.0'),
        translate('help.guides.request-leave.steps.1'),
        translate('help.guides.request-leave.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.request-leave.note')
    },
  },
  {
    id: 'request-permission',
    get title() {
      return translate('help.guides.request-permission.title')
    },
    icon: 'mail',
    path: '/attendance/requests',
    get steps() {
      return [
        translate('help.guides.request-permission.steps.0'),
        translate('help.guides.request-permission.steps.1'),
        translate('help.guides.request-permission.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.request-permission.note')
    },
  },
  {
    id: 'request-overtime',
    get title() {
      return translate('help.guides.request-overtime.title')
    },
    icon: 'clock',
    path: '/attendance/overtime',
    get steps() {
      return [
        translate('help.guides.request-overtime.steps.0'),
        translate('help.guides.request-overtime.steps.1'),
        translate('help.guides.request-overtime.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.request-overtime.note')
    },
  },
  {
    id: 'review-requests',
    get title() {
      return translate('help.guides.review-requests.title')
    },
    icon: 'shield',
    path: '/leave/inbox',
    restricted: true,
    get steps() {
      return [
        translate('help.guides.review-requests.steps.0'),
        translate('help.guides.review-requests.steps.1'),
        translate('help.guides.review-requests.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.review-requests.note')
    },
  },
  {
    id: 'manage-schedule',
    get title() {
      return translate('help.guides.manage-schedule.title')
    },
    icon: 'calendar',
    path: '/attendance/schedules',
    restricted: true,
    get steps() {
      return [
        translate('help.guides.manage-schedule.steps.0'),
        translate('help.guides.manage-schedule.steps.1'),
        translate('help.guides.manage-schedule.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.manage-schedule.note')
    },
  },
  {
    id: 'read-report',
    get title() {
      return translate('help.guides.read-report.title')
    },
    icon: 'grid',
    path: '/attendance/reports',
    restricted: true,
    get steps() {
      return [
        translate('help.guides.read-report.steps.0'),
        translate('help.guides.read-report.steps.1'),
        translate('help.guides.read-report.steps.2'),
        translate('help.guides.read-report.steps.3'),
      ]
    },
    get note() {
      return translate('help.guides.read-report.note')
    },
  },
  {
    id: 'manage-people',
    get title() {
      return translate('help.guides.manage-people.title')
    },
    icon: 'user',
    path: '/employees',
    restricted: true,
    get steps() {
      return [
        translate('help.guides.manage-people.steps.0'),
        translate('help.guides.manage-people.steps.1'),
        translate('help.guides.manage-people.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.manage-people.note')
    },
  },
  {
    id: 'manage-calendar',
    get title() {
      return translate('help.guides.manage-calendar.title')
    },
    icon: 'calendar',
    path: '/calendar-events',
    restricted: true,
    get steps() {
      return [
        translate('help.guides.manage-calendar.steps.0'),
        translate('help.guides.manage-calendar.steps.1'),
        translate('help.guides.manage-calendar.steps.2'),
      ]
    },
    get note() {
      return translate('help.guides.manage-calendar.note')
    },
  },
]

export const helpQuestions = computed(() => [
  [translate('help.questions.0.0'), translate('help.questions.0.1')],
  [translate('help.questions.1.0'), translate('help.questions.1.1')],
  [translate('help.questions.2.0'), translate('help.questions.2.1')],
  [translate('help.questions.3.0'), translate('help.questions.3.1')],
  [translate('help.questions.4.0'), translate('help.questions.4.1')],
  [translate('help.questions.5.0'), translate('help.questions.5.1')],
])
