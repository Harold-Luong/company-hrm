<script setup>
import { computed, ref, watch } from 'vue'
import { accountsApi } from '@/accounts/api.js'
import { accountActions, accountLimits } from '@/accounts/constants.js'
import { accountManagementError } from '@/accounts/errors.js'
import { accountManagement as copy } from '@/content/accounts.js'
import { common } from '@/content/common.js'
import { roles, roleLabels } from '@/auth/roles.js'
import { auth } from '@/auth/session.js'
import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'
import { emptyPage } from '@/api/pagination.js'
import { dateTimeLabel } from '@/shared/date.js'
import PageControls from '@/components/PageControls.vue'

const props = defineProps({ id: { type: String, required: true } })
const {
  data: account,
  loading,
  error: loadError,
  load,
} = useAsyncResource(() => accountsApi.get(props.id), {
  source: () => props.id,
  errorMessage: accountManagementError,
})
const auditPage = ref(0)
const {
  data: history,
  loading: historyLoading,
  error: historyError,
  load: loadHistory,
} = useAsyncResource(() => accountsApi.history(props.id, auditPage.value), {
  source: () => [props.id, auditPage.value],
  initialValue: emptyPage,
  errorMessage: accountManagementError,
})
const action = ref(accountActions.revokeSessions)
const selectedRoles = ref([])
const reason = ref('')
const confirmed = ref(false)
const busy = ref(false)
const mustReload = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
const self = computed(() => String(auth.state.user?.id) === props.id)
const availableActions = computed(() => [
  account.value?.status === 'DISABLED' ? accountActions.enable : accountActions.disable,
  accountActions.changeRoles,
  accountActions.revokeSessions,
])
watch(account, (value) => {
  selectedRoles.value = [...(value?.roles ?? [])]
  confirmed.value = false
  if (!availableActions.value.includes(action.value)) action.value = accountActions.revokeSessions
  if (value) mustReload.value = false
})
watch([action, reason, () => selectedRoles.value.join(',')], () => {
  confirmed.value = false
  error.value = ''
})
watch(
  () => props.id,
  () => {
    reason.value = ''
    error.value = ''
    success.value = ''
    action.value = accountActions.revokeSessions
    auditPage.value = 0
  },
)
async function submit() {
  if (busy.value || self.value || mustReload.value || !confirmed.value || !account.value) return
  error.value = ''
  success.value = ''
  if (!reason.value.trim()) {
    error.value = () => copy.errors.reasonRequired
    return
  }
  if (action.value === accountActions.changeRoles && !selectedRoles.value.length) {
    error.value = () => copy.errors.rolesRequired
    return
  }
  const id = props.id
  busy.value = true
  try {
    const updated = await accountsApi.execute(id, {
      version: account.value.version,
      action: action.value,
      reason: reason.value.trim(),
      ...(action.value === accountActions.changeRoles ? { roles: [...selectedRoles.value] } : {}),
    })
    if (props.id !== id) return
    account.value = updated
    reason.value = ''
    confirmed.value = false
    action.value = accountActions.revokeSessions
    success.value = () => copy.saved
    if (auditPage.value === 0) await loadHistory()
    else auditPage.value = 0
  } catch (cause) {
    if (props.id !== id) return
    error.value = () => accountManagementError(cause)
    // An uncertain write is never retried blindly. Reload the version and audit first.
    mustReload.value = true
    confirmed.value = false
  } finally {
    busy.value = false
  }
}
async function reload() {
  error.value = ''
  success.value = ''
  await Promise.all([load(), loadHistory()])
}
const auditRoles = (value) =>
  value
    .split(',')
    .map((role) => roleLabels[role])
    .join(', ')
</script>
<template>
  <div>
    <RouterLink to="/accounts" class="back-link">{{ copy.back }}</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.detail }}</h1>
        <p v-if="account" class="muted account-email">{{ account.email }}</p>
      </div>
      <button class="button button-secondary" :disabled="busy || loading" @click="reload">
        {{ common.reload }}
      </button>
    </div>
    <p v-if="loadError" class="alert alert-error" role="alert">{{ loadError }}</p>
    <p v-if="loading" role="status">{{ common.loading }}</p>
    <template v-if="account">
      <section class="panel account-panel">
        <dl class="account-summary">
          <div>
            <dt>{{ common.status }}</dt>
            <dd>{{ copy.statuses[account.status] }}</dd>
          </div>
          <div>
            <dt>{{ copy.roles }}</dt>
            <dd>{{ account.roles.map((role) => roleLabels[role]).join(', ') }}</dd>
          </div>
          <div>
            <dt>{{ copy.lastLogin }}</dt>
            <dd>{{ dateTimeLabel(account.lastLoginAt) }}</dd>
          </div>
        </dl>
        <RouterLink :to="`/employees/${account.employeeId}`">{{ copy.employeeProfile }}</RouterLink>
        <p v-if="account.activationPending" class="field-help">{{ copy.pendingHint }}</p>
        <p v-if="self" class="alert alert-info">{{ copy.selfHint }}</p>
        <form v-else @submit.prevent="submit">
          <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
          <p v-if="success" class="alert alert-success" role="status">{{ success }}</p>
          <p v-if="mustReload" class="field-help">{{ copy.reloadHint }}</p>
          <fieldset class="form-fields" :disabled="busy || mustReload">
            <label for="account-action">{{ common.actions }}</label
            ><select id="account-action" v-model="action">
              <option v-for="item in availableActions" :key="item" :value="item">
                {{ copy.actions[item] }}
              </option>
            </select>
            <fieldset v-if="action === accountActions.changeRoles" class="role-fieldset">
              <legend>{{ copy.roles }}</legend>
              <div class="role-options">
                <label v-for="role in roles" :key="role" class="role-option"
                  ><input v-model="selectedRoles" type="checkbox" :value="role" /><span>{{
                    roleLabels[role]
                  }}</span></label
                >
              </div>
            </fieldset>
            <p class="field-help">{{ copy.revokeHint }}</p>
            <label for="account-reason">{{ copy.reason }}</label
            ><textarea
              id="account-reason"
              v-model="reason"
              :maxlength="accountLimits.reason"
              required
              rows="3"
            />
            <label class="confirmation"
              ><input v-model="confirmed" type="checkbox" required /><span>{{
                copy.confirm
              }}</span></label
            >
            <button class="button button-primary" :disabled="!confirmed" type="submit">
              {{ busy ? common.saving : copy.submit }}
            </button>
          </fieldset>
        </form>
      </section>
      <section class="panel account-panel" :aria-busy="historyLoading">
        <div class="list-toolbar">
          <h2>{{ copy.history }}</h2>
          <button
            class="button button-secondary"
            :disabled="historyLoading || busy"
            @click="loadHistory"
          >
            {{ common.reload }}
          </button>
        </div>
        <p v-if="historyError" class="alert alert-error" role="alert">{{ historyError }}</p>
        <p v-if="historyLoading" role="status">{{ common.loading }}</p>
        <p v-else-if="!historyError && !history.content.length">{{ copy.noHistory }}</p>
        <ol v-else class="account-history">
          <li v-for="entry in history.content" :key="entry.id">
            <strong>{{ copy.actions[entry.action] }}</strong>
            <p>{{ entry.actorEmail }} · {{ dateTimeLabel(entry.createdAt) }}</p>
            <p>{{ entry.reason }}</p>
            <p v-if="entry.beforeStatus !== entry.afterStatus">
              {{ copy.statuses[entry.beforeStatus] }} → {{ copy.statuses[entry.afterStatus] }}
            </p>
            <p v-if="entry.beforeRoles !== entry.afterRoles">
              {{ auditRoles(entry.beforeRoles) }} → {{ auditRoles(entry.afterRoles) }}
            </p>
          </li>
        </ol>
        <PageControls
          :page="history.page"
          :pages="history.totalPages"
          :total="history.totalElements"
          :busy="historyLoading || busy"
          @change="auditPage = $event"
        />
      </section>
    </template>
  </div>
</template>
<style scoped>
.account-panel {
  padding: 24px;
  margin-bottom: 20px;
}
.account-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.account-summary dd {
  margin: 8px 0 16px;
}
.account-email,
.account-history li {
  overflow-wrap: anywhere;
}
.account-history {
  padding-left: 20px;
}
.account-history li {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.confirmation {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.confirmation input {
  flex: 0 0 auto;
  width: auto;
}
@media (max-width: 760px) {
  .account-summary {
    grid-template-columns: 1fr;
  }
  .account-panel {
    padding: 16px;
  }
}
</style>
