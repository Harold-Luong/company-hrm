<script setup>
import { helpLayout as copy } from '@/content/workspace.js'

import AppIcon from '@/components/AppIcon.vue'
import '@/assets/styles/help.css'

defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
})
</script>

<template>
  <div class="help-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p class="muted">{{ description }}</p>
      </div>
      <span class="help-heading-icon"><AppIcon name="book" :size="28" /></span>
    </div>
    <nav class="help-tabs" :aria-label="copy.title">
      <RouterLink to="/help/guide" active-class="is-active">{{ copy.userGuide }}</RouterLink>
      <RouterLink to="/help/features" active-class="is-active">{{
        copy.featureReference
      }}</RouterLink>
    </nav>
    <slot />
  </div>
</template>
