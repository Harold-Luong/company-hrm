export const accountManagementRoles = ['ADMIN']
export const accountStatuses = ['ACTIVE', 'DISABLED', 'PENDING_ACTIVATION']
export const accountActions = Object.freeze({
  disable: 'DISABLE',
  enable: 'ENABLE',
  changeRoles: 'CHANGE_ROLES',
  revokeSessions: 'REVOKE_SESSIONS',
})
export const accountLimits = { search: 255, reason: 500 }
