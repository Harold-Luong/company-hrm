<script setup>
import { computed, onMounted, ref } from 'vue'
import { hrm, fullName, employeeStatuses, accountStatuses } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
import PageControls from '@/components/PageControls.vue'
const props = defineProps({ resource: { type: String, required: true } })
const title = computed(
    () =>
        ({ employees: 'Nhân viên', departments: 'Phòng ban', positions: 'Chức danh' })[props.resource],
)
const rows = ref([])
const page = ref(0)
const pages = ref(0)
const total = ref(0)
const busy = ref(false)
const error = ref('')
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
        error.value = serviceError(cause)
    } finally {
        busy.value = false
    }
}
onMounted(() => load())
</script>
<template>
    <div>
        <div class="page-heading">
            <div>
                <p class="eyebrow">QUẢN LÝ NHÂN SỰ</p>
                <h1>{{ title }}</h1>
                <p class="muted">
                    {{
                        resource === 'employees'
                            ? 'Hồ sơ, công việc và trạng thái tài khoản của nhân viên.'
                            : 'Quản lý danh mục tổ chức dùng trong hồ sơ nhân viên.'
                    }}
                </p>
            </div>
            <RouterLink :to="`/${resource}/new`" class="button button-primary">Thêm {{ title.toLowerCase() }}
            </RouterLink>
        </div>
        <section class="panel directory-panel" :aria-busy="busy">
            <div class="list-toolbar">
                <h2>Danh sách {{ title.toLowerCase() }}</h2>
                <button class="button button-secondary" :disabled="busy" @click="load()">
                    {{ busy ? 'Đang tải…' : 'Tải lại' }}
                </button>
            </div>
            <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
            <p v-if="busy" class="table-message" role="status">Đang tải dữ liệu…</p>
            <p v-else-if="!rows.length && !error" class="table-message">
                Chưa có {{ title.toLowerCase() }}. Chọn “Thêm {{ title.toLowerCase() }}” để tạo bản ghi đầu
                tiên.
            </p>
            <div v-if="rows.length" class="table-scroll" tabindex="0" :aria-label="`Bảng ${title.toLowerCase()}`">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th scope="col">Mã</th>
                            <th scope="col">{{ resource === 'employees' ? 'Họ và tên' : 'Tên' }}</th>
                            <template v-if="resource === 'employees'">
                                <th scope="col">Phòng ban / Chức danh</th>
                                <th scope="col">Công việc</th>
                                <th scope="col">Tài khoản</th>
                            </template>
                            <th v-else scope="col">Mô tả</th>
                            <th scope="col">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in rows" :key="row.id">
                            <td class="muted">{{ row.employeeCode || row.code }}</td>
                            <td>
                                <RouterLink class="record-link" :to="`/${resource}/${row.id}`">{{
                                    resource === 'employees' ? fullName(row) : row.name
                                    }}</RouterLink><small v-if="resource === 'employees'" class="cell-secondary">{{
                                        row.personalEmail
                                    }}</small>
                            </td>
                            <template v-if="resource === 'employees'">
                                <td>
                                    {{ row.department?.name || '—'
                                    }}<small class="cell-secondary">{{ row.position?.name || '—' }}</small>
                                </td>
                                <td>
                                    <span class="status-chip" :class="row.status.toLowerCase()">{{
                                        employeeStatuses[row.status] || row.status
                                        }}</span>
                                </td>
                                <td>{{ accountStatuses[row.accountStatus] || row.accountStatus }}</td>
                            </template>
                            <td v-else>{{ row.description || '—' }}</td>
                            <td>
                                <RouterLink class="text-button" :to="`/${resource}/${row.id}`">Xem chi tiết</RouterLink>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <PageControls :page="page" :pages="pages" :total="total" :busy="busy" @change="load" />
        </section>
    </div>
</template>
