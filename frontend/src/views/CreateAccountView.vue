<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { createAccount as copy } from '@/content/auth.js'
import { common } from '@/content/common.js'

import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { ApiError, errorMessage } from '@/api/errors.js'
import { roles, roleLabels } from '@/auth/roles.js'
import AppIcon from '@/components/AppIcon.vue'
const router = useRouter()
const form = reactive({
  email: '',
  password: '',
  employeeId: '',
  roles: ['EMPLOYEE'],
})
const busy = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
const roleDescriptions = {
  get EMPLOYEE() {
    return copy.employeeRole
  },
  get MANAGER() {
    return copy.managerRole
  },
  get HR() {
    return copy.hrRole
  },
  get ADMIN() {
    return copy.adminRole
  },
}
async function submit() {
  if (busy.value) return
  error.value = ''
  success.value = ''
  if (!form.roles.length) {
    error.value = () => copy.roleRequired
    return
  }
  if (!form.password.trim()) {
    error.value = () => copy.blankPassword
    return
  }
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(form.employeeId.trim())
  ) {
    error.value = () => copy.invalidEmployeeId
    return
  }
  busy.value = true
  try {
    await auth.register({
      ...form,
      email: form.email.trim(),
      employeeId: form.employeeId.trim(),
      roles: [...form.roles],
    })
    const createdEmail = form.email.trim()
    success.value = () => copy.created({ email: createdEmail })
    form.email = ''
    form.password = ''
    form.employeeId = ''
    form.roles = ['EMPLOYEE']
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 403) {
      await router.replace({ name: 'forbidden' })
    } else {
      error.value = () => errorMessage(cause)
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <span class="permission-label">
        <AppIcon name="shield" :size="16" />{{ copy.adminBadge }}
      </span>
    </div>
    <div class="create-account-grid">
      <form class="panel create-form" :aria-busy="busy" @submit.prevent="submit">
        <div class="panel-heading">
          <h2>{{ copy.credentialsTitle }}</h2>
          <span class="small muted">{{ copy.requiredHint }}</span>
        </div>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
        <fieldset class="form-fields" :disabled="busy">
          <label for="new-email">{{ common.workEmail }} <span class="required">*</span></label
          ><input
            id="new-email"
            v-model="form.email"
            type="email"
            autocomplete="off"
            :maxlength="fieldLimits.email"
            required
            :placeholder="common.emailPlaceholder"
          />
          <label for="new-password"
            >{{ copy.initialPassword }} <span class="required">*</span></label
          ><input
            id="new-password"
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            required
            :placeholder="copy.passwordPlaceholder"
          />
          <label for="employee-reference"
            >{{ copy.employeeId }} <span class="required">*</span></label
          ><input
            id="employee-reference"
            v-model="form.employeeId"
            type="text"
            required
            :placeholder="copy.employeeIdPlaceholder"
            aria-describedby="reference-help"
          />
          <p id="reference-help" class="field-help">{{ copy.employeeIdHint }}</p>
          <fieldset class="role-fieldset">
            <legend>{{ copy.roles }} <span class="required">*</span></legend>
            <p class="field-help">{{ copy.rolesHint }}</p>
            <div class="role-options">
              <label
                v-for="role in roles"
                :key="role"
                class="role-option"
                :class="{ selected: form.roles.includes(role) }"
                ><input v-model="form.roles" type="checkbox" :value="role" /><span
                  ><strong>{{ roleLabels[role] }}</strong
                  ><small>{{ roleDescriptions[role] }}</small></span
                ></label
              >
            </div>
          </fieldset>
          <div class="form-footer">
            <span class="muted small">{{ copy.saveHint }}</span
            ><button class="button button-primary" type="submit">
              <AppIcon name="add" :size="18" />{{ busy ? common.creating : copy.title }}
            </button>
          </div>
        </fieldset>
      </form>
      <aside class="create-aside">
        <section class="panel guidance-panel">
          <span class="metric-icon violet">
            <AppIcon name="shield" :size="23" />
          </span>
          <h2>{{ copy.guidanceTitle }}</h2>
          <p>{{ copy.guidanceDescription }}</p>
          <ul>
            <li>{{ copy.activatedImmediately }}</li>
            <li>{{ copy.uniqueEmployeeId }}</li>
            <li>{{ copy.adminRoleHint }}</li>
          </ul>
        </section>
        <div class="info-strip align-start">
          <AppIcon name="info" />
          <p>{{ copy.sessionHint }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>
