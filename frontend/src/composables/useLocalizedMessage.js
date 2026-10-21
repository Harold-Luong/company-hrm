import { customRef } from 'vue'

// Keep a resolver so an already-visible validation message follows locale changes.
export function useLocalizedMessage(initialValue = '') {
  let value = initialValue
  return customRef((track, trigger) => ({
    get() {
      track()
      return typeof value === 'function' ? value() : value
    },
    set(next) {
      value = next
      trigger()
    },
  }))
}
