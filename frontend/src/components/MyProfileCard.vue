<script setup>
import { dateLabel } from '@/shared/date.js'
import { myProfileCard as copy } from '@/content/hrm.js'
import { common } from '@/content/common.js'

import { computed, nextTick, ref } from 'vue'
import { employeeStatuses } from '@/hrm/constants.js'
import { fullName } from '@/hrm/utils.js'
import AppIcon from '@/components/AppIcon.vue'
import AuthAccountPanel from '@/components/AuthAccountPanel.vue'
import ProfileAttendancePanel from '@/components/ProfileAttendancePanel.vue'
import ProfileLeavePanel from '@/components/ProfileLeavePanel.vue'
import '@/assets/styles/profile-modules.css'

const props = defineProps({
  employee: { type: Object, default: null },
  loading: Boolean,
  error: { type: String, default: '' },
})
defineEmits(['retry'])
const activeTab = ref('personal')
const tabs = [
  {
    id: 'personal',
    get label() {
      return copy.personalTab
    },
  },
  {
    id: 'work',
    get label() {
      return copy.workTab
    },
  },
  {
    id: 'attendance',
    get label() {
      return copy.attendanceTab
    },
  },
  {
    id: 'leave',
    get label() {
      return copy.leaveTab
    },
  },
  {
    id: 'account',
    get label() {
      return copy.accountTab
    },
  },
]
const tabButtons = ref([])
const genders = {
  get MALE() {
    return copy.male
  },
  get FEMALE() {
    return copy.female
  },
  get OTHER() {
    return copy.other
  },
}
const displayValue = (value) => value || copy.notUpdated
const dateOnly = (value) => (value ? dateLabel(value) : copy.notUpdated)
const statusLabel = computed(() => employeeStatuses[props.employee?.status] || copy.notUpdated)
const personalFields = computed(() =>
  props.employee
    ? [
        [common.fullName, fullName(props.employee)],
        [copy.birthDate, dateOnly(props.employee.dateOfBirth)],
        [copy.gender, genders[props.employee.gender]],
        [common.phone, props.employee.phone],
        [copy.personalEmail, props.employee.personalEmail],
        [common.address, props.employee.address],
      ]
    : [],
)
const workFields = computed(() =>
  props.employee
    ? [
        [common.employeeCode, props.employee.employeeCode],
        [copy.hireDate, dateOnly(props.employee.hireDate)],
        [common.department, props.employee.department?.name],
        [common.position, props.employee.position?.name],
        [
          copy.manager,
          props.employee.manager ? fullName(props.employee.manager) : copy.notAvailable,
        ],
        [copy.employmentStatus, statusLabel.value],
      ]
    : [],
)
async function navigateTabs(event) {
  const index = tabs.findIndex((tab) => tab.id === activeTab.value)
  let next
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return
  event.preventDefault()
  activeTab.value = tabs[next].id
  await nextTick()
  tabButtons.value[next]?.focus()
}
</script>

<template>
  <section class="panel account-panel employee-profile" :aria-label="copy.title">
    <header v-if="employee" class="employee-profile-header">
      <span class="avatar employee-profile-avatar">
        <AppIcon name="user" :size="32" />
      </span>
      <div class="employee-profile-identity">
        <h2>{{ fullName(employee) }}</h2>
        <p>{{ employee.employeeCode }} · {{ displayValue(employee.position?.name) }}</p>
        <div class="employee-profile-meta">
          <span class="status-chip" :class="employee.status?.toLowerCase()">{{ statusLabel }}</span>
          <span>{{ displayValue(employee.department?.name) }}</span>
        </div>
      </div>
    </header>

    <nav class="employee-profile-navigation" :aria-label="copy.tabsLabel">
      <div
        class="employee-profile-tabs"
        role="tablist"
        :aria-label="copy.tabsLabel"
        @keydown="navigateTabs"
      >
        <button
          v-for="tab in tabs"
          :id="`employee-${tab.id}-tab`"
          :key="tab.id"
          ref="tabButtons"
          type="button"
          role="tab"
          class="employee-profile-tab"
          :class="{ selected: activeTab === tab.id }"
          :aria-selected="activeTab === tab.id"
          :aria-controls="`employee-${tab.id}-panel`"
          :tabindex="activeTab === tab.id ? 0 : -1"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>
    </nav>

    <div
      v-for="tab in tabs"
      v-show="activeTab === tab.id"
      :id="`employee-${tab.id}-panel`"
      :key="tab.id"
      role="tabpanel"
      :aria-labelledby="`employee-${tab.id}-tab`"
      tabindex="0"
    >
      <template v-if="(tab.id === 'personal' || tab.id === 'work') && activeTab === tab.id">
        <p v-if="loading" class="muted" role="status">{{ copy.loading }}</p>
        <div v-else-if="error" class="alert alert-error" role="alert">
          <p>{{ error }}</p>
          <button class="button button-secondary" @click="$emit('retry')">
            {{ common.retry }}
          </button>
        </div>
        <p v-else-if="!employee" class="muted">{{ copy.unlinked }}</p>
        <dl v-if="employee" class="profile-fields">
          <div
            v-for="[label, value] in tab.id === 'personal' ? personalFields : workFields"
            :key="label"
          >
            <dt>{{ label }}</dt>
            <dd>{{ displayValue(value) }}</dd>
          </div>
        </dl>
        <template
          v-if="
            tab.id === 'personal' &&
            employee &&
            (employee.contactRelative || employee.contactRelativePhone)
          "
        >
          <h3 class="employee-profile-subheading">{{ copy.emergencyContact }}</h3>
          <dl class="profile-fields">
            <div>
              <dt>{{ common.fullName }}</dt>
              <dd>{{ displayValue(employee.contactRelative) }}</dd>
            </div>
            <div>
              <dt>{{ common.phone }}</dt>
              <dd>{{ displayValue(employee.contactRelativePhone) }}</dd>
            </div>
          </dl>
        </template>
      </template>
      <AuthAccountPanel v-if="tab.id === 'account' && activeTab === 'account'" />
      <ProfileAttendancePanel v-if="tab.id === 'attendance' && activeTab === 'attendance'" />
      <ProfileLeavePanel v-if="tab.id === 'leave' && activeTab === 'leave'" />
    </div>
    <p
      v-if="employee && (activeTab === 'personal' || activeTab === 'work')"
      class="employee-profile-note"
    >
      {{ copy.updateHint }}
    </p>
  </section>
</template>

<style scoped>
.employee-profile {
  font-size: 14px;
  line-height: 1.6;
}

.employee-profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--border);
}

.employee-profile-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 76px;
  height: 76px;
  border-radius: 50%;
}

.employee-profile-identity {
  min-width: 0;
  overflow-wrap: anywhere;
}

.employee-profile-identity h2 {
  margin: 0 0 3px;
  font-size: 20px;
}

.employee-profile-identity p {
  margin: 0 0 4px;
  color: var(--muted);
  font-size: 14px;
}

.employee-profile-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 14px;
  color: var(--muted);
}

.employee-profile .status-chip.suspended {
  background: #f9edef;
  color: #a34150;
}

.employee-profile-navigation,
.employee-profile-tabs {
  flex-wrap: wrap;
  display: flex;
  align-items: center;
  gap: 6px;
}

.employee-profile-navigation {
  flex-wrap: wrap;
  margin: 24px 0 16px;
}

.employee-profile-tab {
  display: inline-flex;
  justify-content: center;
  padding: 6px 17px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  white-space: nowrap;
}

.employee-profile-tab:hover {
  background: #f6f8fb;
  color: var(--primary-dark);
}

.employee-profile-tab.selected {
  background: var(--primary);
  color: white;
  font-weight: 600;
  box-shadow: inset 0 -3px var(--primary-dark);
}

.employee-profile :focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.employee-profile-subheading {
  margin: 24px 0 14px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  color: var(--muted);
}

.employee-profile-note {
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 14px;
}

@media (max-width: 600px) {
  .employee-profile {
    padding: 20px 16px;
  }

  .employee-profile-header {
    gap: 12px;
  }

  .employee-profile-avatar {
    flex-basis: 56px;
    height: 56px;
  }

  .employee-profile-identity h2 {
    font-size: 20px;
  }

  .employee-profile-navigation,
  .employee-profile-tabs {
    gap: 2px;
  }

  .employee-profile-tab {
    padding: 6px 10px;
    font-size: 14px;
  }
}
</style>
