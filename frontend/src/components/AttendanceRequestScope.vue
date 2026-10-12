<script setup>
defineProps({ busy: Boolean })
const inbox = defineModel({ type: Boolean, default: false })
</script>

<template>
  <div class="attendance-request-scope" role="group" aria-label="Phạm vi yêu cầu">
    <span class="muted small">Phạm vi yêu cầu</span>
    <div class="attendance-scope-options">
      <button
        type="button"
        class="button button-secondary"
        :aria-pressed="!inbox"
        :disabled="busy"
        @click="inbox = false"
      >
        Đơn của tôi
      </button>
      <button
        type="button"
        class="button button-secondary"
        :aria-pressed="inbox"
        :disabled="busy"
        @click="inbox = true"
      >
        Hàng chờ duyệt
      </button>
    </div>
  </div>
</template>
