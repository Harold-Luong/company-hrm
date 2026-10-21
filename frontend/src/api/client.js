import { auth } from '../auth/session.js'

// JSON bodies and query parameters belong here; session handling stays in auth.request.
export function requestJson(path, { query, body, ...options } = {}) {
  const [pathname, search = ''] = path.split('?')
  const params = new URLSearchParams(search)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') params.set(key, value)
  }
  const queryString = params.toString()
  return auth.request(`${pathname}${queryString ? `?${queryString}` : ''}`, {
    ...options,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
}
