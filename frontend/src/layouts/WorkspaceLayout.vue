<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import AppIcon from '@/components/AppIcon.vue'
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const busy = ref(false)
const initials = computed(() => auth.state.user?.email.slice(0, 2).toUpperCase() || 'CH')
watch(
    () => route.fullPath,
    () => {
        mobileOpen.value = false
    },
)
async function signOut() {
    busy.value = true
    let warning = false
    try {
        await auth.logout()
    } catch {
        warning = true
    }
    await router.replace({
        name: 'login',
        query: warning ? { logout: 'local' } : { logout: 'success' },
    })
    busy.value = false
}
</script>

<template>
    <div v-if="auth.state.user" class="workspace" @keydown.esc="mobileOpen = false">
        <a class="skip-link" href="#main-content">Đến nội dung chính</a>
        <button v-if="mobileOpen" class="sidebar-overlay" aria-label="Đóng menu" @click="mobileOpen = false"></button>
        <aside id="workspace-sidebar" class="sidebar" :class="{ 'is-open': mobileOpen }" :inert="busy">
            <RouterLink to="/" class="brand"><span class="brand-icon">C<span>h</span></span><span>company<span
                        class="brand-light">hrm</span><small>WORKSPACE</small></span></RouterLink>
            <button class="mobile-sidebar-close icon-button" aria-label="Đóng menu" @click="mobileOpen = false">
                <AppIcon name="close" />
            </button>
            <div class="workspace-label">
                <span class="workspace-symbol">C</span>
                <div>Company workspace<small>Không gian nội bộ</small></div>
                <span class="status-dot"></span>
            </div>
            <nav class="sidebar-nav" aria-label="Điều hướng chính">
                <p class="nav-section">KHÔNG GIAN LÀM VIỆC</p>
                <RouterLink to="/" class="nav-item" exact-active-class="is-active">
                    <AppIcon name="grid" />Tổng quan
                </RouterLink>
                <RouterLink to="/account" class="nav-item" active-class="is-active">
                    <AppIcon name="user" />Tài khoản của tôi
                </RouterLink>
                <template v-if="auth.hasRole(accountCreationRoles)">
                    <p class="nav-section management-label">QUẢN TRỊ TRUY CẬP</p>
                    <RouterLink to="/accounts/new" class="nav-item" active-class="is-active">
                        <AppIcon name="add" />Tạo tài khoản
                    </RouterLink>
                </template>
            </nav>
            <div class="sidebar-bottom">
                <div class="sidebar-note">
                    <AppIcon name="shield" />
                    <p>Truy cập theo vai trò<small>Không gian được cá nhân hóa theo quyền của bạn.</small></p>
                </div>
                <button class="nav-item logout-button" :disabled="busy" @click="signOut">
                    <AppIcon name="logout" />{{ busy ? 'Đang đăng xuất…' : 'Đăng xuất' }}
                </button>
            </div>
        </aside>
        <div class="workspace-body" :inert="mobileOpen">
            <header class="topbar">
                <div class="breadcrumb">
                    <button class="icon-button mobile-menu" aria-label="Mở menu" aria-controls="workspace-sidebar"
                        :aria-expanded="mobileOpen" @click="mobileOpen = true">
                        <AppIcon name="menu" />
                    </button><span>Workspace</span><span class="breadcrumb-divider">/</span><strong>{{ route.meta.title
                        }}</strong>
                </div>
                <RouterLink to="/account" class="topbar-profile" aria-label="Mở tài khoản của tôi"><span
                        class="topbar-email">{{
                            auth.state.user.email }}</span><span class="avatar">{{ initials }}</span></RouterLink>
            </header>
            <main id="main-content" class="page-content" tabindex="-1">
                <RouterView />
            </main>
            <footer class="workspace-footer">
                <span>© {{ new Date().getFullYear() }} Company HRM</span><span>Không gian làm việc nội bộ</span>
            </footer>
        </div>
    </div>
</template>
