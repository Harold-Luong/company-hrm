<script setup>
import AppIcon from '@/components/AppIcon.vue'
</script>
<template>
    <section class="panel empty-state">
        <span class="empty-icon">
            <AppIcon name="grid" :size="34" />
        </span>
        <p class="eyebrow">404 · KHÔNG TÌM THẤY</p>
        <h1>Trang này không tồn tại</h1>
        <p>Đường dẫn có thể đã thay đổi. Hãy quay lại trang tổng quan để tiếp tục.</p>
        <RouterLink to="/" class="button button-primary">Về tổng quan
            <AppIcon name="arrow" :size="17" />
        </RouterLink>
    </section>
</template>
