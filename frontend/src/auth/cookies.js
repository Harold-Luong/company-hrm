import { authRequest } from './api.js'
import { ApiError } from './api.js'

// Read a masked CSRF token from JSON; both cookies remain HttpOnly.
// Call inside the shared session lock so first-time CSRF cookies cannot race.
export async function csrfSession() {
  const data = await authRequest('/csrf', { credentials: 'same-origin' })
  if (typeof data.token !== 'string' || !data.token || data.headerName !== 'X-XSRF-TOKEN') {
    throw new ApiError(502, 'Invalid CSRF response')
  }
  return data
}

export async function cookieAuthRequest(path, init = {}) {
  const csrf = await csrfSession()
  return authRequest(path, {
    ...init,
    credentials: 'same-origin',
    headers: { ...init.headers, [csrf.headerName]: csrf.token },
  })
}
