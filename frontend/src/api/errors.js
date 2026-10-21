import { apiErrors as copy } from '../content/errors.js'
export class ApiError extends Error {
  constructor(status, message, retryAfter = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.retryAfter = retryAfter
  }
}
const messages = {
  get 'Department code already exists'() {
    return copy.departmentCodeExists
  },
  get 'Position code already exists'() {
    return copy.positionCodeExists
  },
  get 'Employee with this employeeCode already exists'() {
    return copy.employeeCodeExists
  },
  get 'Employee with this email already exists'() {
    return copy.employeeEmailExists
  },
  get 'dateOfBirth must be before hireDate'() {
    return copy.birthDateAfterHireDate
  },
  get 'managerId must not create a cycle in the manager hierarchy'() {
    return copy.managerCycle
  },
  get 'Account provisioning is disabled'() {
    return copy.provisioningDisabled
  },
  get 'An account request is already pending'() {
    return copy.provisioningPending
  },
  get 'Idempotency-Key was used with different data'() {
    return copy.idempotencyConflict
  },
  get 'Invalid email or password'() {
    return copy.invalidCredentials
  },
  get 'User is inactive'() {
    return copy.inactiveAccount
  },
  get 'Email already exists'() {
    return copy.accountEmailExists
  },
  get 'Employee already has an account'() {
    return copy.employeeAccountExists
  },
  get 'Invalid refresh token'() {
    return copy.sessionExpired
  },
  get 'Shared session requires HTTPS and Web Locks support'() {
    return copy.unsupportedSharedSession
  },
  get 'Access is denied'() {
    return copy.forbidden
  },
}
export function errorMessage(error, unavailableMessage = copy.authUnavailable) {
  if (!(error instanceof ApiError)) return copy.unexpected
  if (messages[error.message]) return messages[error.message]
  if (error.status === 429) {
    const seconds = Number(error.retryAfter)
    return Number.isFinite(seconds) && seconds > 0
      ? copy.retryAfterMinutes({ minutes: Math.ceil(seconds / 60) })
      : copy.tooManyRequests
  }
  if (error.status === 401) return copy.sessionExpired
  if (error.status === 403) return copy.forbidden
  if (error.status === 404) return copy.notFound
  if (error.status === 409) return copy.conflict
  if (error.status === 400) return copy.invalidInput
  return unavailableMessage
}
export function serviceError(error) {
  return errorMessage(error, copy.serviceUnavailable)
}
