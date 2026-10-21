// Shared bootstrap for UI tests whose authentication endpoints are mocked.
export const csrfResponse = {
  token: 'test-csrf',
  headerName: 'X-XSRF-TOKEN',
  sessionPresent: false,
}
export function isCsrfRequest(route) {
  return new URL(route.request().url()).pathname === '/api/v1/auth/csrf'
}
