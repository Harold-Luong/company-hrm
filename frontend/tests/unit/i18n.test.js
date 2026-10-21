import { afterEach, describe, expect, it, vi as mock } from 'vitest'
import { computed, effectScope, watchEffect } from 'vue'
import { baseCompile } from '@intlify/message-compiler'
import i18n, {
  getDisplayLocale,
  setLocale,
  startLocaleSync,
  translate,
} from '../../src/i18n/index.js'
import vi from '../../src/locales/vi.json'
import en from '../../src/locales/en.json'
import { activation } from '../../src/content/auth.js'
import { attendanceRequests } from '../../src/content/attendance.js'
import { provisioning } from '../../src/content/hrm.js'
import { useLocalizedMessage } from '../../src/composables/useLocalizedMessage.js'
import { employeeStatuses } from '../../src/hrm/constants.js'
import { calendarWeekdays } from '../../src/shared/intl.js'
import { calendarDate, dateLabel, timeLabel } from '../../src/shared/date.js'
import { features, helpGroups } from '../../src/help/content.js'
import { fieldLimits } from '../../src/config/validation.js'
import { provisioningLookup } from '../../src/config/runtime.js'

const flatten = (value, prefix = '') =>
  Object.fromEntries(
    Object.entries(value).flatMap(([key, item]) =>
      typeof item === 'string'
        ? [[`${prefix}${key}`, item]]
        : Object.entries(flatten(item, `${prefix}${key}.`)),
    ),
  )
const placeholders = (text) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort()
afterEach(() => {
  mock.restoreAllMocks()
  setLocale('vi')
})

describe('translation catalogs', () => {
  it('has identical keys and interpolation parameters in both languages; every message compiles', () => {
    const vietnamese = flatten(vi),
      english = flatten(en)
    expect(Object.keys(english).sort()).toEqual(Object.keys(vietnamese).sort())
    for (const [key, text] of Object.entries(vietnamese)) {
      expect(placeholders(english[key]), key).toEqual(placeholders(text))
      for (const message of [text, english[key]]) {
        const errors = []
        baseCompile(message, { onError: (error) => errors.push(error.message) })
        expect(errors, key).toEqual([])
      }
    }
  })
  it('reacts to locale changes without recreating content objects, labels or open messages', () => {
    const scope = effectScope()
    let rendered
    scope.run(() => {
      const message = useLocalizedMessage(() => activation.invalidToken)
      watchEffect(
        () => {
          rendered = [
            activation.actions.submitting,
            employeeStatuses.ACTIVE,
            message.value,
            features[0].title,
            helpGroups[0].label,
          ]
        },
        { flush: 'sync' },
      )
    })
    const previous = [...rendered]
    setLocale('en')
    expect(rendered[0]).toBe(en.activation.actions.submitting)
    expect(rendered.every((value, index) => value !== previous[index])).toBe(true)
    scope.stop()
  })
  it('interpolates configuration and named arguments in both languages', () => {
    for (const locale of ['vi', 'en']) {
      setLocale(locale)
      expect(attendanceRequests.reasonLength({ count: 12 })).toContain('12')
      expect(attendanceRequests.reasonLength({ count: 12 })).toContain(
        fieldLimits.attendanceReason.toLocaleString(getDisplayLocale()),
      )
      expect(provisioning.pollingNotice).toContain(String(provisioningLookup.maxAttempts))
      expect(provisioning.pollingNotice).toContain(String(provisioningLookup.intervalMs / 1000))
      expect(translate('common.emailPlaceholder')).not.toContain("{'@'}")
    }
  })
  it('changes display formats while preserving the business date and timezone', () => {
    const value = new Date('2026-04-01T18:30:00Z')
    const display = computed(() => [
      dateLabel('2026-04-02'),
      timeLabel(value),
      calendarWeekdays.value[0],
    ])
    const previous = [...display.value]
    expect(calendarDate(value)).toBe('2026-04-02')
    setLocale('en')
    expect(calendarDate(value)).toBe('2026-04-02')
    expect(display.value[0]).toBe('04/02/2026')
    expect(display.value[2]).toBe('Mon')
    expect(display.value).not.toEqual(previous)
  })
})

describe('locale preferences', () => {
  it('persists supported locales, updates document language and rejects unknown locales', () => {
    expect(setLocale('en')).toBe(true)
    expect(localStorage.getItem('locale')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    expect(setLocale('xx')).toBe(false)
    expect(i18n.global.locale.value).toBe('en')
  })
  it('synchronizes another tab and removes its listener when disposed', () => {
    const stop = startLocaleSync()
    window.dispatchEvent(new StorageEvent('storage', { key: 'locale', newValue: 'en' }))
    expect(i18n.global.locale.value).toBe('en')
    window.dispatchEvent(new StorageEvent('storage', { key: 'locale', newValue: 'invalid' }))
    expect(i18n.global.locale.value).toBe('en')
    stop()
    window.dispatchEvent(new StorageEvent('storage', { key: 'locale', newValue: 'vi' }))
    expect(i18n.global.locale.value).toBe('en')
  })
  it('still switches when browser storage is unavailable', () => {
    mock.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => setLocale('en')).not.toThrow()
    expect(i18n.global.locale.value).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
