<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { safeDestination } from '@/auth/navigation.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const busy = ref(false)
const message = ref('')
async function retry() {
    busy.value = true
    await router.replace(safeDestination(route.query.redirect))
    if (route.name === 'connection-error')
        message.value = 'Dịch vụ vẫn chưa phản hồi. Vui lòng thử lại sau.'
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
            <span class="empty-icon">
                <AppIcon name="info" :size="34" />
            </span>
            <p class="eyebrow">KẾT NỐI GIÁN ĐOẠN</p>
            <h1>Chưa thể mở workspace</h1>
            <p>Không thể xác minh phiên truy cập lúc này. Vui lòng kiểm tra kết nối và thử lại.</p>
            <p v-if="message" class="alert alert-warning" role="status">{{ message }}</p>
            <div class="button-row">
                <button class="button button-primary" :disabled="busy" @click="retry">
                    {{ busy ? 'Đang kiểm tra…' : 'Thử kết nối lại' }}</button><button class="button button-secondary"
                    :disabled="busy" @click="backToLogin">
                    Về đăng nhập
                </button>
            </div>
        </section>
    </main>
</template>
