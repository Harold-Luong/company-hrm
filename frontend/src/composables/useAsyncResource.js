import { useLocalizedMessage } from './useLocalizedMessage.js'
import { onScopeDispose, ref, watch } from 'vue'
import { serviceError } from '../api/errors.js'

// Read operations only. A newer load or disposed scope invalidates older responses.
export function useAsyncResource(
  loader,
  {
    initialValue = () => null,
    enabled = () => true,
    errorMessage = serviceError,
    resetOnLoad = true,
    source,
  } = {},
) {
  const data = ref(initialValue())
  const loading = ref(false)
  const error = useLocalizedMessage()
  let revision = 0
  let disposed = false

  async function load(...args) {
    if (disposed) return
    const current = ++revision
    error.value = ''
    if (resetOnLoad || !enabled()) data.value = initialValue()
    loading.value = false
    if (!enabled()) return
    loading.value = true
    try {
      const result = await loader(...args)
      if (current !== revision || disposed) return
      data.value = result
      return result
    } catch (cause) {
      if (current === revision && !disposed) error.value = () => errorMessage(cause)
    } finally {
      if (current === revision && !disposed) loading.value = false
    }
  }

  onScopeDispose(() => {
    disposed = true
    revision++
  })
  if (source) watch(source, () => void load(), { immediate: true, flush: 'sync' })
  return { data, loading, error, load }
}
