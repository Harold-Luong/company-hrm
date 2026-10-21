<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { fieldLimits } from '@/config/validation.js'

import { employeeForm as copy } from '@/content/hrm.js'
import { common } from '@/content/common.js'

import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hrm } from '@/hrm/api.js'
import { employeeStatuses, accountStatuses } from '@/hrm/constants.js'
import { fullName } from '@/hrm/utils.js'
import { serviceError } from '@/api/errors.js'
import { auth } from '@/auth/session.js'
import { personnelManagementRoles } from '@/auth/roles.js'
import { formatDate } from '@/shared/date.js'
import ReferencePicker from '@/components/ReferencePicker.vue'
import ProvisioningPanel from '@/components/ProvisioningPanel.vue'
const props = defineProps({ id: { type: String, default: '' } })
const router = useRouter()
const form = reactive({
  firstName: '',
  lastName: '',
  personalEmail: '',
  phone: '',
  dateOfBirth: '',
  gender: '',
  address: '',
  contactRelative: '',
  contactRelativePhone: '',
  hireDate: '',
  status: 'PROBATION',
  departmentId: '',
  positionId: '',
  managerId: '',
})
const employee = ref(null)
const loading = ref(!!props.id)
const loaded = ref(!props.id)
const busy = ref(false)
const error = useLocalizedMessage()
const success = useLocalizedMessage()
const nextStatus = ref('')
const yesterdayDate = new Date()
yesterdayDate.setDate(yesterdayDate.getDate() - 1)
const yesterday = `${yesterdayDate.getFullYear()}-${String(yesterdayDate.getMonth() + 1).padStart(2, '0')}-${String(yesterdayDate.getDate()).padStart(2, '0')}`
function populate(data) {
  employee.value = data
  for (const key of Object.keys(form)) form[key] = data[key] ?? ''
  form.departmentId = data.department?.id || ''
  form.positionId = data.position?.id || ''
  form.managerId = data.manager?.id || ''
  nextStatus.value = data.status
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    populate(await hrm.get('employees', props.id))
    loaded.value = true
  } catch (cause) {
    error.value = () => serviceError(cause)
  } finally {
    loading.value = false
  }
}
async function save(statusOnly = false) {
  if (busy.value) return
  error.value = ''
  success.value = ''
  if (!statusOnly && form.dateOfBirth && form.dateOfBirth >= form.hireDate) {
    error.value = () => copy.invalidBirthDate
    return
  }
  busy.value = true
  try {
    const payload = Object.fromEntries(
      Object.entries(form).map(([key, value]) => [
        key,
        typeof value === 'string' ? value.trim() || null : value,
      ]),
    )
    const result = statusOnly
      ? await hrm.status(props.id, nextStatus.value)
      : await hrm.save('employees', props.id, payload)
    if (!props.id) await router.replace(`/employees/${result.id}`)
    else {
      if (statusOnly) {
        employee.value = result
        form.status = result.status
        nextStatus.value = result.status
      } else populate(result)
      success.value = () => (statusOnly ? copy.statusUpdated : copy.saved)
    }
  } catch (cause) {
    error.value = () => serviceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  if (props.id) load()
})
</script>
<template>
  <div>
    <RouterLink to="/employees" class="back-link">{{ copy.back }}</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ employee ? fullName(employee) : copy.createTitle }}</h1>
        <p class="muted">
          {{ employee ? copy.editDescription : copy.createDescription }}
        </p>
      </div>
      <span v-if="employee" class="status-chip">{{ accountStatuses[employee.accountStatus] }}</span>
    </div>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
    <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
    <p v-if="loading" role="status">{{ copy.loading }}</p>
    <button v-else-if="!loaded" class="button button-secondary" @click="load">
      {{ common.retry }}
    </button>
    <div v-if="loaded" class="employee-layout" :class="{ 'employee-layout-new': !id }">
      <form class="panel create-form" :aria-busy="busy" @submit.prevent="save()">
        <div class="panel-heading">
          <h2>{{ copy.formTitle }}</h2>
          <span class="small muted">{{ copy.requiredHint }}</span>
        </div>
        <fieldset class="form-fields employee-form-sections" :disabled="busy">
          <section class="employee-form-section" aria-labelledby="personal-heading">
            <div class="employee-section-heading">
              <h3 id="personal-heading">{{ copy.personalTitle }}</h3>
              <p class="muted small">{{ copy.personalDescription }}</p>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="last-name">{{ copy.lastName }}</label
                ><input
                  id="last-name"
                  v-model="form.lastName"
                  required
                  :maxlength="fieldLimits.employeeName"
                  pattern=".*\S.*"
                />
              </div>
              <div class="field">
                <label for="first-name">{{ copy.firstName }}</label
                ><input
                  id="first-name"
                  v-model="form.firstName"
                  required
                  :maxlength="fieldLimits.employeeName"
                  pattern=".*\S.*"
                />
              </div>
              <div class="field">
                <label for="birth-date">{{ copy.birthDate }}</label
                ><input id="birth-date" v-model="form.dateOfBirth" type="date" :max="yesterday" />
              </div>
              <div class="field">
                <label for="employee-gender">{{ copy.gender }}</label>
                <select id="employee-gender" v-model="form.gender" required>
                  <option disabled value="">{{ copy.selectGender }}</option>
                  <option value="MALE">{{ copy.male }}</option>
                  <option value="FEMALE">{{ copy.female }}</option>
                  <option value="OTHER">{{ copy.other }}</option>
                </select>
              </div>
            </div>
          </section>
          <section class="employee-form-section" aria-labelledby="contact-heading">
            <div class="employee-section-heading">
              <h3 id="contact-heading">{{ copy.contactTitle }}</h3>
              <p class="muted small">{{ copy.contactDescription }}</p>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="employee-email">{{ copy.personalEmail }}</label
                ><input
                  id="employee-email"
                  v-model="form.personalEmail"
                  required
                  type="email"
                  :maxlength="fieldLimits.email"
                />
              </div>
              <div class="field">
                <label for="phone">{{ copy.phone }}</label
                ><input id="phone" v-model="form.phone" type="tel" :maxlength="fieldLimits.phone" />
              </div>
              <div class="field span-all">
                <label for="employee-address">{{ common.address }}</label>
                <input
                  id="employee-address"
                  v-model="form.address"
                  :maxlength="fieldLimits.address"
                />
              </div>
            </div>
          </section>
          <section class="employee-form-section" aria-labelledby="work-heading">
            <div class="employee-section-heading">
              <h3 id="work-heading">{{ copy.workTitle }}</h3>
              <p class="muted small">{{ copy.workDescription }}</p>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="hire-date">{{ copy.hireDate }}</label
                ><input id="hire-date" v-model="form.hireDate" type="date" required />
              </div>
              <div class="field">
                <label for="employee-status">{{ copy.employmentStatus }}</label
                ><select id="employee-status" v-model="form.status" required>
                  <option v-for="(label, value) in employeeStatuses" :key="value" :value="value">
                    {{ label }}
                  </option>
                </select>
              </div>
              <ReferencePicker
                v-model="form.departmentId"
                resource="departments"
                :label="common.department"
                :selected-label="employee?.department?.name"
              />
              <ReferencePicker
                v-model="form.positionId"
                resource="positions"
                :label="common.position"
                :selected-label="employee?.position?.name"
              />
              <ReferencePicker
                v-model="form.managerId"
                resource="employees"
                :label="copy.manager"
                :exclude="id"
                :selected-label="employee?.manager ? fullName(employee.manager) : ''"
              />
            </div>
          </section>
          <section class="employee-form-section" aria-labelledby="relative-heading">
            <div class="employee-section-heading">
              <h3 id="relative-heading">{{ copy.emergencyContactTitle }}</h3>
              <p class="muted small">{{ copy.emergencyContactDescription }}</p>
            </div>
            <div class="form-grid">
              <div class="field">
                <label for="contact-relative">{{ copy.emergencyContactTitle }}</label>
                <input
                  id="contact-relative"
                  v-model="form.contactRelative"
                  :maxlength="fieldLimits.emergencyContact"
                />
              </div>
              <div class="field">
                <label for="contact-relative-phone">{{ copy.emergencyPhone }}</label>
                <input
                  id="contact-relative-phone"
                  v-model="form.contactRelativePhone"
                  type="tel"
                  :maxlength="fieldLimits.phone"
                />
              </div>
            </div>
          </section>
          <div class="form-footer">
            <RouterLink to="/employees" class="button button-secondary">{{
              common.back
            }}</RouterLink
            ><button type="submit" class="button button-primary">
              {{ busy ? common.saving : id ? copy.save : copy.create }}
            </button>
          </div>
        </fieldset>
      </form>
      <aside v-if="employee" class="employee-aside">
        <section class="panel create-form">
          <h2>{{ copy.statusTitle }}</h2>
          <p class="muted small">{{ copy.statusDescription }}</p>
          <form @submit.prevent="save(true)">
            <fieldset class="form-fields" :disabled="busy">
              <label for="quick-status">{{ copy.newStatus }}</label
              ><select id="quick-status" v-model="nextStatus">
                <option v-for="(label, value) in employeeStatuses" :key="value" :value="value">
                  {{ label }}
                </option></select
              ><button
                type="submit"
                class="button button-secondary full-width"
                :disabled="nextStatus === employee.status"
              >
                {{ copy.updateStatus }}
              </button>
            </fieldset>
          </form>
          <dl class="details-list">
            <div>
              <dt>{{ copy.createdAt }}</dt>
              <dd>{{ formatDate(employee.createdAt) }}</dd>
            </div>
            <div>
              <dt>{{ copy.updatedAt }}</dt>
              <dd>{{ formatDate(employee.updatedAt) }}</dd>
            </div>
          </dl>
        </section>
        <ProvisioningPanel
          v-if="auth.hasRole(personnelManagementRoles)"
          :employee="employee"
          @updated="employee.accountStatus = $event"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.employee-layout-new {
  grid-template-columns: minmax(0, 1fr);
  max-width: 1000px;
}

.employee-form-sections {
  display: grid;
  gap: 28px;
}

.employee-form-section + .employee-form-section {
  border-top: 1px solid var(--border);
  padding-top: 28px;
}

.employee-section-heading {
  margin-bottom: 20px;
}

.employee-section-heading h3 {
  margin: 0 0 6px;
  color: var(--primary-dark);
  font-size: 15px;
}

.employee-section-heading p {
  margin: 0;
  line-height: 1.6;
}

.employee-form-sections > .form-footer {
  margin-top: 0;
}
</style>
