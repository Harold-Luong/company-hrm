import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/auth/session.js'
import { ApiError } from '@/auth/api.js'
import { accountCreationRoles, safeDestination } from '@/auth/navigation.js'
export const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: 'Đăng nhập' },
  },
  {
    path: '/connection-error',
    name: 'connection-error',
    component: () => import('@/views/ConnectionErrorView.vue'),
    meta: { title: 'Kết nối gián đoạn' },
  },
  {
    path: '/',
    component: () => import('@/layouts/WorkspaceLayout.vue'),
    meta: { title: 'Không gian làm việc', requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: 'Tổng quan' },
      },
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/AccountView.vue'),
        meta: { title: 'Tài khoản của tôi' },
      },
      {
        path: 'accounts/new',
        name: 'create-account',
        component: () => import('@/views/CreateAccountView.vue'),
        meta: { title: 'Tạo tài khoản', roles: accountCreationRoles },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: 'Quyền truy cập' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: 'Không tìm thấy trang' },
      },
    ],
  },
]
export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
  if (to.name === 'connection-error') return true
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
router.afterEach((to) => {
  document.title = `${to.meta.title} | Company HRM`
})
export default router
