<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { directory as copy } from '@/content/hrm.js'
import { common } from '@/content/common.js'

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { hrm } from '@/hrm/api.js'
import { fullName } from '@/hrm/utils.js'
import { employeeStatuses, accountStatuses } from '@/hrm/constants.js'
import { serviceError } from '@/api/errors.js'
import PageControls from '@/components/PageControls.vue'
import { subscribeAccountResults } from '@/notifications/accountResults.js'
const props = defineProps({ resource: { type: String, required: true } })
const title = computed(
  () =>
    ({
      get employees() {
        return common.employee
      },
      get departments() {
        return common.department
      },
      get positions() {
        return common.position
      },
    })[props.resource],
)
const rows = ref([])
const page = ref(0)
const pages = ref(0)
const total = ref(0)
const busy = ref(false)
const error = useLocalizedMessage()
const pendingAccountUpdates = new Map()
function updateAccount(employeeId, accountStatus) {
  const row = rows.value.find((item) => item.id === employeeId)
  if (row) row.accountStatus = accountStatus
}
const stopAccountResults = subscribeAccountResults((result) => {
  if (props.resource !== 'employees') return
  updateAccount(result.employeeId, result.accountStatus)
  if (busy.value) pendingAccountUpdates.set(result.employeeId, result.accountStatus)
})
onBeforeUnmount(stopAccountResults)
async function load(next = page.value) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const data = await hrm.list(props.resource, next)
    rows.value = data.content
    page.value = data.page
    pages.value = data.totalPages
    total.value = data.totalElements
  } catch (cause) {
    error.value = () => serviceError(cause)
  } finally {
    for (const [employeeId, status] of pendingAccountUpdates) updateAccount(employeeId, status)
    pendingAccountUpdates.clear()
    busy.value = false
  }
}
onMounted(() => load())
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p class="muted">
          {{ resource === 'employees' ? copy.employeeDescription : copy.catalogDescription }}
        </p>
      </div>
      <RouterLink :to="`/${resource}/new`" class="button button-primary"
        >{{ copy.create({ resource: title.toLowerCase() }) }}
      </RouterLink>
    </div>
    <section class="panel directory-panel" :aria-busy="busy">
      <div class="list-toolbar">
        <h2>{{ copy.listTitle({ resource: title.toLowerCase() }) }}</h2>
        <button class="button button-secondary" :disabled="busy" @click="load()">
          {{ busy ? common.loading : common.reload }}
        </button>
      </div>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <p v-if="busy" class="table-message" role="status">{{ copy.loading }}</p>
      <p v-else-if="!rows.length && !error" class="table-message">
        {{ copy.empty({ resource: title.toLowerCase(), createResource: title.toLowerCase() }) }}
      </p>
      <div
        v-if="rows.length"
        class="table-scroll"
        tabindex="0"
        :aria-label="copy.tableLabel({ resource: title.toLowerCase() })"
      >
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">{{ copy.code }}</th>
              <th scope="col">{{ resource === 'employees' ? common.fullName : copy.name }}</th>
              <template v-if="resource === 'employees'">
                <th scope="col">{{ copy.organization }}</th>
                <th scope="col">{{ copy.work }}</th>
                <th scope="col">{{ copy.account }}</th>
              </template>
              <th v-else scope="col">{{ copy.description }}</th>
              <th scope="col">{{ common.actions }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td class="muted">{{ row.employeeCode || row.code }}</td>
              <td>
                <RouterLink class="record-link" :to="`/${resource}/${row.id}`">{{
                  resource === 'employees' ? fullName(row) : row.name
                }}</RouterLink
                ><small v-if="resource === 'employees'" class="cell-secondary">{{
                  row.personalEmail
                }}</small>
              </td>
              <template v-if="resource === 'employees'">
                <td>
                  {{ row.department?.name || common.missingValue
                  }}<small class="cell-secondary">{{
                    row.position?.name || common.missingValue
                  }}</small>
                </td>
                <td>
                  <span class="status-chip" :class="row.status.toLowerCase()">{{
                    employeeStatuses[row.status] || row.status
                  }}</span>
                </td>
                <td>{{ accountStatuses[row.accountStatus] || row.accountStatus }}</td>
              </template>
              <td v-else>{{ row.description || common.missingValue }}</td>
              <td>
                <RouterLink class="text-button" :to="`/${resource}/${row.id}`">{{
                  common.viewDetails
                }}</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls :page="page" :pages="pages" :total="total" :busy="busy" @change="load" />
    </section>
  </div>
</template>
