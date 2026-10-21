import { pagination } from '../config/presentation.js'
import { requestJson } from '../api/client.js'
import { idempotencyHeader } from '../api/headers.js'
import { hrmEndpoints as endpoints } from './endpoints.js'

// Preserve existing imports; new UI code can import constants and utils directly.
export { employeeStatuses, accountStatuses, requestStatuses } from './constants.js'
export { fullName, uuidPattern } from './utils.js'

export const hrm = {
  list: (resource, page = 0, size = pagination.pageSize) =>
    requestJson(endpoints.collection(resource), { query: { page, size } }),
  get: (resource, id) => requestJson(endpoints.detail(resource, id)),
  save: (resource, id, body) =>
    requestJson(id ? endpoints.detail(resource, id) : endpoints.collection(resource), {
      method: id ? 'PUT' : 'POST',
      body,
    }),
  status: (id, status) => requestJson(endpoints.status(id), { method: 'PATCH', body: { status } }),
  provision: (id, email, key) =>
    requestJson(endpoints.accountRequests(id), {
      method: 'POST',
      headers: idempotencyHeader(key),
      body: { email },
    }),
  request: (id, requestId) => requestJson(endpoints.accountRequest(id, requestId)),
  activationInvitation: (employeeId) => requestJson(endpoints.activationInvitation(employeeId)),
  resendActivationInvitation: (employeeId) =>
    requestJson(endpoints.activationInvitation(employeeId), { method: 'POST' }),
}
