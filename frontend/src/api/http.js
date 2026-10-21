import { requestTimeouts } from '../config/runtime.js'
import { ApiError } from './errors.js'

export async function apiRequest(path, init = {}) {
  const { responseType = 'json', ...requestInit } = init
  let response
  try {
    response = await fetch(path, {
      ...requestInit,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
      signal: AbortSignal.any([
        AbortSignal.timeout(
          responseType === 'stream' ? requestTimeouts.stream : requestTimeouts.json,
        ),
        ...(init.signal ? [init.signal] : []),
      ]),
      cache: 'no-store',
    })
  } catch {
    throw new ApiError(0, 'Network unavailable')
  }
  if (response.ok && responseType === 'blob') return response.blob()
  if (response.ok && responseType === 'stream') {
    if (!response.headers.get('Content-Type')?.startsWith('text/event-stream') || !response.body) {
      await response.body?.cancel()
      throw new ApiError(502, 'Invalid event stream')
    }
    return response.body
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'message' in data
        ? String(data.message)
        : data?.detail || response.statusText
    throw new ApiError(response.status, message, response.headers.get('Retry-After'))
  }
  if (response.status === 204) return null
  if (!data || typeof data !== 'object') throw new ApiError(502, 'Invalid server response')
  return data
}
