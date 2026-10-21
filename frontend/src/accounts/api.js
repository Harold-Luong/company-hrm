import { requestJson } from '../api/client.js'
import { apiPath } from '../api/config.js'
import { pagination } from '../config/presentation.js'

const endpoint = (...parts) => apiPath('auth', 'accounts', ...parts)
export const accountsApi = {
  list: (query, page = 0) =>
    requestJson(endpoint(), { query: { ...query, page, size: pagination.pageSize } }),
  get: (id) => requestJson(endpoint(id)),
  history: (id, page = 0) =>
    requestJson(endpoint(id, 'audit'), { query: { page, size: pagination.pageSize } }),
  execute: (id, command) => requestJson(endpoint(id, 'actions'), { method: 'POST', body: command }),
}
