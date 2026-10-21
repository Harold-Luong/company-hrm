import { apiPath } from '../api/config.js'

const resources = Object.freeze({
  employees: apiPath('employees'),
  departments: apiPath('departments'),
  positions: apiPath('positions'),
})

function collection(resource) {
  if (!Object.hasOwn(resources, resource)) {
    throw new TypeError(`Unknown HRM resource: ${resource}`)
  }
  return resources[resource]
}

const detail = (resource, id) => `${collection(resource)}/${encodeURIComponent(id)}`
const accountRequests = (id) => `${detail('employees', id)}/account-requests`

export const hrmEndpoints = {
  collection,
  detail,
  status: (id) => `${detail('employees', id)}/status`,
  accountRequests,
  accountRequest: (id, requestId) => `${accountRequests(id)}/${encodeURIComponent(requestId)}`,
  activationInvitation: (employeeId) => apiPath('auth', 'activation-invitations', employeeId),
}
