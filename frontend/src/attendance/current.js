import { auth } from '@/auth/session.js'
import { attendance } from './api.js'
import { today } from '../shared/date.js'

export async function currentAttendance() {
    let currentDate = today()
    const prior = new Date(`${currentDate}T12:00:00Z`)
    prior.setUTCDate(prior.getUTCDate() - 1)
    const previousDate = prior.toISOString().slice(0, 10)
    const [day, plan] = await Promise.all([
        attendance.mine(previousDate, currentDate),
        attendance.schedule(previousDate, currentDate),
    ])
    const previousPlan = plan.find((p) => p.date === previousDate)
    const previousRow = day.find((r) => r.workDate === previousDate)
    const overnightOpen = previousPlan?.definition?.overnight && previousRow?.status === 'OPEN'
    const priorWindowEnd = previousPlan?.definition?.overnight
        ? new Date(`${currentDate}T${previousPlan.definition.checkOutUntil}+07:00`)
        : null
    const lateNightEntry = priorWindowEnd && new Date() <= priorWindowEnd && !previousRow?.checkIn
    if (overnightOpen || lateNightEntry) currentDate = previousDate
    return {
        currentDate,
        row: day.find((r) => r.workDate === currentDate) || null,
        schedule: plan.find((p) => p.date === currentDate) || null,
    }
}

export function recordAttendance(action, workDate) {
    const storage = `attendance:${auth.state.user?.id}:${workDate}:${action}`
    let key = sessionStorage.getItem(storage)
    if (!key) {
        key = crypto.randomUUID()
        sessionStorage.setItem(storage, key)
    }
    return attendance.punch(action, key)
}
