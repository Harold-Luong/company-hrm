<script setup>
import { pageControls as copy } from '@/content/workspace.js'
import { common } from '@/content/common.js'

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
    <span class="muted">{{
      copy.summary({ total: total, currentPage: pages ? page + 1 : 0, pages: pages })
    }}</span>
    <div class="button-row">
      <button
        class="button button-secondary"
        :disabled="busy || page === 0"
        @click="$emit('change', page - 1)"
      >
        {{ common.previousPage }}
      </button>
      <button
        class="button button-secondary"
        :disabled="busy || page + 1 >= pages"
        @click="$emit('change', page + 1)"
      >
        {{ common.nextPage }}
      </button>
    </div>
  </div>
</template>
