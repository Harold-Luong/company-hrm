<script setup>
import { auth } from '@/auth/session.js'
import { attendanceRoles } from '@/attendance/helpers.js'
import AppIcon from './AppIcon.vue'
</script>
<template>
  <nav class="attendance-nav button-row" aria-label="Chức năng chấm công">
    <RouterLink
      to="/attendance"
      class="button button-secondary"
      exact-active-class="attendance-current"
      ><AppIcon name="clock" :size="17" />Công của tôi</RouterLink
    >
    <RouterLink
      to="/attendance/requests"
      class="button button-secondary"
      active-class="attendance-current"
      ><AppIcon name="mail" :size="17" />Đi trễ / Về sớm</RouterLink
    >
    <template v-if="auth.hasRole(attendanceRoles)">
      <RouterLink
        to="/attendance/shifts"
        class="button button-secondary"
        active-class="attendance-current"
        ><AppIcon name="calendar" :size="17" />Ca làm việc</RouterLink
      >
      <RouterLink
        to="/attendance/schedules"
        class="button button-secondary"
        active-class="attendance-current"
        ><AppIcon name="user" :size="17" />Phân công ca</RouterLink
      >
      <RouterLink
        to="/attendance/reports"
        class="button button-secondary"
        active-class="attendance-current"
        ><AppIcon name="grid" :size="17" />Bảng công</RouterLink
      >
    </template>
  </nav>
</template>
