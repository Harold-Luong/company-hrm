import { computed } from 'vue'
import { getDisplayLocale } from '../i18n/index.js'

export function localizedDateFormatter(options) {
  const formatters = new Map()
  return {
    format(value) {
      const locale = getDisplayLocale()
      if (!formatters.has(locale)) formatters.set(locale, new Intl.DateTimeFormat(locale, options))
      return formatters.get(locale).format(value)
    },
  }
}

export const calendarWeekdays = computed(() => {
  const formatter = new Intl.DateTimeFormat(getDisplayLocale(), {
    weekday: 'short',
    timeZone: 'UTC',
  })
  return Array.from({ length: 7 }, (_, index) =>
    formatter.format(new Date(Date.UTC(2024, 0, 1 + index))),
  )
})
