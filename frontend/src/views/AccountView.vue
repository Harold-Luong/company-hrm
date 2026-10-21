<script setup>
import { account as copy } from '@/content/hrm.js'

import { computed } from 'vue'
import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { auth } from '@/auth/session.js'
import { hrm } from '@/hrm/api.js'
import MyProfileCard from '@/components/MyProfileCard.vue'

const user = computed(() => auth.state.user)
const {
    data: employee,
    loading: employeeLoading,
    error: employeeError,
    load: retry,
} = useAsyncResource(() => hrm.get('employees', user.value.employeeId), {
    source: () => user.value?.employeeId,
    enabled: () => !!user.value?.employeeId,
})
</script>

<template>
    <div v-if="user">
        <div class="page-heading">
            <div>
                <p class="eyebrow">{{ copy.eyebrow }}</p>
                <h1>{{ copy.title }}</h1>
                <p class="muted">{{ copy.description }}</p>
            </div>
        </div>
        <MyProfileCard :employee="employee" :loading="employeeLoading" :error="employeeError" @retry="retry()" />
    </div>
</template>
