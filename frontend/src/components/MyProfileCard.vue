<script setup>
import { computed, nextTick, ref } from 'vue'
import { employeeStatuses, fullName } from '@/hrm/api.js'
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
    { id: 'personal', label: 'Cá nhân' },
    { id: 'work', label: 'Công việc' },
    { id: 'attendance', label: 'Chấm công' },
    { id: 'leave', label: 'Nghỉ phép' },
    { id: 'account', label: 'Tài khoản' },
]
const tabButtons = ref([])
const genders = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }
const displayValue = (value) => value || 'Chưa cập nhật'
const dateOnly = (value) => {
    if (!value) return 'Chưa cập nhật'
    const [year, month, day] = value.split('-')
    return `${day}/${month}/${year}`
}
const statusLabel = computed(() => employeeStatuses[props.employee?.status] || 'Chưa cập nhật')
const personalFields = computed(() =>
    props.employee
        ? [
            ['Họ và tên', fullName(props.employee)],
            ['Ngày sinh', dateOnly(props.employee.dateOfBirth)],
            ['Giới tính', genders[props.employee.gender]],
            ['Điện thoại', props.employee.phone],
            ['Email cá nhân', props.employee.personalEmail],
            ['Địa chỉ', props.employee.address]
        ]
        : [],
)
const workFields = computed(() =>
    props.employee
        ? [
            ['Mã nhân viên', props.employee.employeeCode],
            ['Ngày vào làm', dateOnly(props.employee.hireDate)],
            ['Phòng ban', props.employee.department?.name],
            ['Chức danh', props.employee.position?.name],
            ['Người quản lý', props.employee.manager ? fullName(props.employee.manager) : 'Chưa có'],
            ['Trạng thái làm việc', statusLabel.value],
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
    <section class="panel account-panel employee-profile" aria-label="Hồ sơ của tôi">
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

        <nav class="employee-profile-navigation" aria-label="Các mục hồ sơ">
            <div class="employee-profile-tabs" role="tablist" aria-label="Các mục hồ sơ" @keydown="navigateTabs">
                <button v-for="tab in tabs" :id="`employee-${tab.id}-tab`" :key="tab.id" ref="tabButtons" type="button"
                    role="tab" class="employee-profile-tab" :class="{ selected: activeTab === tab.id }"
                    :aria-selected="activeTab === tab.id" :aria-controls="`employee-${tab.id}-panel`"
                    :tabindex="activeTab === tab.id ? 0 : -1" @click="activeTab = tab.id">
                    {{ tab.label }}
                </button>
            </div>
        </nav>

        <div v-for="tab in tabs" v-show="activeTab === tab.id" :id="`employee-${tab.id}-panel`" :key="tab.id"
            role="tabpanel" :aria-labelledby="`employee-${tab.id}-tab`" tabindex="0">
            <template v-if="(tab.id === 'personal' || tab.id === 'work') && activeTab === tab.id">
                <p v-if="loading" class="muted" role="status">Đang tải thông tin nhân viên…</p>
                <div v-else-if="error" class="alert alert-error" role="alert">
                    <p>{{ error }}</p>
                    <button class="button button-secondary" @click="$emit('retry')">Thử lại</button>
                </div>
                <p v-else-if="!employee" class="muted">Tài khoản chưa được liên kết với hồ sơ nhân viên.</p>
                <dl v-if="employee" class="profile-fields">
                    <div v-for="[label, value] in tab.id === 'personal' ? personalFields : workFields" :key="label">
                        <dt>{{ label }}</dt>
                        <dd>{{ displayValue(value) }}</dd>
                    </div>
                </dl>
                <template v-if="
                    tab.id === 'personal' &&
                    employee &&
                    (employee.contactRelative || employee.contactRelativePhone)
                ">
                    <h3 class="employee-profile-subheading">Liên hệ người thân</h3>
                    <dl class="profile-fields">
                        <div>
                            <dt>Họ và tên</dt>
                            <dd>{{ displayValue(employee.contactRelative) }}</dd>
                        </div>
                        <div>
                            <dt>Điện thoại</dt>
                            <dd>{{ displayValue(employee.contactRelativePhone) }}</dd>
                        </div>
                    </dl>
                </template>
            </template>
            <AuthAccountPanel v-if="tab.id === 'account' && activeTab === 'account'" />
            <ProfileAttendancePanel v-if="tab.id === 'attendance' && activeTab === 'attendance'" />
            <ProfileLeavePanel v-if="tab.id === 'leave' && activeTab === 'leave'" />
        </div>
        <p v-if="employee && (activeTab === 'personal' || activeTab === 'work')" class="employee-profile-note">
            Liên hệ bộ phận nhân sự để cập nhật thông tin của bạn.
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
    background: #e7f2ed;
    color: var(--primary-dark);
    font-weight: 600;
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
