<script setup>
import { computed, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { hrm } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
import MyProfileCard from '@/components/MyProfileCard.vue'

const user = computed(() => auth.state.user)
const employee = ref(null)
const employeeLoading = ref(false)
const employeeError = ref('')
const retry = ref(0)
watch(
  [() => user.value?.employeeId, retry],
  async ([employeeId], _, onCleanup) => {
    let cancelled = false
    onCleanup(() => {
      cancelled = true
    })
    employee.value = null
    employeeError.value = ''
    employeeLoading.value = Boolean(employeeId)
    if (!employeeId) return
    try {
      const result = await hrm.get('employees', employeeId)
      if (!cancelled) employee.value = result
    } catch (cause) {
      if (!cancelled) employeeError.value = serviceError(cause)
    } finally {
      if (!cancelled) employeeLoading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="user">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CÁ NHÂN</p>
        <h1>Hồ sơ của tôi</h1>
        <p class="muted">Thông tin cá nhân, công việc, chấm công, nghỉ phép và tài khoản.</p>
      </div>
    </div>
    <MyProfileCard
      :employee="employee"
      :loading="employeeLoading"
      :error="employeeError"
      @retry="retry++"
    />
  </div>
</template>
