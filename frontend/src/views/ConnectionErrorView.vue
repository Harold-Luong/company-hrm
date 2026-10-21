<script setup>
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'
import { connectionError as copy } from '@/content/auth.js'
import { common } from '@/content/common.js'

import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { safeDestination } from '@/auth/navigation.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const busy = ref(false)
const message = useLocalizedMessage()
async function retry() {
    busy.value = true
    await router.replace(safeDestination(route.query.redirect))
    if (route.name === 'connection-error') message.value = () => copy.unavailable
    busy.value = false
}
async function backToLogin() {
    busy.value = true
    try {
        await auth.logout()
    } catch {
        /* Local credentials are cleared even when offline. */
    }
    await router.replace({ name: 'login', query: { logout: 'local' } })
    busy.value = false
}
</script>
<template>
    <main class="connection-page">
        <section class="panel empty-state">
            <div class="swith-lang">
                <LanguageSwitcher />
            </div>

            <span class="empty-icon">
                <AppIcon name="info" :size="34" />
            </span>
            <p class="eyebrow">{{ copy.eyebrow }}</p>
            <h1>{{ copy.title }}</h1>
            <p>{{ copy.description }}</p>
            <p v-if="message" class="alert alert-warning" role="status">{{ message }}</p>
            <div class="button-row">
                <button class="button button-primary" :disabled="busy" @click="retry">
                    {{ busy ? common.checking : copy.retry }}</button><button
class="button button-secondary"
                    :disabled="busy" @click="backToLogin">
                    {{ copy.login }}
                </button>
            </div>
        </section>
    </main>
</template>
<style scoped>
.swith-lang {
    position: absolute;
    top: 0;
    right: 0;
    padding: 10px 20px;
}
</style>