<script setup>
import AppIcon from '@/components/AppIcon.vue'
</script>
<template>
    <section class="panel empty-state">
        <span class="empty-icon">
            <AppIcon name="lock" :size="34" />
        </span>
        <p class="eyebrow">403 · QUYỀN TRUY CẬP</p>
        <h1>Bạn chưa có quyền truy cập</h1>
        <p>
            Trang này yêu cầu quyền được cấp phù hợp. Vui lòng liên hệ quản trị viên nếu bạn cần sử dụng
            chức năng này.
        </p>
        <RouterLink to="/" class="button button-primary">Về tổng quan
            <AppIcon name="arrow" :size="17" />
        </RouterLink>
    </section>
</template>
