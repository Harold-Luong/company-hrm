<script setup>
import { getDisplayLocale } from '@/i18n/index.js'
import { COMPANY_TIMEZONE } from '@/config/presentation.js'

import { home as copy } from '@/content/workspace.js'
import { common } from '@/content/common.js'

import { computed } from 'vue'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import { formatDate } from '@/shared/date.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'
import QuickAttendance from '@/components/QuickAttendance.vue'
const user = computed(() => auth.state.user)
const today = computed(() =>
    new Intl.DateTimeFormat(getDisplayLocale(), {
        dateStyle: 'full',
        timeZone: COMPANY_TIMEZONE,
    }).format(new Date()),
)
</script>

<template>
    <div v-if="user">
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ copy.title }}</h1>
                <p class="muted">{{ copy.description }}</p>
            </div>
            <span class="date-label">
                <AppIcon name="clock" :size="16" />{{ today }}
            </span>
        </div>
        <section class="welcome-banner">
            <div>
                <span class="banner-label">{{ copy.welcomeEyebrow }}</span>
                <h2>{{ copy.greeting({ name: user.email.split('@')[0] }) }}</h2>
                <p>{{ copy.welcomeDescription }}</p>
                <RouterLink to="/account" class="button button-white">{{ copy.account }}
                    <AppIcon name="arrow" :size="17" />
                </RouterLink>
            </div>
            <div class="welcome-art" aria-hidden="true">
                <AppIcon name="shield" :size="78" />
            </div>
        </section>
        <QuickAttendance />
        <div class="summary-grid">
            <article class="panel summary-card">
                <span class="metric-icon green">
                    <AppIcon name="check" />
                </span><span class="metric-label">{{ copy.accountStatus }}</span><strong>{{ copy.active }}</strong><span
                    class="muted small">{{ copy.verified }}</span>
            </article>
            <article class="panel summary-card">
                <span class="metric-icon violet">
                    <AppIcon name="shield" />
                </span><span class="metric-label">{{ copy.roles }}</span>
                <div class="role-list">
                    <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
                </div>
                <span class="muted small">{{ copy.rolesHint }}</span>
            </article>
            <article class="panel summary-card">
                <span class="metric-icon blue">
                    <AppIcon name="clock" />
                </span><span class="metric-label">{{ copy.lastLogin }}</span><strong class="date-value">{{
                    formatDate(user.lastLoginAt) }}</strong><span class="muted small">{{ copy.timezoneHint }}</span>
            </article>
        </div>
        <section class="section-block">
            <div class="section-heading">
                <h2>{{ copy.quickAccess }}</h2>
                <span class="muted small">{{ copy.quickAccessHint }}</span>
            </div>
            <div class="quick-grid">
                <RouterLink to="/calendar" class="panel quick-card">
                    <span class="metric-icon green">
                        <AppIcon name="calendar" />
                    </span>
                    <div>
                        <h3>{{ copy.calendar }}</h3>
                        <p>{{ copy.calendarDescription }}</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
                <RouterLink v-if="auth.hasRole(personnelManagementRoles)" to="/employees" class="panel quick-card"><span
                        class="metric-icon green">
                        <AppIcon name="user" />
                    </span>
                    <div>
                        <h3>{{ common.employee }}</h3>
                        <p>{{ copy.personnelDescription }}</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
                <RouterLink v-if="auth.hasRole(personnelManagementRoles)" to="/departments" class="panel quick-card">
                    <span class="metric-icon blue">
                        <AppIcon name="grid" />
                    </span>
                    <div>
                        <h3>{{ copy.organization }}</h3>
                        <p>{{ copy.organizationDescription }}</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
                <RouterLink to="/account" class="panel quick-card"><span class="metric-icon blue">
                        <AppIcon name="user" />
                    </span>
                    <div>
                        <h3>{{ copy.profile }}</h3>
                        <p>{{ copy.profileDescription }}</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
                <RouterLink v-if="auth.hasRole(accountCreationRoles)" to="/accounts/new" class="panel quick-card"><span
                        class="metric-icon violet">
                        <AppIcon name="add" />
                    </span>
                    <div>
                        <h3>{{ copy.createAccount }}</h3>
                        <p>{{ copy.createAccountDescription }}</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
            </div>
        </section>
        <div class="info-strip">
            <AppIcon name="info" :size="19" />
            <p>{{ copy.permissionsHint }}</p>
        </div>
    </div>
</template>
