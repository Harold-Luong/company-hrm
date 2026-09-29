import { describe, expect, it } from 'vitest'
import { calendarSource, dayEntries, monthCells, detailDates } from '../../src/calendar/data.js'
import database from '../../src/assets/everrise_vn_event_calendar_mock_db.json'

describe('calendar date and holiday mapping', () => {
  it('reads independent snapshots of the supplied data without inventing events', async () => {
    const data = await calendarSource.load()
    expect(data.events).toHaveLength(16)
    data.events.pop()
    expect((await calendarSource.load()).events).toHaveLength(16)
  })
  it.each([
    ['2026-01-01', 'national_holiday'],
    ['2026-01-02', 'paid_leave_deduction'],
    ['2026-02-13', 'company_granted_holiday'],
    ['2026-02-16', 'statutory_holiday'],
    ['2026-02-23', 'company_granted_holiday'],
    ['2026-04-27', 'substitute_holiday'],
    ['2026-05-01', 'national_holiday'],
    ['2027-05-03', 'substitute_holiday'],
    ['2027-10-11', 'japan_national_holiday'],
    ['2027-11-24', 'calendar_event'],
  ])('uses the category or explicit detail for %s', (date, type) => {
    expect(dayEntries(database.events, date)[0].type).toBe(type)
  })
  it('does not extend events beyond inclusive dates or infer statutory days in gaps', () => {
    expect(dayEntries(database.events, '2026-02-25')).toEqual([])
    expect(dayEntries(database.events, '2026-02-14')[0].details).toEqual([])
    const tet = database.events.find((event) => event.id === '2027-02')
    expect(detailDates(tet.details[0])).toBe('04/02/2027 – 10/02/2027')
    expect(tet.details[0].note_vi).toBe('5 ngày nghỉ Tết theo quy định')
  })
  it('lays out Monday-first weeks and handles leap years independently of timezone', () => {
    const january = monthCells(2026, 1)
    expect(january).toHaveLength(42)
    expect(january.slice(0, 3)).toEqual([null, null, null])
    expect(january[3].date).toBe('2026-01-01')
    expect(january[5].weekend).toBe(true)
    expect(monthCells(2027, 2).filter(Boolean)).toHaveLength(28)
    expect(monthCells(2028, 2).filter(Boolean)).toHaveLength(29)
  })
})
