export const requestTimeouts = { json: 15_000, stream: 75_000 }

export const refreshIntervals = {
  session: 15_000,
  notificationReconnect: 3000,
  accountResults: 3000,
  pendingCounts: 30_000,
  personalLeave: 30_000,
  eventState: 30_000,
}

export const provisioningLookup = { intervalMs: 3000, maxAttempts: 30 }
