import { requestJson } from '../api/client.js'
import { apiPath } from '../api/config.js'

export function openNotificationStream(accountAfter, signal) {
  return requestJson(apiPath('notifications', 'stream'), {
    query: { accountAfter },
    responseType: 'stream',
    headers: { Accept: 'text/event-stream' },
    signal,
  })
}
