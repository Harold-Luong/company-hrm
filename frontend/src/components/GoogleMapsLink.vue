<script setup>
import { computed } from 'vue'
import { common } from '@/content/common.js'

const props = defineProps({ location: { type: String, default: '' } })
const mapsUrl = computed(() => {
  const query = props.location?.trim()
  if (!query) return ''
  return `https://www.google.com/maps/search/?${new URLSearchParams({ api: '1', query })}`
})
</script>

<template>
  <a v-if="mapsUrl" :href="mapsUrl" class="text-button" target="_blank" rel="noopener noreferrer">
    {{ common.openInGoogleMaps }}
  </a>
</template>
