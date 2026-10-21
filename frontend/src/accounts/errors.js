import { ApiError, serviceError } from '../api/errors.js'
import { accountManagement as copy } from '../content/accounts.js'

export function accountManagementError(error) {
  if (error instanceof ApiError) {
    if (error.status === 412) return copy.errors.stale
    if (error.message === 'ACCOUNT_SELF_CHANGE_FORBIDDEN') return copy.selfHint
    if (error.message === 'ACCOUNT_ROLES_REQUIRED') return copy.errors.rolesRequired
    if (error.message === 'ACCOUNT_ROLES_UNCHANGED') return copy.errors.rolesUnchanged
    if (error.status === 409) return copy.errors.conflict
  }
  return serviceError(error)
}
