import { roleNames as copy } from '../content/auth.js'
import { common } from '../content/common.js'
export const roles = ['EMPLOYEE', 'MANAGER', 'HR', 'ADMIN']
export const personnelManagementRoles = ['HR', 'ADMIN']
export const roleLabels = {
  get EMPLOYEE() {
    return common.employee
  },
  get MANAGER() {
    return copy.manager
  },
  get HR() {
    return copy.hr
  },
  get ADMIN() {
    return copy.admin
  },
}
