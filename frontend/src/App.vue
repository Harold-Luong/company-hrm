<script setup>
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { safeDestination } from '@/auth/navigation.js'
const route = useRoute()
const router = useRouter()
watch(
  () => auth.state.user,
  (user, previous) => {
    if (!user && previous && route.meta.requiresAuth) {
      void router.replace({ name: 'login', query: { redirect: safeDestination(route.path) } })
    }
  },
)
</script>

<template>
  <RouterView v-if="route.matched.length" />
  <div v-else class="loading-screen" role="status">
    <span class="brand-icon">C<span>h</span></span>
    <p>Đang mở không gian làm việc…</p>
  </div>
</template>
