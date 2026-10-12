import { auth } from '../auth/session.js'

// Fetch keeps the Bearer token in headers and shares the normal 401 refresh flow.
// EventSource cannot send an Authorization header.
const subscribers = new Set()
let stopSharedStream
export function subscribeNotifications(onChange) {
  subscribers.add(onChange)
  if (!stopSharedStream)
    stopSharedStream = openNotificationStream(() => {
      for (const subscriber of subscribers) subscriber()
    })
  return () => {
    subscribers.delete(onChange)
    if (!subscribers.size) {
      stopSharedStream?.()
      stopSharedStream = undefined
    }
  }
}

function openNotificationStream(onChange) {
  const controller = new AbortController()
  let retryTimer
  let revision
  async function connect() {
    try {
      const body = await auth.request('/api/v1/notifications/stream', {
        responseType: 'stream',
        headers: { Accept: 'text/event-stream' },
        signal: controller.signal,
      })
      const reader = body.getReader()
      const decoder = new TextDecoder()
      let buffer = '',
        event = '',
        data = []
      try {
        while (!controller.signal.aborted) {
          const chunk = await reader.read()
          if (chunk.done) break
          buffer += decoder.decode(chunk.value, { stream: true })
          let end
          while ((end = buffer.indexOf('\n')) !== -1) {
            const line = buffer.slice(0, end).replace(/\r$/, '')
            buffer = buffer.slice(end + 1)
            if (!line) {
              if (event === 'notifications' && data.length) {
                const snapshot = JSON.parse(data.join('\n'))
                if (
                  Number.isSafeInteger(snapshot.revision) &&
                  snapshot.revision >= 0 &&
                  snapshot.revision !== revision
                ) {
                  revision = snapshot.revision
                  if (!controller.signal.aborted) onChange()
                }
              }
              event = ''
              data = []
            } else if (line.startsWith('event:')) event = line.slice(6).trim()
            else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
          }
        }
      } finally {
        await reader.cancel().catch(() => {})
        reader.releaseLock()
      }
    } catch {
      // Counts retain their polling fallback if notifications are unavailable.
    } finally {
      if (!controller.signal.aborted) retryTimer = window.setTimeout(connect, 3000)
    }
  }
  connect()
  return () => {
    controller.abort()
    window.clearTimeout(retryTimer)
  }
}
