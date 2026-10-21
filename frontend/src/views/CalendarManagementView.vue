<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { pagination } from '@/config/presentation.js'

import { dateLimits } from '@/config/validation.js'

import { calendarManagement as copy } from '@/content/calendar.js'
import { common } from '@/content/common.js'

import { emptyPage } from '@/api/pagination.js'
import { computed, onMounted, reactive, ref } from 'vue'
import { calendarManagement } from '@/calendar/api.js'
import { eventTypes, eventStatuses } from '@/calendar/constants.js'
import { calendarDate, displayEventRange } from '@/calendar/utils.js'
import { calendarManagementError, sortManagedEvents } from '@/calendar/management.js'
import PageControls from '@/components/PageControls.vue'

const year = calendarDate().slice(0, 4)
const filters = reactive({
  from: `${year}-01-01`,
  to: `${year}-12-31`,
  type: '',
  status: '',
  size: pagination.pageSize,
})
const applied = ref({ ...filters })
const result = ref(emptyPage())
const sortedEvents = computed(() => sortManagedEvents(result.value.content))
const busy = ref(false)
const error = useLocalizedMessage()
async function load(page = 0, query = applied.value) {
  if (busy.value) return
  const days =
    (Date.parse(`${query.to}T00:00:00Z`) - Date.parse(`${query.from}T00:00:00Z`)) / 86400000
  if (!Number.isFinite(days) || days < 0 || days >= dateLimits.maxRangeDays) {
    error.value = () => copy.invalidRange
    return
  }
  busy.value = true
  error.value = ''
  try {
    result.value = await calendarManagement.list(query, page)
    applied.value = { ...query }
  } catch (cause) {
    error.value = () => calendarManagementError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => load())
</script>

<template>
  <div class="calendar-management">
    <RouterLink to="/calendar" class="back-link">{{ copy.back }}</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <RouterLink to="/calendar-events/new" class="button button-primary">{{
        copy.create
      }}</RouterLink>
    </div>
    <form class="panel calendar-filters" @submit.prevent="load(0, { ...filters })">
      <fieldset class="form-fields calendar-filter-grid" :disabled="busy">
        <div class="field">
          <label for="event-from">{{ common.fromDate }}</label
          ><input id="event-from" v-model="filters.from" type="date" required />
        </div>
        <div class="field">
          <label for="event-to">{{ common.toDate }}</label
          ><input id="event-to" v-model="filters.to" type="date" :min="filters.from" required />
        </div>
        <div class="field">
          <label for="event-type-filter">{{ copy.type }}</label
          ><select id="event-type-filter" v-model="filters.type">
            <option value="">{{ copy.allTypes }}</option>
            <option v-for="(label, value) in eventTypes" :key="value" :value="value">
              {{ label }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="event-status-filter">{{ common.status }}</label
          ><select id="event-status-filter" v-model="filters.status">
            <option value="">{{ copy.allStatuses }}</option>
            <option v-for="(label, value) in eventStatuses" :key="value" :value="value">
              {{ label }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="event-page-size">{{ copy.pageSize }}</label
          ><select id="event-page-size" v-model.number="filters.size">
            <option v-for="size in pagination.calendarPageSizes" :key="size" :value="size">
              {{ size }}
            </option>
          </select>
        </div>
        <button class="button button-secondary" type="submit">{{ copy.apply }}</button>
      </fieldset>
      <p class="field-help">{{ copy.filterHint }}</p>
    </form>
    <section class="panel directory-panel" :aria-busy="busy">
      <div class="list-toolbar">
        <h2>{{ copy.listTitle }}</h2>
        <button class="button button-secondary" :disabled="busy" @click="load(result.page)">
          {{ common.reload }}
        </button>
      </div>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <p v-if="busy" role="status" class="table-message">{{ copy.loading }}</p>
      <p v-else-if="!error && !result.content.length" role="status" class="table-message">
        {{ copy.empty }}
      </p>
      <div
        v-if="!busy && !error && result.content.length"
        class="table-scroll"
        tabindex="0"
        :aria-label="copy.listTitle"
      >
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">{{ copy.event }}</th>
              <th scope="col">{{ copy.time }}</th>
              <th scope="col">{{ common.status }}</th>
              <th scope="col">{{ common.actions }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in sortedEvents" :key="event.id">
              <td>
                <RouterLink class="record-link" :to="`/calendar-events/${event.id}`">{{
                  event.title
                }}</RouterLink
                ><small class="cell-secondary">{{ eventTypes[event.type] || event.type }}</small>
              </td>
              <td>
                {{ displayEventRange(event)
                }}<small v-if="event.location" class="cell-secondary">{{ event.location }}</small>
              </td>
              <td>
                <span class="event-status" :class="`status-${event.status}`">{{
                  eventStatuses[event.status] || event.status
                }}</span>
              </td>
              <td>
                <RouterLink class="text-button" :to="`/calendar-events/${event.id}`"
                  >{{ common.viewDetails }}
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls
        :page="result.page"
        :pages="result.totalPages"
        :total="result.totalElements"
        :busy="busy"
        @change="load($event)"
      />
    </section>
  </div>
</template>

<style scoped src="@/assets/styles/calendar-management.css"></style>
