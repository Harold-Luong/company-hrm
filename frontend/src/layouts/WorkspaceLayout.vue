<script setup>
import { getRouteTitle } from '@/router/title.js'
import { refreshIntervals } from '@/config/runtime.js'
import { workspaceLayout as copy } from '@/content/workspace.js'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { accountManagementRoles } from '@/accounts/constants.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import { calendarManagementRoles } from '@/calendar/constants.js'
import { leaveReviewRoles } from '@/leave/constants.js'
import { attendanceRoles } from '@/attendance/constants.js'
import { leave } from '@/leave/api.js'
import { attendance } from '@/attendance/api.js'
import { subscribeNotifications } from '@/notifications/stream.js'
import { requestChangedEvent } from '@/notifications/changes.js'
import AppIcon from '@/components/AppIcon.vue'
import AccountResultToasts from '@/components/AccountResultToasts.vue'
import { startAccountNotifications } from '@/notifications/accountResults.js'
import { useI18n } from 'vue-i18n'
const { t } = useI18n({ useScope: 'global' })

const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const accountMenuOpen = ref(false)
const accountMenu = ref(null)
const accountTrigger = ref(null)
function closeMenus() {
    if (accountMenuOpen.value) {
        accountMenuOpen.value = false
        accountTrigger.value?.focus()
    }
    mobileOpen.value = false
}
function closeAccountMenuOutside(event) {
    if (!accountMenu.value?.contains(event.target)) accountMenuOpen.value = false
}
function closeAccountMenuOnBlur(event) {
    if (!accountMenu.value?.contains(event.relatedTarget)) accountMenuOpen.value = false
}
const busy = ref(false)
const pendingLeaveCount = ref(0)
const pendingAttendanceCount = ref(0)
let countLoading = false
let countQueued = false
let countTimer
let countStopped = false
let stopNotifications
let stopAccountNotifications
const initials = computed(() => auth.state.user?.email.slice(0, 2).toUpperCase() || 'CH')
const isRequestPage = computed(
    () =>
        ['/leave', '/attendance/requests', '/attendance/overtime', '/attendance/corrections'].includes(
            route.path,
        ) || route.path.startsWith('/leave/requests/'),
)
watch(
    () => route.fullPath,
    () => {
        mobileOpen.value = false
        accountMenuOpen.value = false
        refreshPendingCounts()
    },
)
async function refreshPendingCounts() {
    if (countStopped || !auth.hasRole(leaveReviewRoles)) return
    if (countLoading) {
        countQueued = true
        return
    }
    countLoading = true
    try {
        await Promise.allSettled(
            [
                [pendingLeaveCount, () => leave.pendingCount()],
                [pendingAttendanceCount, () => attendance.pendingRequestCount()],
            ].map(async ([target, fetchCount]) => {
                const result = await fetchCount()
                if (!countStopped)
                    target.value = Number.isSafeInteger(result.count) && result.count >= 0 ? result.count : 0
            }),
        )
    } finally {
        countLoading = false
        if (countQueued && !countStopped) {
            countQueued = false
            refreshPendingCounts()
        }
    }
}
async function schedulePendingCounts() {
    await refreshPendingCounts()
    if (!countStopped)
        countTimer = window.setTimeout(schedulePendingCounts, refreshIntervals.pendingCounts)
}
onMounted(() => {
    countStopped = false
    document.addEventListener('pointerdown', closeAccountMenuOutside)
    window.addEventListener(requestChangedEvent, refreshPendingCounts)
    schedulePendingCounts()
    if (auth.hasRole(leaveReviewRoles))
        stopNotifications = subscribeNotifications(refreshPendingCounts)
    if (auth.hasRole(accountCreationRoles)) stopAccountNotifications = startAccountNotifications()
})
onBeforeUnmount(() => {
    countStopped = true
    document.removeEventListener('pointerdown', closeAccountMenuOutside)
    window.removeEventListener(requestChangedEvent, refreshPendingCounts)
    window.clearTimeout(countTimer)
    stopNotifications?.()
    stopAccountNotifications?.()
})
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
    <div v-if="auth.state.user" class="workspace" @keydown.esc="closeMenus">
        <AccountResultToasts />
        <a class="skip-link" href="#main-content">{{ t('workspaceLayout.skipToContent') }}</a>
        <button
v-if="mobileOpen" class="sidebar-overlay" :aria-label="t('workspaceLayout.closeMenu')"
            @click="mobileOpen = false"></button>
        <aside id="workspace-sidebar" class="sidebar" :class="{ 'is-open': mobileOpen }" :inert="busy">
            <RouterLink to="/" class="brand"><span class="brand-icon">{{ t('common.monogramFirst') }}<span>{{
                t('common.monogramSecond') }}</span></span><span>{{ t('common.wordmarkPrefix')
                    }}<span class="brand-light">{{ t('common.wordmarkSuffix') }}</span><small>{{
                        t('workspaceLayout.workspaceLabel') }} </small></span></RouterLink>
            <button
class="mobile-sidebar-close icon-button" :aria-label="t('workspaceLayout.closeMenu')"
                @click="mobileOpen = false">
                <AppIcon name="close" />
            </button>
            <nav class="sidebar-nav" :aria-label="t('workspaceLayout.mainNavigation')">
                <section class="nav-group" aria-labelledby="nav-personal">
                    <h2 id="nav-personal" class="nav-section">{{ t('workspaceLayout.personalSection') }}</h2>
                    <RouterLink to="/" class="nav-item" exact-active-class="is-active">
                        <AppIcon name="grid" />{{ t('workspaceLayout.home') }}
                    </RouterLink>
                    <RouterLink to="/calendar" class="nav-item" active-class="is-active">
                        <AppIcon name="calendar" />{{ t('workspaceLayout.calendar') }}
                    </RouterLink>
                    <RouterLink to="/attendance" class="nav-item" exact-active-class="is-active">
                        <AppIcon name="clock" />{{ t('workspaceLayout.attendance') }}
                    </RouterLink>
                    <details class="nav-requests" :open="isRequestPage">
                        <summary class="nav-item" :class="{ 'is-active': isRequestPage }">
                            <AppIcon name="mail" /><span>{{ t('workspaceLayout.myRequests') }}</span>
                            <AppIcon name="arrow" :size="14" class="nav-chevron" />
                        </summary>
                        <div class="nav-submenu">
                            <RouterLink
to="/leave" class="nav-item" :class="{
                                'is-active': route.path === '/leave' || route.path.startsWith('/leave/requests/'),
                            }">
                                {{ t('workspaceLayout.myLeave') }}
                            </RouterLink>
                            <RouterLink to="/attendance/requests" class="nav-item" active-class="is-active">
                                {{ t('workspaceLayout.attendanceRequests') }}
                            </RouterLink>
                            <RouterLink to="/attendance/overtime" class="nav-item" active-class="is-active">
                                {{ t('workspaceLayout.overtime') }}
                            </RouterLink>
                            <RouterLink to="/attendance/corrections" class="nav-item" active-class="is-active">
                                {{ t('workspaceLayout.corrections') }}
                            </RouterLink>
                        </div>
                    </details>
                </section>
                <section v-if="auth.hasRole(attendanceRoles)" class="nav-group" aria-labelledby="nav-attendance">
                    <h2 id="nav-attendance" class="nav-section">
                        {{ t('workspaceLayout.attendanceSection') }}
                    </h2>
                    <RouterLink to="/attendance/requests/inbox" class="nav-item" active-class="is-active">
                        <AppIcon name="shield" /><span>{{ t('workspaceLayout.reviewAttendance') }}</span>
                        <span
v-if="pendingAttendanceCount > 0" class="nav-notification" :aria-label="t('workspaceLayout.pendingAttendance', {
                            pendingAttendanceCount: pendingAttendanceCount,
                        })
                            ">
                            {{ copy.badgeCount(pendingAttendanceCount) }}
                        </span>
                    </RouterLink>
                    <RouterLink to="/attendance/shifts" class="nav-item" active-class="is-active">
                        <AppIcon name="clock" />{{ t('workspaceLayout.shifts') }}
                    </RouterLink>
                    <RouterLink to="/attendance/schedules" class="nav-item" active-class="is-active">
                        <AppIcon name="calendar" /><span>{{ t('workspaceLayout.schedules') }}</span>
                    </RouterLink>
                    <RouterLink to="/attendance/reports" class="nav-item" active-class="is-active">
                        <AppIcon name="grid" />{{ t('workspaceLayout.reports') }}
                    </RouterLink>
                </section>
                <section v-if="auth.hasRole(leaveReviewRoles)" class="nav-group" aria-labelledby="nav-leave">
                    <h2 id="nav-leave" class="nav-section">{{ t('workspaceLayout.leaveSection') }}</h2>
                    <RouterLink to="/leave/inbox" class="nav-item" active-class="is-active">
                        <AppIcon name="shield" /><span>{{ t('workspaceLayout.reviewLeave') }}</span><span
                            v-if="pendingLeaveCount > 0" class="nav-notification" :aria-label="t('workspaceLayout.pendingLeave', { pendingLeaveCount: pendingLeaveCount })
                                ">{{ copy.badgeCount(pendingLeaveCount) }}</span>
                    </RouterLink>
                </section>
                <section v-if="auth.hasRole(personnelManagementRoles)" class="nav-group" aria-labelledby="nav-people">
                    <h2 id="nav-people" class="nav-section">{{ t('workspaceLayout.personnelSection') }}</h2>
                    <RouterLink
to="/employees" class="nav-item" :class="{
                        'is-active': route.path === '/employees' || route.path.startsWith('/employees/'),
                    }">
                        <AppIcon name="user" />{{ t('common.employee') }}
                    </RouterLink>
                    <RouterLink
to="/departments" class="nav-item" :class="{
                        'is-active': route.path === '/departments' || route.path.startsWith('/departments/'),
                    }">
                        <AppIcon name="grid" />{{ t('common.department') }}
                    </RouterLink>
                    <RouterLink
to="/positions" class="nav-item" :class="{
                        'is-active': route.path === '/positions' || route.path.startsWith('/positions/'),
                    }">
                        <AppIcon name="shield" />{{ t('common.position') }}
                    </RouterLink>
                </section>
                <section class="nav-group" aria-labelledby="nav-admin">
                    <h2 id="nav-admin" class="nav-section">{{ t('workspaceLayout.adminSection') }}</h2>
                    <RouterLink
v-if="auth.hasRole(calendarManagementRoles)" to="/calendar-events" class="nav-item"
                        :class="{
                            'is-active':
                                route.path === '/calendar-events' || route.path.startsWith('/calendar-events/'),
                        }">
                        <AppIcon name="calendar" />{{ t('workspaceLayout.manageCalendar') }}
                    </RouterLink>
                    <RouterLink v-if="auth.hasRole(accountManagementRoles)" to="/accounts" class="nav-item" active-class="is-active">
                        <AppIcon name="shield" />{{ t('workspaceLayout.manageAccounts') }}
                    </RouterLink>
                    <RouterLink to="/services" class="nav-item" active-class="is-active">
                        <AppIcon name="info" />{{ t('workspaceLayout.services') }}
                    </RouterLink>
                    <RouterLink
v-if="auth.hasRole(accountCreationRoles)" to="/accounts/new" class="nav-item"
                        active-class="is-active">
                        <AppIcon name="add" />{{ t('workspaceLayout.createAccount') }}
                    </RouterLink>
                </section>
                <section class="nav-group" aria-labelledby="nav-help">
                    <h2 id="nav-help" class="nav-section">{{ t('workspaceLayout.helpSection') }}</h2>
                    <RouterLink to="/help/endpoints" class="nav-item" active-class="is-active">
                        <AppIcon name="book" />{{ t('common.endpointMap') }}
                    </RouterLink>
                    <RouterLink to="/help/guide" class="nav-item" active-class="is-active">
                        <AppIcon name="book" />{{ t('workspaceLayout.userGuide') }}
                    </RouterLink>
                    <RouterLink to="/help/features" class="nav-item" active-class="is-active">
                        <AppIcon name="info" />{{ t('workspaceLayout.featureReference') }}
                    </RouterLink>
                </section>
            </nav>
            <div class="sidebar-bottom">
                <div class="sidebar-note">
                    <AppIcon name="shield" />
                    <p>
                        {{ t('workspaceLayout.accessTitle')
                        }}<small>{{ t('workspaceLayout.accessHint') }}</small>
                    </p>
                </div>
            </div>
        </aside>
        <div class="workspace-body" :inert="mobileOpen">
            <header class="topbar">
                <div class="breadcrumb">
                    <button
class="icon-button mobile-menu" :aria-label="t('workspaceLayout.openMenu')"
                        aria-controls="workspace-sidebar" :aria-expanded="mobileOpen" @click="mobileOpen = true">
                        <AppIcon name="menu" />
                    </button><span>{{ t('workspaceLayout.workspaceTitle') }}</span><span
                        class="breadcrumb-divider">/</span><strong>{{ getRouteTitle(route) }}</strong>
                </div>
                <div ref="accountMenu" class="topbar-account" @focusout="closeAccountMenuOnBlur">
                    <button
ref="accountTrigger" type="button" class="topbar-profile account-menu-trigger"
                        :aria-label="t('workspaceLayout.openAccountMenu')" aria-controls="account-dropdown"
                        :aria-expanded="accountMenuOpen" @click="accountMenuOpen = !accountMenuOpen">
                        <span class="topbar-email">{{ auth.state.user.email }}</span>
                        <span class="avatar">{{ initials }}</span>
                    </button>
                    <nav
v-if="accountMenuOpen" id="account-dropdown" class="account-dropdown"
                        :aria-label="t('workspaceLayout.account')">
                        <RouterLink to="/account" class="account-dropdown-link" @click="accountMenuOpen = false">
                            <AppIcon name="user" :size="18" />
                            <span>{{ t('workspaceLayout.profile') }}</span>
                        </RouterLink>
                        <LanguageSwitcher menu />

                        <button class="nav-item logout-button" :disabled="busy" @click="signOut">
                            <AppIcon name="logout" />{{
                                busy ? t('workspaceLayout.signingOut') : t('workspaceLayout.signOut')
                            }}
                        </button>
                    </nav>
                </div>
            </header>
            <main id="main-content" class="page-content" tabindex="-1">
                <RouterView :key="route.path" />
            </main>
            <footer class="workspace-footer">
                <span>{{ t('workspaceLayout.copyright', { year: new Date().getFullYear() }) }}</span><span>{{
                    t('workspaceLayout.footer') }}</span>
            </footer>
        </div>
    </div>
</template>

<style scoped>
.sidebar>.brand {
    margin-bottom: 24px;
}

.nav-group {
    padding-top: 20px;
    border-top: 1px solid var(--border);
}

.nav-group+.nav-group {
    margin-top: 18px;
}

.nav-item>svg {
    flex-shrink: 0;
}

.nav-requests>summary {
    cursor: pointer;
    list-style: none;
}

.nav-requests>summary::-webkit-details-marker {
    display: none;
}

.nav-chevron {
    margin-left: auto;
    transform: rotate(0deg);
}

.nav-requests[open]>summary .nav-chevron {
    transform: rotate(90deg);
}

.nav-submenu {
    margin: 4px 0 8px 23px;
    padding-left: 8px;
    border-left: 1px solid var(--border);
}

.nav-submenu .nav-item {
    min-height: 36px;
    padding: 8px 10px;
}

.topbar-account {
    position: relative;
    flex-shrink: 0;
}

.account-menu-trigger {
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: inherit;
    padding: 6px;
}

.account-menu-trigger:hover,
.account-menu-trigger[aria-expanded='true'] {
    background: #e7f2ed;
}

.account-dropdown {
    position: absolute;
    display: flex;
    flex-direction: column;
    gap: 4px;
    z-index: 30;
    top: calc(100% + 8px);
    right: 0;
    width: 220px;
    max-width: calc(100vw - 32px);
    padding: 6px;
    background: white;
    border: 1px solid var(--border);
    border-radius: 10px;
    box-shadow: 0 8px 24px #24324a14;
}

.account-dropdown-link {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 10px;
    width: 100%;
    box-sizing: border-box;
    padding: 11px 12px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: inherit;
    text-align: left;
    text-decoration: none;
    font: inherit;
    cursor: pointer;
}

.account-dropdown-link:hover,
.account-dropdown-link:focus-visible {
    background: #e7f2ed;
    color: var(--primary-dark);
}

.nav-notification {
    min-width: 1.35rem;
    height: 1.35rem;
    margin-left: auto;
    padding: 0 0.35rem;
    border-radius: 999px;
    background: #dc2626;
    color: #fff;
    font-size: 0.75rem;
    font-weight: 700;
    line-height: 1.35rem;
    text-align: center;
}
</style>
