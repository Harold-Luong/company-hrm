import { watch } from 'vue'

// Reuse the key after an uncertain response; editing the payload starts a new attempt.
export function useIdempotencyKey(source) {
  let key = ''
  const reset = () => {
    key = ''
  }
  watch(source, reset, { deep: true, flush: 'sync' })
  return {
    get: () => (key ||= crypto.randomUUID()),
    reset,
  }
}
