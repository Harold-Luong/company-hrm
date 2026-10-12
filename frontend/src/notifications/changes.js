export const requestChangedEvent = 'hrm:requests-changed'

export function notifyRequestChange(result) {
  window.dispatchEvent(new Event(requestChangedEvent))
  return result
}
