<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { ApiError, errorMessage } from '@/auth/api.js'
import { roles, roleLabels } from '@/auth/roles.js'
import AppIcon from '@/components/AppIcon.vue'
const router = useRouter()
const form = reactive({
    email: '',
    password: '',
    employeeId: '',
    roles: ['EMPLOYEE'],
})
const busy = ref(false)
const error = ref('')
const success = ref('')
const roleDescriptions = {
    EMPLOYEE: 'Truy cập không gian cá nhân.',
    MANAGER: 'Vai trò quản lý của doanh nghiệp.',
    HR: 'Cấp tài khoản nội bộ theo quyền nhân sự.',
    ADMIN: 'Cấp tài khoản nội bộ theo quyền quản trị.',
}
async function submit() {
    if (busy.value) return
    error.value = ''
    success.value = ''
    if (!form.roles.length) {
        error.value = 'Vui lòng chọn ít nhất một vai trò.'
        return
    }
    if (!form.password.trim()) {
        error.value = 'Mật khẩu không được chỉ chứa khoảng trắng.'
        return
    }
    if (
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(form.employeeId.trim())
    ) {
        error.value = 'UUID liên kết phải đúng định dạng, ví dụ 550e8400-e29b-41d4-a716-446655440000.'
        return
    }
    busy.value = true
    try {
        await auth.register({
            ...form,
            email: form.email.trim(),
            employeeId: form.employeeId.trim(),
            roles: [...form.roles],
        })
        success.value = `Đã tạo tài khoản ${form.email.trim()} thành công.`
        form.email = ''
        form.password = ''
        form.employeeId = ''
        form.roles = ['EMPLOYEE']
    } catch (cause) {
        if (cause instanceof ApiError && cause.status === 403) {
            await router.replace({ name: 'forbidden' })
        } else {
            error.value = errorMessage(cause)
        }
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <div>
        <div class="page-heading">
            <div>
                <p class="eyebrow">QUẢN TRỊ TRUY CẬP</p>
                <h1>Tạo tài khoản</h1>
                <p class="muted">Cấp quyền truy cập workspace cho một tài khoản nội bộ mới.</p>
            </div>
            <span class="permission-label">
                <AppIcon name="shield" :size="16" />HR / Admin
            </span>
        </div>
        <div class="create-account-grid">
            <form class="panel create-form" :aria-busy="busy" @submit.prevent="submit">
                <div class="panel-heading">
                    <h2>Thông tin đăng nhập</h2>
                    <span class="small muted">* Thông tin bắt buộc</span>
                </div>
                <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
                <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
                <fieldset class="form-fields" :disabled="busy">
                    <label for="new-email">Email công việc <span class="required">*</span></label><input id="new-email"
                        v-model="form.email" type="email" autocomplete="off" maxlength="255" required
                        placeholder="ten@congty.com" />
                    <label for="new-password">Mật khẩu ban đầu <span class="required">*</span></label><input
                        id="new-password" v-model="form.password" type="password" autocomplete="new-password" required
                        placeholder="Nhập mật khẩu cho tài khoản mới" />
                    <label for="employee-reference">UUID liên kết <span class="required">*</span></label><input
                        id="employee-reference" v-model="form.employeeId" type="text" required
                        placeholder="550e8400-e29b-41d4-a716-446655440000" aria-describedby="reference-help" />
                    <p id="reference-help" class="field-help">
                        Nhập Employee ID hiện có do người phụ trách cung cấp. Thao tác này chỉ tạo tài khoản
                        đăng nhập.
                    </p>
                    <fieldset class="role-fieldset">
                        <legend>Vai trò truy cập <span class="required">*</span></legend>
                        <p class="field-help">Một tài khoản có thể được cấp nhiều vai trò.</p>
                        <div class="role-options">
                            <label v-for="role in roles" :key="role" class="role-option"
                                :class="{ selected: form.roles.includes(role) }"><input v-model="form.roles"
                                    type="checkbox" :value="role" /><span><strong>{{ roleLabels[role]
                                        }}</strong><small>{{ roleDescriptions[role] }}</small></span></label>
                        </div>
                    </fieldset>
                    <div class="form-footer">
                        <span class="muted small">Thông tin được lưu khi bạn tạo tài khoản.</span><button
                            class="button button-primary" type="submit">
                            <AppIcon name="add" :size="18" />{{ busy ? 'Đang tạo…' : 'Tạo tài khoản' }}
                        </button>
                    </div>
                </fieldset>
            </form>
            <aside class="create-aside">
                <section class="panel guidance-panel">
                    <span class="metric-icon violet">
                        <AppIcon name="shield" :size="23" />
                    </span>
                    <h2>Cấp quyền phù hợp</h2>
                    <p>Chỉ chọn những vai trò cần thiết cho công việc của người dùng.</p>
                    <ul>
                        <li>Tài khoản mới được kích hoạt ngay sau khi tạo thành công.</li>
                        <li>Mỗi UUID liên kết chỉ có một tài khoản.</li>
                        <li>Quản trị viên không tự động có quyền của HR.</li>
                    </ul>
                </section>
                <div class="info-strip align-start">
                    <AppIcon name="info" />
                    <p>Việc tạo tài khoản không thay đổi phiên đăng nhập của bạn.</p>
                </div>
            </aside>
        </div>
    </div>
</template>
