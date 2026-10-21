<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { authAccount as copy } from '@/content/auth.js'
import { common } from '@/content/common.js'

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { errorMessage } from '@/api/errors.js'
import { formatDate } from '@/shared/date.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'

const router = useRouter()
const user = computed(() => auth.state.user)
const confirming = ref(false)
const busy = ref(false)
const error = useLocalizedMessage()

async function logoutAll() {
  if (busy.value) return

  busy.value = true
  error.value = ''

  try {
    await auth.logout(true)
    await router.replace({
      name: 'login',
      query: { logout: 'success' },
    })
  } catch (cause) {
    error.value = () => errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div v-if="user" class="profile-module">
    <div class="profile-module-heading">
      <h3>{{ copy.title }}</h3>
      <span class="status-chip" :class="user.active ? 'active' : 'inactive'">
        {{ user.active ? copy.active : copy.inactive }}
      </span>
    </div>

    <dl class="profile-fields">
      <div>
        <dt>{{ copy.loginEmail }}</dt>
        <dd>{{ user.email }}</dd>
      </div>
      <div>
        <dt>{{ copy.accountId }}</dt>
        <dd>#{{ user.id }}</dd>
      </div>
      <div>
        <dt>{{ copy.roles }}</dt>
        <dd class="role-list">
          <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
        </dd>
      </div>
      <div>
        <dt>{{ copy.createdAt }}</dt>
        <dd>{{ formatDate(user.createdAt) }}</dd>
      </div>
      <div>
        <dt>{{ copy.updatedAt }}</dt>
        <dd>{{ formatDate(user.updatedAt) }}</dd>
      </div>
      <div>
        <dt>{{ copy.lastLoginAt }}</dt>
        <dd>{{ formatDate(user.lastLoginAt) }}</dd>
      </div>
    </dl>
    <p class="account-note">{{ copy.permissionsHint }}</p>

    <section class="account-security" aria-labelledby="account-security-title">
      <div class="profile-module-heading">
        <h3 id="account-security-title">{{ copy.securityTitle }}</h3>
      </div>
      <p class="muted">{{ copy.securityDescription }}</p>
      <p class="small muted">{{ copy.revocationHint }}</p>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <div v-if="confirming" class="confirm-box">
        <p>{{ copy.logoutAllConfirmation }}</p>
        <div class="button-row">
          <button class="button button-danger" :disabled="busy" @click="logoutAll">
            {{ busy ? copy.processing : copy.confirmLogout }}
          </button>
          <button class="button button-secondary" :disabled="busy" @click="confirming = false">
            {{ common.cancel }}
          </button>
        </div>
      </div>
      <button v-else class="button button-danger-outline" @click="confirming = true">
        <AppIcon name="logout" :size="17" /> {{ copy.logoutAll }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.account-note {
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 14px;
}
.account-security {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.account-security .profile-module-heading {
  margin-bottom: 12px;
}
</style>
