export const API_PREFIX = '/api/v1'

// Each argument is one path segment, including dynamic identifiers and actions.
export const apiPath = (...segments) =>
  `${API_PREFIX}/${segments.map((segment) => encodeURIComponent(segment)).join('/')}`
