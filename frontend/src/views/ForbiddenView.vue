<script setup>
import { forbidden as copy } from '@/content/workspace.js'

import AppIcon from '@/components/AppIcon.vue'
</script>
<template>
  <section class="panel empty-state">
    <span class="empty-icon">
      <AppIcon name="lock" :size="34" />
    </span>
    <p class="eyebrow">{{ copy.eyebrow }}</p>
    <h1>{{ copy.title }}</h1>
    <p>{{ copy.description }}</p>
    <RouterLink to="/" class="button button-primary"
      >{{ copy.home }} <AppIcon name="arrow" :size="17" />
    </RouterLink>
  </section>
</template>
