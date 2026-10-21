import { common } from '../content/common.js'
export const minutesLabel = (value) =>
  value == null
    ? common.unknown
    : common.hoursMinutes({ hours: Math.floor(value / 60), minutes: value % 60 })
export const secondsLabel = (value) =>
  value == null
    ? common.unknown
    : `${minutesLabel(Math.floor(value / 60))}${value % 60 ? common.secondsSuffix({ seconds: value % 60 }) : ''}`

export const minuteOfDay = (time) => {
  const [hour, minute] = time.split(':').map(Number)
  return hour * 60 + minute
}
