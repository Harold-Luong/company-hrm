<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../auth/session.js'
import { demoPersonas, demoPassword } from './config.js'
import { resetDemo } from './api.js'
import { defineContent } from '../i18n/content.js'
const copy = defineContent('demo')
const router = useRouter()
const toolbar = ref(null)
let observer
onMounted(() => {
  observer = new ResizeObserver(() =>
    document.documentElement.style.setProperty(
      '--demo-toolbar-height',
      `${toolbar.value.offsetHeight}px`,
    ),
  )
  observer.observe(toolbar.value)
})
onUnmounted(() => {
  observer?.disconnect()
  document.documentElement.style.removeProperty('--demo-toolbar-height')
})
const busy = ref(false),
  error = ref(''),
  confirming = ref(false)
const activeRole = computed(() => auth.state.user?.roles[0])
async function enter(persona) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (auth.authenticated.value) await auth.logout()
    await auth.login(persona.email, demoPassword)
    await router.replace('/')
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}
function reset() {
  resetDemo()
  window.location.hash = '/login'
  window.location.reload()
}
</script>
<template>
  <aside ref="toolbar" class="demo-toolbar" :aria-label="copy.title">
    <div class="demo-toolbar-copy">
      <strong>{{ copy.title }}</strong
      ><span>{{ copy.description }}</span>
    </div>
    <div class="demo-toolbar-actions">
      <button
        v-for="persona in demoPersonas"
        :key="persona.role"
        type="button"
        :disabled="busy"
        :aria-pressed="activeRole === persona.role"
        @click="enter(persona)"
      >
        {{ copy.roles[persona.role] }}
      </button>
      <button v-if="!confirming" type="button" :disabled="busy" @click="confirming = true">
        {{ copy.reset }}
      </button>
      <template v-else>
        <span>{{ copy.confirmReset }}</span>
        <button type="button" @click="reset">{{ copy.confirm }}</button>
        <button type="button" @click="confirming = false">{{ copy.cancel }}</button>
      </template>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
  </aside>
</template>
<style>
.demo-toolbar ~ .workspace .sidebar,
.demo-toolbar ~ .workspace .topbar {
  top: var(--demo-toolbar-height, 80px);
}
.demo-toolbar ~ .workspace {
  min-height: calc(100svh - var(--demo-toolbar-height, 80px));
}
.demo-toolbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 24px;
  background: #123c35;
  color: #fff;
  font-size: 13px;
}
.demo-toolbar-copy {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
}
.demo-toolbar-copy strong {
  letter-spacing: 0.08em;
}
.demo-toolbar-copy span {
  color: #d4e8df;
}
.demo-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.demo-toolbar button {
  border: 1px solid #789d92;
  border-radius: 6px;
  background: transparent;
  color: white;
  padding: 7px 10px;
  cursor: pointer;
  font: inherit;
}
.demo-toolbar button:hover,
.demo-toolbar button[aria-pressed='true'] {
  background: #e2f4e9;
  color: #123c35;
}
.demo-toolbar button:focus-visible {
  outline: 3px solid #ffd074;
  outline-offset: 2px;
}
.demo-toolbar p {
  margin: 0;
  width: 100%;
  color: #ffd9d9;
}
@media (max-width: 640px) {
  .demo-toolbar {
    padding: 12px;
  }
}
</style>
