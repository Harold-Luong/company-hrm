<script setup>
import { attendanceRequestScope as copy } from '@/content/attendance.js'

defineProps({ busy: Boolean })
const inbox = defineModel({ type: Boolean, default: false })
</script>

<template>
  <div class="attendance-request-scope" role="group" :aria-label="copy.label">
    <span class="muted small">{{ copy.label }}</span>
    <div class="attendance-scope-options">
      <button
        type="button"
        class="button button-secondary"
        :aria-pressed="!inbox"
        :disabled="busy"
        @click="inbox = false"
      >
        {{ copy.mine }}
      </button>
      <button
        type="button"
        class="button button-secondary"
        :aria-pressed="inbox"
        :disabled="busy"
        @click="inbox = true"
      >
        {{ copy.inbox }}
      </button>
    </div>
  </div>
</template>
