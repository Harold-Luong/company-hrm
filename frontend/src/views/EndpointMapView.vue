<script setup>
import { common } from '@/content/common.js'
import HelpLayout from '@/components/HelpLayout.vue'

const mapUrl = `${import.meta.env.BASE_URL}docs/endpoint-map.html`
</script>

<template>
  <HelpLayout :title="common.endpointMap" :description="common.endpointMapDescription">
    <iframe
      class="endpoint-map"
      :src="mapUrl"
      :title="common.endpointMap"
      sandbox="allow-scripts allow-downloads allow-popups allow-popups-to-escape-sandbox"
      referrerpolicy="no-referrer"
    ></iframe>
  </HelpLayout>
</template>

<style scoped>
.endpoint-map {
  display: block;
  width: 100%;
  height: 80vh;
  min-height: 560px;
  border: 1px solid var(--border, #dce4ef);
  border-radius: 12px;
  background: #f4f7fb;
}
</style>
