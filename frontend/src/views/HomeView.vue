<script setup>
import { computed } from 'vue'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { formatDate } from '@/auth/format.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'
const user = computed(() => auth.state.user)
const today = new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'full',
    timeZone: 'Asia/Ho_Chi_Minh',
}).format(new Date())
</script>

<template>
    <div v-if="user">
        <div class="page-heading">
            <div>
                <p class="eyebrow">WORKSPACE</p>
                <h1>Tổng quan</h1>
                <p class="muted">Chào mừng bạn đến với không gian làm việc Company HRM.</p>
            </div>
            <span class="date-label">
                <AppIcon name="clock" :size="16" />{{ today }}
            </span>
        </div>
        <section class="welcome-banner">
            <div>
                <span class="banner-label">SẴN SÀNG CHO NGÀY LÀM VIỆC</span>
                <h2>Xin chào, {{ user.email.split('@')[0] }}.</h2>
                <p>Quản lý tài khoản và truy cập các tiện ích được cấp cho bạn.</p>
                <RouterLink to="/account" class="button button-white">Xem tài khoản
                    <AppIcon name="arrow" :size="17" />
                </RouterLink>
            </div>
            <div class="welcome-art" aria-hidden="true">
                <AppIcon name="shield" :size="78" />
            </div>
        </section>
        <div class="summary-grid">
            <article class="panel summary-card">
                <span class="metric-icon green">
                    <AppIcon name="check" />
                </span><span class="metric-label">Trạng thái tài khoản</span><strong>Đang hoạt động</strong><span
                    class="muted small">Tài khoản đã được xác thực</span>
            </article>
            <article class="panel summary-card">
                <span class="metric-icon violet">
                    <AppIcon name="shield" />
                </span><span class="metric-label">Vai trò của bạn</span>
                <div class="role-list">
                    <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
                </div>
                <span class="muted small">Quyền được cấp bởi doanh nghiệp</span>
            </article>
            <article class="panel summary-card">
                <span class="metric-icon blue">
                    <AppIcon name="clock" />
                </span><span class="metric-label">Lần đăng nhập gần nhất</span><strong class="date-value">{{
                    formatDate(user.lastLoginAt) }}</strong><span class="muted small">Múi giờ Việt Nam (GMT+7)</span>
            </article>
        </div>
        <section class="section-block">
            <div class="section-heading">
                <h2>Truy cập nhanh</h2>
                <span class="muted small">Dành cho vai trò của bạn</span>
            </div>
            <div class="quick-grid">
                <RouterLink to="/account" class="panel quick-card"><span class="metric-icon blue">
                        <AppIcon name="user" />
                    </span>
                    <div>
                        <h3>Tài khoản của tôi</h3>
                        <p>Xem thông tin đăng nhập, vai trò và quản lý phiên truy cập.</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
                <RouterLink v-if="auth.hasRole(accountCreationRoles)" to="/accounts/new" class="panel quick-card"><span
                        class="metric-icon violet">
                        <AppIcon name="add" />
                    </span>
                    <div>
                        <h3>Tạo tài khoản</h3>
                        <p>Cấp tài khoản nội bộ và chỉ định vai trò truy cập.</p>
                    </div>
                    <AppIcon name="arrow" :size="19" />
                </RouterLink>
            </div>
        </section>
        <div class="info-strip">
            <AppIcon name="info" :size="19" />
            <p>
                Các trang và thao tác hiển thị theo quyền truy cập hiện tại của bạn. Liên hệ quản trị viên
                nếu cần thay đổi quyền.
            </p>
        </div>
    </div>
</template>
