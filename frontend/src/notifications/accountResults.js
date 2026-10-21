import { refreshIntervals } from '../config/runtime.js'
import { accountResultMessages as copy } from '../content/notifications.js'
import { ref } from 'vue'
import { auth } from '../auth/session.js'
import { hrm } from '../hrm/api.js'
import { uuidPattern } from '../hrm/utils.js'
import { subscribeAccountEvents } from './stream.js'

export const accountToasts = ref([])
export const provisioningFailures = {
  get ACCOUNT_ALREADY_EXISTS() {
    return copy.employeeAccountExists
  },
  get EMAIL_ALREADY_USED() {
    return copy.emailExists
  },
}
const listeners = new Set()
let owner
let records = new Map()
let running = false
let generation = 0
const terminal = (data) => ['SUCCEEDED', 'FAILED'].includes(data.provisioningStatus)
const storageKey = () => `company-hrm.account-tracking.${owner}`
function scope() {
  const nextOwner = String(auth.state.user?.id ?? '')
  if (owner === nextOwner) return
  owner = nextOwner
  records = new Map()
  accountToasts.value = []
  try {
    for (const record of JSON.parse(sessionStorage.getItem(storageKey()) || '[]')) {
      if (uuidPattern.test(record.employeeId) && uuidPattern.test(record.requestId))
        records.set(record.requestId, { ...record, busy: false, nextAt: 0 })
    }
  } catch {
    /* Storage is optional; live updates still work. */
  }
}
function persist() {
  try {
    sessionStorage.setItem(storageKey(), JSON.stringify([...records.values()]))
  } catch {
    /* Keep tracking in memory if storage is unavailable. */
  }
}
export function dismissAccountToast(requestId) {
  accountToasts.value = accountToasts.value.filter((item) => item.requestId !== requestId)
}
export function subscribeAccountResults(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
// Call only for a request submitted by the current user, or a recipient-scoped SSE event.
export function trackAccountRequest(employeeId, data) {
  scope()
  if (!uuidPattern.test(employeeId) || !uuidPattern.test(data.requestId)) return
  const previous = records.get(data.requestId)
  if (previous && terminal(previous)) return
  const record = { ...previous, ...data, employeeId, nextAt: 0 }
  records.set(data.requestId, record)
  // Persist completion before notifying, so SSE, polling and reload cannot repeat the toast.
  persist()
  if (terminal(record)) {
    accountToasts.value.push(record)
    for (const listener of listeners) listener(record)
  }
}
async function refresh(record) {
  if (!running || record.busy || terminal(record)) return
  record.busy = true
  const startedGeneration = generation
  try {
    const result = await hrm.request(record.employeeId, record.requestId)
    if (!running || startedGeneration !== generation || String(auth.state.user?.id ?? '') !== owner)
      return
    trackAccountRequest(record.employeeId, result)
  } catch {
    // Employee may still be consuming the Auth result, or temporarily unavailable.
  } finally {
    if (startedGeneration === generation) {
      const current = records.get(record.requestId)
      if (current) {
        current.busy = false
        current.attempts = (current.attempts || 0) + 1
        current.nextAt = Date.now() + (current.attempts < 30 ? 3000 : 30000)
      }
    }
  }
}
export function startAccountNotifications() {
  scope()
  running = true
  generation++
  for (const record of records.values()) record.busy = false
  const stop = subscribeAccountEvents((items) => {
    for (const item of items) {
      // Confirm with Employee before updating the UI: its Kafka consumer can lag behind Auth.
      if (!records.has(item.requestId))
        trackAccountRequest(item.employeeId, {
          requestId: item.requestId,
          provisioningStatus: 'PENDING',
        })
      const record = records.get(item.requestId)
      if (record) refresh(record)
    }
  })
  const poll = () => {
    for (const record of records.values()) if (record.nextAt <= Date.now()) refresh(record)
  }
  poll()
  const timer = window.setInterval(poll, refreshIntervals.accountResults)
  return () => {
    running = false
    generation++
    stop()
    window.clearInterval(timer)
    accountToasts.value = []
  }
}
