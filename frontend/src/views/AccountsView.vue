<script setup>
import { reactive, ref } from 'vue'
import { accountsApi } from '@/accounts/api.js'
import { accountStatuses, accountLimits } from '@/accounts/constants.js'
import { accountManagementError } from '@/accounts/errors.js'
import { accountManagement as copy } from '@/content/accounts.js'
import { common } from '@/content/common.js'
import { roles, roleLabels } from '@/auth/roles.js'
import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { emptyPage } from '@/api/pagination.js'
import { dateTimeLabel } from '@/shared/date.js'
import PageControls from '@/components/PageControls.vue'

const filters = reactive({ search: '', status: '', role: '' })
const applied = ref({ ...filters })
const page = ref(0)
const {
  data: result,
  loading,
  error,
  load,
} = useAsyncResource(() => accountsApi.list(applied.value, page.value), {
  initialValue: emptyPage,
  source: () => [applied.value, page.value],
  errorMessage: accountManagementError,
})
function apply() {
  page.value = 0
  applied.value = { ...filters, search: filters.search.trim() }
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
      <RouterLink to="/accounts/new" class="button button-primary">{{ copy.create }}</RouterLink>
    </div>
    <form class="panel account-filters" @submit.prevent="apply">
      <div class="field">
        <label for="account-search">{{ copy.search }}</label
        ><input
          id="account-search"
          v-model="filters.search"
          :maxlength="accountLimits.search"
          type="search"
        />
      </div>
      <div class="field">
        <label for="account-status">{{ common.status }}</label
        ><select id="account-status" v-model="filters.status">
          <option value="">{{ common.all }}</option>
          <option v-for="status in accountStatuses" :key="status" :value="status">
            {{ copy.statuses[status] }}
          </option>
        </select>
      </div>
      <div class="field">
        <label for="account-role">{{ copy.roles }}</label
        ><select id="account-role" v-model="filters.role">
          <option value="">{{ common.all }}</option>
          <option v-for="role in roles" :key="role" :value="role">{{ roleLabels[role] }}</option>
        </select>
      </div>
      <button class="button button-secondary" :disabled="loading">{{ copy.apply }}</button>
    </form>
    <section class="panel directory-panel" :aria-busy="loading">
      <div class="list-toolbar">
        <h2>{{ copy.list }}</h2>
        <button class="button button-secondary" :disabled="loading" @click="load">
          {{ common.reload }}
        </button>
      </div>
      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <p v-if="loading" class="table-message" role="status">{{ common.loading }}</p>
      <p v-else-if="!error && !result.content.length" class="table-message" role="status">
        {{ copy.empty }}
      </p>
      <div v-else-if="!error" class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>{{ common.workEmail }}</th>
              <th>{{ common.status }}</th>
              <th>{{ copy.roles }}</th>
              <th>{{ copy.lastLogin }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="account in result.content" :key="account.id">
              <td>
                <RouterLink :to="`/accounts/${account.id}`">{{ account.email }}</RouterLink>
              </td>
              <td>{{ copy.statuses[account.status] }}</td>
              <td>{{ account.roles.map((role) => roleLabels[role]).join(', ') }}</td>
              <td>{{ dateTimeLabel(account.lastLoginAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls
        :page="result.page"
        :pages="result.totalPages"
        :total="result.totalElements"
        :busy="loading"
        @change="page = $event"
      />
    </section>
  </div>
</template>
<style scoped>
.account-filters {
  display: grid;
  grid-template-columns: minmax(0, 2fr) repeat(2, minmax(0, 1fr)) auto;
  gap: 16px;
  align-items: end;
  padding: 20px;
  margin-bottom: 20px;
}
.field {
  display: grid;
  gap: 8px;
  min-width: 0;
}
.table-scroll {
  overflow-x: auto;
}
@media (max-width: 760px) {
  .account-filters {
    grid-template-columns: 1fr;
  }
}
</style>
