<script setup>
import { roleLabels } from '@/auth/roles.js'
defineProps({ role: { type: String, required: true } })
</script>
<template>
    <span class="role-badge" :class="`role-${role.toLowerCase()}`">{{
        roleLabels[role] || role
        }}</span>
</template>
