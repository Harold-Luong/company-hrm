import { watchEffect } from 'vue'
import { getRouteTitle } from './title.js'
import { common } from '../content/common.js'
import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/auth/session.js'
import { ApiError } from '@/api/errors.js'
import { safeDestination } from '@/auth/navigation.js'
import { routes } from './routes.js'

export { routes } from './routes.js'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: (to) =>
    to.path.startsWith('/help/') && to.hash ? { el: to.hash, top: 24 } : { top: 0 },
})
router.beforeEach(async (to) => {
  if (to.name === 'connection-error' || to.name === 'activate') return true
  try {
    const wasInitialized = auth.state.initialized
    await auth.initialize()
    // Re-read current roles at each protected navigation, not from a JWT/local cache.
    if (wasInitialized && auth.authenticated.value && (to.meta.requiresAuth || to.name === 'login'))
      await auth.loadUser()
  } catch (error) {
    if (!(error instanceof ApiError) || ![401, 403, 404].includes(error.status)) {
      return { name: 'connection-error', query: { redirect: safeDestination(to.path) } }
    }
  }
  if (to.meta.requiresAuth && !auth.authenticated.value) {
    return { name: 'login', query: { redirect: safeDestination(to.path) } }
  }
  if (to.name === 'login' && auth.authenticated.value) return safeDestination(to.query.redirect)
  if (to.meta.roles && !auth.hasRole(to.meta.roles)) return { name: 'forbidden' }
  return true
})
watchEffect(() => {
  document.title = `${getRouteTitle(router.currentRoute.value)} | ${common.appName}`
})
export default router
