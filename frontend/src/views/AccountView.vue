<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { fullName, hrm } from '@/hrm/api.js'
import { errorMessage } from '@/auth/api.js'
import { formatDate } from '@/auth/format.js'
import AppIcon from '@/components/AppIcon.vue'
import RoleBadge from '@/components/RoleBadge.vue'

const router = useRouter()
const user = computed(() => auth.state.user)

const activeTab = ref('account')

const confirming = ref(false)
const busy = ref(false)
const error = ref('')

const employee = ref(null)
const employeeLoading = ref(false)
const employeeError = ref('')

const employeeName = computed(() =>
    employee.value ? fullName(employee.value) : ''
)

const displayValue = (value) =>
    value === null || value === undefined || value === ''
        ? 'Chưa cập nhật'
        : value

async function logoutAll() {
    if (busy.value) return

    busy.value = true
    error.value = ''

    try {
        await auth.logout(true)
        await router.replace({
            name: 'login',
            query: { logout: 'success' }
        })
    } catch (cause) {
        error.value = errorMessage(cause)
    } finally {
        busy.value = false
    }
}

watch(
    () => user.value?.employeeId,
    async (employeeId, _, onCleanup) => {
        let cancelled = false
        onCleanup(() => {
            cancelled = true
        })

        employee.value = null
        employeeError.value = ''
        employeeLoading.value = Boolean(employeeId)

        if (!employeeId) return

        try {
            const result = await hrm.get('employees', employeeId)

            if (!cancelled) {
                employee.value = result
            }
        } catch (cause) {
            if (!cancelled) {
                employeeError.value = errorMessage(cause)
                console.error('Failed to fetch employee:', cause)
            }
        } finally {
            if (!cancelled) {
                employeeLoading.value = false
            }
        }
    },
    { immediate: true }
)
</script>

<template>
    <div v-if="user">
        <div class="page-heading">
            <div>
                <p class="eyebrow">CÁ NHÂN</p>
                <h1>Tài khoản của tôi</h1>
                <p class="muted">
                    Quản lý thông tin tài khoản và hồ sơ cá nhân.
                </p>
            </div>
        </div>

        <!-- Tabs -->
        <div class="profile-tabs" role="tablist" aria-label="Thông tin hồ sơ">
            <button id="account-tab" class="profile-tab" :class="{ active: activeTab === 'account' }" role="tab"
                :aria-selected="activeTab === 'account'" aria-controls="account-content" @click="activeTab = 'account'">
                <AppIcon name="user" :size="18" />
                Thông tin tài khoản
            </button>

            <button id="personal-tab" class="profile-tab" :class="{ active: activeTab === 'personal' }" role="tab"
                :aria-selected="activeTab === 'personal'" aria-controls="personal-content"
                @click="activeTab = 'personal'">
                <AppIcon name="users" :size="18" />
                Thông tin cá nhân
            </button>
        </div>

        <!-- TAB 1: ACCOUNT -->
        <div v-if="activeTab === 'account'" id="account-content" role="tabpanel" aria-labelledby="account-tab"
            class="account-grid">
            <section class="panel account-panel">
                <div class="panel-heading">
                    <h2>Thông tin tài khoản</h2>

                    <span class="status-badge">
                        <span class="status-dot"></span>
                        Đang hoạt động
                    </span>
                </div>

                <div class="account-identity">
                    <span class="avatar avatar-large">
                        {{ user.email.slice(0, 2).toUpperCase() }}
                    </span>

                    <div>
                        <h3>{{ employeeName || user.email }}</h3>
                        <p class="muted">
                            Tài khoản nội bộ · #{{ user.id }}
                        </p>
                    </div>
                </div>

                <dl class="details-list">
                    <div>
                        <dt>Email đăng nhập</dt>
                        <dd>{{ user.email }}</dd>
                    </div>

                    <div>
                        <dt>Vai trò</dt>
                        <dd class="role-list">
                            <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
                        </dd>
                    </div>

                    <div>
                        <dt>Ngày tạo tài khoản</dt>
                        <dd>{{ formatDate(user.createdAt) }}</dd>
                    </div>

                    <div>
                        <dt>Cập nhật gần nhất</dt>
                        <dd>{{ formatDate(user.updatedAt) }}</dd>
                    </div>

                    <div>
                        <dt>Đăng nhập gần nhất</dt>
                        <dd>{{ formatDate(user.lastLoginAt) }}</dd>
                    </div>
                </dl>
            </section>

            <div class="account-aside">
                <section class="panel security-panel">
                    <span class="metric-icon green">
                        <AppIcon name="shield" :size="23" />
                    </span>

                    <h2>Bảo mật phiên truy cập</h2>

                    <p class="muted">
                        Thu hồi phiên đăng nhập trên các thiết bị,
                        bao gồm phiên hiện tại.
                    </p>

                    <p class="small muted">
                        Các phiên khác sẽ cần đăng nhập lại khi làm
                        mới phiên. Quyền truy cập đã cấp có thể còn
                        hiệu lực tối đa 15 phút theo cấu hình hiện tại.
                    </p>

                    <div v-if="error" class="alert alert-error" role="alert">
                        {{ error }}
                    </div>

                    <div v-if="confirming" class="confirm-box">
                        <p>Bạn muốn đăng xuất tất cả phiên?</p>

                        <div class="button-row">
                            <button class="button button-danger" :disabled="busy" @click="logoutAll">
                                {{ busy ? 'Đang xử lý…' : 'Xác nhận đăng xuất' }}
                            </button>

                            <button class="button button-secondary" :disabled="busy" @click="confirming = false">
                                Hủy
                            </button>
                        </div>
                    </div>

                    <button v-else class="button button-danger-outline full-width" @click="confirming = true">
                        <AppIcon name="logout" :size="17" />
                        Đăng xuất tất cả phiên
                    </button>
                </section>

                <div class="info-strip align-start">
                    <AppIcon name="info" />
                    <p>
                        Để thay đổi quyền truy cập, vui lòng liên hệ
                        quản trị viên.
                    </p>
                </div>
            </div>
        </div>

        <!-- TAB 2: PERSONAL -->
        <!-- TAB: PERSONAL -->
        <div v-else id="personal-content" role="tabpanel" aria-labelledby="personal-tab" class="account-grid">
            <section class="panel account-panel">
                <div class="panel-heading">
                    <h2>Thông tin cá nhân</h2>
                    <span v-if="employee" class="status-badge">
                        <span class="status-dot"></span>
                        {{ employee.status === 'ACTIVE' ? 'Đang làm việc' : 'Không hoạt động' }}
                    </span>
                </div>

                <p v-if="employeeLoading" class="muted">
                    Đang tải thông tin nhân viên...
                </p>

                <div v-else-if="employeeError" class="alert alert-error" role="alert">
                    {{ employeeError }}
                </div>

                <div v-else-if="employee">
                    <div class="account-identity">
                        <span class="avatar avatar-large">
                            {{ (employee.firstName || 'NV').slice(0, 2).toUpperCase() }}
                        </span>
                        <div>
                            <h3>{{ employeeName || 'Chưa cập nhật' }}</h3>
                            <p class="muted">
                                Nhân viên · {{ employee.employeeCode }}
                            </p>
                        </div>
                    </div>

                    <h3 class="detail-section-title">Thông tin cơ bản</h3>

                    <dl class="details-list">
                        <div>
                            <dt>Mã nhân viên</dt>
                            <dd>{{ displayValue(employee.employeeCode) }}</dd>
                        </div>
                        <div>
                            <dt>Họ</dt>
                            <dd>{{ displayValue(employee.lastName) }}</dd>
                        </div>
                        <div>
                            <dt>Tên</dt>
                            <dd>{{ displayValue(employee.firstName) }}</dd>
                        </div>
                        <div>
                            <dt>Email</dt>
                            <dd>{{ displayValue(employee.email) }}</dd>
                        </div>
                        <div>
                            <dt>Số điện thoại</dt>
                            <dd>{{ displayValue(employee.phone) }}</dd>
                        </div>
                        <div>
                            <dt>Ngày sinh</dt>
                            <dd>
                                {{ employee.dateOfBirth
                                    ? formatDate(employee.dateOfBirth)
                                    : 'Chưa cập nhật' }}
                            </dd>
                        </div>
                    </dl>

                    <h3 class="detail-section-title">Thông tin công việc</h3>

                    <dl class="details-list">
                        <div>
                            <dt>Phòng ban</dt>
                            <dd>{{ displayValue(employee.department?.name) }}</dd>
                        </div>
                        <div>
                            <dt>Chức vụ</dt>
                            <dd>{{ displayValue(employee.position?.name) }}</dd>
                        </div>
                        <div>
                            <dt>Người quản lý</dt>
                            <dd>
                                {{ employee.manager
                                    ? fullName(employee.manager) || 'Chưa cập nhật'
                                    : 'Chưa có' }}
                            </dd>
                        </div>
                        <div>
                            <dt>Ngày vào làm</dt>
                            <dd>
                                {{ employee.hireDate
                                    ? formatDate(employee.hireDate)
                                    : 'Chưa cập nhật' }}
                            </dd>
                        </div>
                        <div>
                            <dt>Trạng thái làm việc</dt>
                            <dd>
                                {{ employee.status === 'ACTIVE'
                                    ? 'Đang làm việc'
                                    : displayValue(employee.status) }}
                            </dd>
                        </div>
                    </dl>
                </div>

                <p v-else class="muted">
                    Tài khoản chưa được liên kết với hồ sơ nhân viên.
                </p>
            </section>

            <div class="account-aside">
                <section class="panel security-panel">
                    <span class="metric-icon green">
                        <AppIcon name="info" :size="23" />
                    </span>
                    <h2>Hồ sơ nhân viên</h2>
                    <p class="muted">
                        Thông tin được đồng bộ từ hệ thống quản lý nhân sự.
                    </p>

                    <dl v-if="employee" class="details-list">
                        <div>
                            <dt>Mã phòng ban</dt>
                            <dd>{{ displayValue(employee.department?.code) }}</dd>
                        </div>
                        <div>
                            <dt>Mã chức vụ</dt>
                            <dd>{{ displayValue(employee.position?.code) }}</dd>
                        </div>
                        <div>
                            <dt>Cập nhật hồ sơ</dt>
                            <dd>{{ formatDate(employee.updatedAt) }}</dd>
                        </div>
                    </dl>
                </section>

                <div class="info-strip align-start">
                    <AppIcon name="info" />
                    <p>
                        Để điều chỉnh thông tin nhân viên, vui lòng liên hệ
                        bộ phận nhân sự (HR).
                    </p>0
                </div>
            </div>
        </div>
    </div>
</template>
<style lang="css" scoped>
.profile-tabs {
    display: flex;
    align-items: center;
    gap: 24px;
    border-bottom: 1px solid var(--border, #e5e7eb);
    margin-bottom: 24px;
    overflow-x: auto;
}

.profile-tab {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 12px 4px;
    background: transparent;
    border: 0;
    border-bottom: 2px solid transparent;
    color: var(--text-muted, #64748b);
    font: inherit;
    font-weight: 500;
    cursor: pointer;
    white-space: nowrap;
    transition: color 0.2s, border-color 0.2s;
}

.profile-tab:hover {
    color: var(--text-primary, #111827);
}

.profile-tab.active {
    color: var(--primary, #2563eb);
    border-bottom-color: var(--primary, #2563eb);
    font-weight: 600;
}

.profile-tab:focus-visible {
    outline: 2px solid var(--primary, #2563eb);
    outline-offset: -2px;
}

.detail-section-title {
    font-size: 14px;
    font-weight: 600;
    margin: 28px 0 12px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--border, #e5e7eb);
}

.detail-section-title:first-of-type {
    margin-top: 20px;
}
</style>
