<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hrm, employeeStatuses, accountStatuses, fullName } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { formatDate } from '@/auth/format.js'
import ReferencePicker from '@/components/ReferencePicker.vue'
import ProvisioningPanel from '@/components/ProvisioningPanel.vue'
const props = defineProps({ id: { type: String, default: '' } })
const router = useRouter()
const form = reactive({
  employeeCode: '',
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  dateOfBirth: '',
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
const error = ref('')
const success = ref('')
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
    error.value = serviceError(cause)
  } finally {
    loading.value = false
  }
}
async function save(statusOnly = false) {
  if (busy.value) return
  error.value = ''
  success.value = ''
  if (!statusOnly && form.dateOfBirth && form.dateOfBirth >= form.hireDate) {
    error.value = 'Ngày sinh phải trước ngày vào làm.'
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
      success.value = statusOnly ? 'Đã cập nhật trạng thái công việc.' : 'Đã lưu hồ sơ nhân viên.'
    }
  } catch (cause) {
    error.value = serviceError(cause)
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
    <RouterLink to="/employees" class="back-link">← Danh sách nhân viên</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">HỒ SƠ NHÂN SỰ</p>
        <h1>{{ employee ? fullName(employee) : 'Thêm nhân viên' }}</h1>
        <p class="muted">
          {{ employee ? employee.employeeCode : 'Tạo hồ sơ và phân công vị trí trong tổ chức.' }}
        </p>
      </div>
      <span v-if="employee" class="status-chip">{{ accountStatuses[employee.accountStatus] }}</span>
    </div>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
    <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
    <p v-if="loading" role="status">Đang tải hồ sơ…</p>
    <button v-else-if="!loaded" class="button button-secondary" @click="load">Thử lại</button>
    <div v-if="loaded" class="employee-layout">
      <form class="panel create-form" :aria-busy="busy" @submit.prevent="save()">
        <div class="panel-heading">
          <h2>Thông tin nhân viên</h2>
          <span class="small muted">* Bắt buộc</span>
        </div>
        <fieldset class="form-fields form-grid" :disabled="busy">
          <div class="field">
            <label for="employee-code">Mã nhân viên *</label
            ><input
              id="employee-code"
              v-model="form.employeeCode"
              required
              maxlength="50"
              pattern=".*\S.*"
            />
          </div>
          <div class="field">
            <label for="employee-email">Email công việc *</label
            ><input
              id="employee-email"
              v-model="form.email"
              required
              type="email"
              maxlength="255"
            />
          </div>
          <div class="field">
            <label for="last-name">Họ và tên đệm *</label
            ><input
              id="last-name"
              v-model="form.lastName"
              required
              maxlength="100"
              pattern=".*\S.*"
            />
          </div>
          <div class="field">
            <label for="first-name">Tên *</label
            ><input
              id="first-name"
              v-model="form.firstName"
              required
              maxlength="100"
              pattern=".*\S.*"
            />
          </div>
          <div class="field">
            <label for="phone">Số điện thoại</label
            ><input id="phone" v-model="form.phone" type="tel" maxlength="30" />
          </div>
          <div class="field">
            <label for="birth-date">Ngày sinh</label
            ><input id="birth-date" v-model="form.dateOfBirth" type="date" :max="yesterday" />
          </div>
          <div class="field">
            <label for="hire-date">Ngày vào làm *</label
            ><input id="hire-date" v-model="form.hireDate" type="date" required />
          </div>
          <div class="field">
            <label for="employee-status">Trạng thái công việc *</label
            ><select id="employee-status" v-model="form.status" required>
              <option v-for="(label, value) in employeeStatuses" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
          <ReferencePicker
            v-model="form.departmentId"
            resource="departments"
            label="Phòng ban"
            :selected-label="employee?.department?.name"
          />
          <ReferencePicker
            v-model="form.positionId"
            resource="positions"
            label="Chức danh"
            :selected-label="employee?.position?.name"
          />
          <ReferencePicker
            v-model="form.managerId"
            resource="employees"
            label="Người quản lý"
            :exclude="id"
            :selected-label="employee?.manager ? fullName(employee.manager) : ''"
          />
          <div class="form-footer span-all">
            <RouterLink to="/employees" class="button button-secondary">Quay lại</RouterLink
            ><button type="submit" class="button button-primary">
              {{ busy ? 'Đang lưu…' : id ? 'Lưu hồ sơ' : 'Tạo nhân viên' }}
            </button>
          </div>
        </fieldset>
      </form>
      <aside v-if="employee" class="employee-aside">
        <section class="panel create-form">
          <h2>Trạng thái công việc</h2>
          <p class="muted small">
            Cập nhật riêng trạng thái, không lưu các thay đổi khác trong biểu mẫu.
          </p>
          <form @submit.prevent="save(true)">
            <fieldset class="form-fields" :disabled="busy">
              <label for="quick-status">Trạng thái mới</label
              ><select id="quick-status" v-model="nextStatus">
                <option v-for="(label, value) in employeeStatuses" :key="value" :value="value">
                  {{ label }}
                </option></select
              ><button
                type="submit"
                class="button button-secondary full-width"
                :disabled="nextStatus === employee.status"
              >
                Cập nhật trạng thái
              </button>
            </fieldset>
          </form>
          <dl class="details-list">
            <div>
              <dt>Ngày tạo</dt>
              <dd>{{ formatDate(employee.createdAt) }}</dd>
            </div>
            <div>
              <dt>Cập nhật</dt>
              <dd>{{ formatDate(employee.updatedAt) }}</dd>
            </div>
          </dl>
        </section>
        <ProvisioningPanel
          v-if="auth.hasRole(accountCreationRoles)"
          :employee="employee"
          @updated="employee.accountStatus = $event"
        />
      </aside>
    </div>
  </div>
</template>
