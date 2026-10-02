<script setup>
defineProps({
  page: { type: Number, required: true },
  pages: { type: Number, required: true },
  total: { type: Number, required: true },
  busy: Boolean,
})
defineEmits(['change'])
</script>
<template>
  <div class="pagination">
    <span class="muted">{{ total }} bản ghi · Trang {{ pages ? page + 1 : 0 }} / {{ pages }}</span>
    <div class="button-row">
      <button
        class="button button-secondary"
        :disabled="busy || page === 0"
        @click="$emit('change', page - 1)"
      >
        Trang trước
      </button>
      <button
        class="button button-secondary"
        :disabled="busy || page + 1 >= pages"
        @click="$emit('change', page + 1)"
      >
        Trang sau
      </button>
    </div>
  </div>
</template>
