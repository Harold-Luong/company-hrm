import { describe, expect, it } from 'vitest'
import {
  sortManagedEvents,
  eventActions,
  eventPayload,
  eventToForm,
  newEventForm,
} from '../../src/calendar/management.js'
const timed = {
  id: 1,
  title: 'Meeting',
  description: null,
  type: 'MEETING',
  holidayKind: null,
  allDay: false,
  startDate: null,
  endDate: null,
  startAt: '2026-01-15T16:00:00.123Z',
  endAt: '2026-01-15T17:00:00Z',
  location: null,
  status: 'DRAFT',
  version: 5,
}
describe('calendar management forms', () => {
  it('round-trips Vietnam time and excludes server metadata', () => {
    const form = eventToForm(timed)
    expect(form.startTime).toBe('2026-01-15T23:00:00.123')
    expect(form.endTime).toBe('2026-01-16T00:00:00')
    expect(eventPayload(form)).toEqual({
      title: 'Meeting',
      description: null,
      type: 'MEETING',
      holidayKind: null,
      allDay: false,
      startDate: null,
      endDate: null,
      startAt: timed.startAt,
      endAt: '2026-01-15T17:00:00.000Z',
      timezone: 'Asia/Ho_Chi_Minh',
      location: null,
      audienceType: 'ALL',
    })
  })
  it('clears inactive timing fields and trims optional values', () => {
    const form = {
      ...newEventForm(),
      title: ' Holiday ',
      description: ' ',
      reason: ' Change ',
      startTime: 'old value',
      endTime: 'old value',
    }
    expect(eventPayload(form)).toMatchObject({
      title: 'Holiday',
      description: null,
      reason: 'Change',
      allDay: true,
      startAt: null,
      endAt: null,
    })
  })
  it('rejects reversed and zero-duration ranges', () => {
    const form = eventToForm(timed)
    form.endTime = form.startTime
    expect(() => eventPayload(form)).toThrow('Giờ kết thúc')
    expect(() =>
      eventPayload({
        ...newEventForm(),
        title: 'Holiday',
        startDate: '2026-02-02',
        endDate: '2026-02-01',
      }),
    ).toThrow('Ngày kết thúc')
  })
  it('follows lifecycle boundaries for today and cancelled events', () => {
    const now = Date.parse('2026-01-15T05:00:00Z')
    const holiday = { allDay: true, startDate: '2026-01-15', status: 'DRAFT' }
    expect(eventActions(holiday, now)).toEqual({
      edit: true,
      publish: true,
      cancel: false,
      remove: true,
    })
    expect(eventActions({ ...holiday, status: 'PUBLISHED' }, now)).toEqual({
      edit: false,
      publish: false,
      cancel: false,
      remove: false,
    })
    expect(eventActions({ ...timed, status: 'PUBLISHED' }, now)).toEqual({
      edit: true,
      publish: false,
      cancel: true,
      remove: false,
    })
    expect(eventActions({ ...timed, status: 'CANCELLED' }, now)).toEqual({
      edit: false,
      publish: false,
      cancel: false,
      remove: false,
    })
  })
})

describe('management list ordering', () => {
  it('prioritizes drafts then proximity to today in Vietnam without mutating API data', () => {
    const events = [
      { id: 1, status: 'PUBLISHED', allDay: true, startDate: '2026-01-15', endDate: '2026-01-15' },
      { id: 2, status: 'DRAFT', allDay: true, startDate: '2026-02-15', endDate: '2026-02-15' },
      { id: 3, status: 'DRAFT', allDay: true, startDate: '2026-01-16', endDate: '2026-01-16' },
      { id: 4, status: 'DRAFT', allDay: true, startDate: '2026-01-14', endDate: '2026-01-14' },
      {
        id: 5,
        status: 'DRAFT',
        allDay: false,
        startAt: '2026-01-14T17:30:00Z',
        endAt: '2026-01-14T18:30:00Z',
      },
      { id: 6, status: 'CANCELLED', allDay: true, startDate: '2026-01-13', endDate: '2026-01-13' },
      { id: 7, status: 'PUBLISHED', allDay: true, startDate: '2026-01-20', endDate: '2026-01-20' },
    ]
    expect(sortManagedEvents(events, '2026-01-15').map((event) => event.id)).toEqual([
      5, 4, 3, 2, 1, 6, 7,
    ])
    expect(events.map((event) => event.id)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })
})
