import { apiRequest } from '../api/http.js'
import { apiPath } from '../api/config.js'

// Compatibility exports for existing callers; shared HTTP code lives in api/.
export { ApiError, errorMessage, serviceError } from '../api/errors.js'
export { apiRequest } from '../api/http.js'

export function authRequest(path, init = {}) {
  return apiRequest(`${apiPath('auth')}${path}`, init)
}

export function activateAccount(token, password, signal) {
  return authRequest('/activate', {
    method: 'POST',
    credentials: 'omit',
    signal,
    body: JSON.stringify({ token, password }),
  })
}
