import { auth } from '../auth/session.js'

// Fetch keeps the Bearer token in headers and shares the normal 401 refresh flow.
// EventSource cannot send an Authorization header.
const subscribers = new Set()
const accountSubscribers = new Set()
let stopSharedStream
export function subscribeNotifications(onChange) {
  return subscribe(subscribers, onChange)
}
export function subscribeAccountEvents(onChange) {
  return subscribe(accountSubscribers, onChange)
}
function subscribe(target, onChange) {
  target.add(onChange)
  if (!stopSharedStream)
    stopSharedStream = openNotificationStream(
      () => {
        for (const subscriber of subscribers) subscriber()
      },
      (items) => {
        for (const subscriber of accountSubscribers) subscriber(items)
      },
    )
  return () => {
    target.delete(onChange)
    if (!subscribers.size && !accountSubscribers.size) {
      stopSharedStream?.()
      stopSharedStream = undefined
    }
  }
}

function openNotificationStream(onChange, onAccounts) {
  const controller = new AbortController()
  let retryTimer
  let revision
  const cursorKey = `company-hrm.account-cursor.${auth.state.user.id}`
  let accountCursor
  try {
    const saved = sessionStorage.getItem(cursorKey)
    if (saved !== null && Number.isSafeInteger(Number(saved)) && Number(saved) >= 0)
      accountCursor = Number(saved)
  } catch {
    /* Reconnect still retains the cursor in memory. */
  }
  async function connect() {
    try {
      const query = accountCursor === undefined ? '' : `?accountAfter=${accountCursor}`
      const body = await auth.request(`/api/v1/notifications/stream${query}`, {
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
              } else if (event === 'account-results' && data.length && !controller.signal.aborted) {
                const batch = JSON.parse(data.join('\n'))
                if (
                  Number.isSafeInteger(batch.cursor) &&
                  batch.cursor >= 0 &&
                  Array.isArray(batch.items)
                ) {
                  onAccounts(batch.items)
                  accountCursor = batch.cursor
                  try {
                    sessionStorage.setItem(cursorKey, String(accountCursor))
                  } catch {
                    /* Optional persistence. */
                  }
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
