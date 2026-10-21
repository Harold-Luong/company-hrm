<script setup>
import { getDisplayLocale } from '@/i18n/index.js'

import { dateLimits } from '@/config/validation.js'

import { profileLeave as copy } from '@/content/leave.js'
import { common } from '@/content/common.js'

import { computed, ref } from 'vue'
import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import { dateLabel, today } from '@/shared/date.js'
import { leaveTypes, leaveStatuses, leavePeriods } from '@/leave/constants.js'
import PageControls from '@/components/PageControls.vue'

const employeeId = computed(() => auth.state.user?.employeeId)
const year = ref(Number(today().slice(0, 4)))
const status = ref('')
const page = ref(0)
const daysLabel = (value) =>
  value == null ? common.missingValue : Number(value).toLocaleString(getDisplayLocale())

const {
  data: balance,
  loading: balanceLoading,
  error: balanceError,
  load: balanceRetry,
} = useAsyncResource(
  () => {
    if (
      !Number.isInteger(year.value) ||
      year.value < dateLimits.minYear ||
      year.value > dateLimits.maxYear
    ) {
      throw new RangeError(copy.invalidYear)
    }
    return leave.balance(year.value)
  },
  {
    source: [employeeId, year],
    enabled: () => !!employeeId.value,
    errorMessage: (cause) => (cause instanceof RangeError ? cause.message : copy.balanceFailed),
  },
)
const {
  data: result,
  loading: requestsLoading,
  error: requestsError,
  load: requestsRetry,
} = useAsyncResource(() => leave.list(false, status.value, page.value, 5), {
  source: [employeeId, status, page],
  enabled: () => !!employeeId.value,
  errorMessage: () => copy.historyFailed,
})
function refresh() {
  balanceRetry()
  requestsRetry()
}
</script>

<template>
  <section class="profile-module profile-leave" :aria-label="copy.sectionLabel">
    <div class="profile-module-heading">
      <h3>{{ copy.sectionLabel }}</h3>
      <div v-if="employeeId" class="button-row">
        <button
          class="button button-secondary"
          :disabled="balanceLoading || requestsLoading"
          @click="refresh"
        >
          {{ common.reload }}
        </button>
        <RouterLink class="button button-primary" to="/leave">{{ copy.create }}</RouterLink>
      </div>
    </div>
    <p v-if="!employeeId" class="muted" role="status">{{ copy.unlinked }}</p>
    <template v-else>
      <section :aria-label="copy.balanceTitle" :aria-busy="balanceLoading">
        <div class="profile-module-heading">
          <h4>{{ copy.balanceYear({ year: year }) }}</h4>
          <div class="field">
            <label for="profile-leave-year">{{ copy.yearLabel }}</label>
            <input
              id="profile-leave-year"
              v-model.number="year"
              type="number"
              :min="dateLimits.minYear"
              :max="dateLimits.maxYear"
              step="1"
              required
            />
          </div>
        </div>
        <p v-if="balanceLoading" class="muted" role="status">{{ copy.balanceLoading }}</p>
        <div v-else-if="balanceError" class="alert alert-error" role="alert">
          <p>{{ balanceError }}</p>
          <button class="button button-secondary" @click="balanceRetry()">
            {{ common.retry }}
          </button>
        </div>
        <dl v-else-if="balance" class="profile-module-stats">
          <div>
            <dt>{{ copy.entitlement }}</dt>
            <dd>
              {{ daysLabel(balance.entitledDays) }} <small>{{ copy.dayUnit }}</small>
            </dd>
          </div>
          <div>
            <dt>{{ copy.used }}</dt>
            <dd>
              {{ daysLabel(balance.usedDays) }} <small>{{ copy.dayUnit }}</small>
            </dd>
          </div>
          <div>
            <dt>{{ copy.remaining }}</dt>
            <dd>
              {{ daysLabel(balance.remainingDays) }} <small>{{ copy.dayUnit }}</small>
            </dd>
          </div>
        </dl>
        <p class="muted small">{{ copy.balanceHint }}</p>
      </section>

      <section
        class="profile-leave-history"
        :aria-label="copy.historyTitle"
        :aria-busy="requestsLoading"
      >
        <div class="profile-module-heading">
          <div>
            <h4>{{ copy.historyTitle }}</h4>
            <p class="muted small">{{ copy.historyDescription }}</p>
          </div>
          <div class="field">
            <label for="profile-leave-status">{{ copy.statusLabel }}</label>
            <select id="profile-leave-status" v-model="status" @change="page = 0">
              <option value="">{{ common.all }}</option>
              <option v-for="(label, value) in leaveStatuses" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
        </div>
        <p v-if="requestsLoading" class="muted" role="status">{{ copy.historyLoading }}</p>
        <div v-else-if="requestsError" class="alert alert-error" role="alert">
          <p>{{ requestsError }}</p>
          <button class="button button-secondary" @click="requestsRetry()">
            {{ common.retry }}
          </button>
        </div>
        <template v-else-if="result">
          <ul v-if="result.content.length" class="profile-module-requests">
            <li v-for="request in result.content" :key="request.id">
              <div>
                <strong>{{ leaveTypes[request.leaveType] || request.leaveType }}</strong>
                <p class="muted small">
                  {{ dateLabel(request.startDate) }} – {{ dateLabel(request.endDate) }} ·
                  {{ leavePeriods[request.period] || copy.fullDay }}
                  <template v-if="request.totalUnits != null">
                    {{ copy.durationDays({ days: daysLabel(request.totalUnits / 2) }) }}</template
                  >
                </p>
              </div>
              <span
                class="status-chip"
                :class="{
                  active: request.status === 'APPROVED',
                  probation: request.status === 'PENDING',
                  terminated: request.status === 'REJECTED',
                  'profile-leave-cancelled': request.status === 'CANCELLED',
                }"
                >{{ leaveStatuses[request.status] || request.status }}</span
              >
              <RouterLink class="text-button" :to="`/leave/requests/${request.id}`">{{
                copy.viewHistory
              }}</RouterLink>
            </li>
          </ul>
          <p v-else class="muted" role="status">
            {{ status ? copy.filteredEmpty : copy.empty }}
          </p>
          <PageControls
            v-if="result.totalPages > 1"
            :page="result.page"
            :pages="result.totalPages"
            :total="result.totalElements"
            :busy="requestsLoading"
            @change="page = $event"
          />
        </template>
      </section>
      <RouterLink class="text-button profile-module-more" to="/leave">{{
        copy.managementLink
      }}</RouterLink>
    </template>
  </section>
</template>

<style scoped>
.profile-leave h4 {
  margin: 0;
  font-size: 15px;
}
.profile-leave-history {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.profile-leave-history .profile-module-heading p {
  margin: 4px 0 0;
}
.profile-leave-cancelled {
  background: #f1f3f5;
  color: var(--muted);
}
.profile-leave :deep(.pagination) {
  flex-wrap: wrap;
  gap: 12px;
}
</style>
