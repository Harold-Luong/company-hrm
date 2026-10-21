<script setup>
import { refreshIntervals } from '@/config/runtime.js'
import { app as copy } from '@/content/workspace.js'
import { common } from '@/content/common.js'
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { safeDestination } from '@/auth/navigation.js'

const route = useRoute()
const router = useRouter()
let sessionTimer
let checkingSession = false
async function checkSession() {
    if (checkingSession || !auth.authenticated.value || document.visibilityState === 'hidden') return
    checkingSession = true
    try {
        await auth.checkSession()
    } catch {
        // Rejected sessions are cleared by auth; outages keep the session for retry.
    } finally {
        checkingSession = false
    }
}
onMounted(() => {
    sessionTimer = window.setInterval(checkSession, refreshIntervals.session)
    window.addEventListener('focus', checkSession)
    document.addEventListener('visibilitychange', checkSession)
})
onUnmounted(() => {
    window.clearInterval(sessionTimer)
    window.removeEventListener('focus', checkSession)
    document.removeEventListener('visibilitychange', checkSession)
})
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
        <span class="brand-icon">{{ common.monogramFirst }}<span>{{ common.monogramSecond }}</span></span>
        <p>{{ copy.openingWorkspace }}</p>
    </div>
</template>
