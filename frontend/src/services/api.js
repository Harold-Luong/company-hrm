import { serviceNames as copy } from '../content/services.js'
import { requestJson } from '../api/client.js'
import { apiPath } from '../api/config.js'

export const serviceDefinitions = [
  {
    id: 'auth',
    get name() {
      return copy.auth
    },
    requiresDatabaseStatus: true,
  },
  {
    id: 'employees',
    get name() {
      return copy.employees
    },
    requiresDatabaseStatus: true,
  },
  {
    id: 'calendar',
    get name() {
      return copy.calendar
    },
  },
  {
    id: 'attendance',
    get name() {
      return copy.attendance
    },
    requiresDatabaseStatus: true,
  },
  {
    id: 'leave',
    get name() {
      return copy.leave
    },
  },
  {
    id: 'notifications',
    get name() {
      return copy.notifications
    },
  },
]

export const getServiceHealth = (serviceId) => requestJson(apiPath(serviceId, 'health-check'))
