<script setup>
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
    size: 20,
})
const applied = ref({ ...filters })
const result = ref({ content: [], page: 0, totalPages: 0, totalElements: 0 })
const sortedEvents = computed(() => sortManagedEvents(result.value.content))
const busy = ref(false)
const error = ref('')
async function load(page = 0, query = applied.value) {
    if (busy.value) return
    const days =
        (Date.parse(`${query.to}T00:00:00Z`) - Date.parse(`${query.from}T00:00:00Z`)) / 86400000
    if (!Number.isFinite(days) || days < 0 || days > 365) {
        error.value = 'Chọn khoảng ngày hợp lệ, tối đa 366 ngày.'
        return
    }
    busy.value = true
    error.value = ''
    try {
        result.value = await calendarManagement.list(query, page)
        applied.value = { ...query }
    } catch (cause) {
        error.value = calendarManagementError(cause)
    } finally {
        busy.value = false
    }
}
onMounted(() => load())
</script>

<template>
    <div class="calendar-management">
        <RouterLink to="/calendar" class="back-link">← Xem lịch nhân viên</RouterLink>
        <div class="page-heading">
            <div>
                <p class="eyebrow">LỊCH CÔNG TY</p>
                <h1>Quản lý lịch</h1>
                <p class="muted">Chuẩn bị bản nháp, công bố và cập nhật sự kiện cho toàn thể nhân viên.</p>
            </div>
            <RouterLink to="/calendar-events/new" class="button button-primary">Tạo sự kiện</RouterLink>
        </div>
        <form class="panel calendar-filters" @submit.prevent="load(0, { ...filters })">
            <fieldset class="form-fields calendar-filter-grid" :disabled="busy">
                <div class="field">
                    <label for="event-from">Từ ngày</label><input id="event-from" v-model="filters.from" type="date"
                        required />
                </div>
                <div class="field">
                    <label for="event-to">Đến ngày</label><input id="event-to" v-model="filters.to" type="date"
                        :min="filters.from" required />
                </div>
                <div class="field">
                    <label for="event-type-filter">Loại sự kiện</label><select id="event-type-filter"
                        v-model="filters.type">
                        <option value="">Tất cả loại</option>
                        <option v-for="(label, value) in eventTypes" :key="value" :value="value">
                            {{ label }}
                        </option>
                    </select>
                </div>
                <div class="field">
                    <label for="event-status-filter">Trạng thái</label><select id="event-status-filter"
                        v-model="filters.status">
                        <option value="">Tất cả trạng thái</option>
                        <option v-for="(label, value) in eventStatuses" :key="value" :value="value">
                            {{ label }}
                        </option>
                    </select>
                </div>
                <div class="field">
                    <label for="event-page-size">Số dòng</label><select id="event-page-size"
                        v-model.number="filters.size">
                        <option v-for="size in [10, 20, 50, 100]" :key="size" :value="size">{{ size }}</option>
                    </select>
                </div>
                <button class="button button-secondary" type="submit">Áp dụng</button>
            </fieldset>
            <p class="field-help">
                Tìm sự kiện có thời gian giao với khoảng đã chọn, tối đa 366 ngày. Giờ Việt Nam (UTC+7). Ưu
                tiên bản nháp, sau đó ngày bắt đầu gần hôm nay nhất.
            </p>
        </form>
        <section class="panel directory-panel" :aria-busy="busy">
            <div class="list-toolbar">
                <h2>Danh sách sự kiện</h2>
                <button class="button button-secondary" :disabled="busy" @click="load(result.page)">
                    Tải lại
                </button>
            </div>
            <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
            <p v-if="busy" role="status" class="table-message">Đang tải sự kiện…</p>
            <p v-else-if="!error && !result.content.length" role="status" class="table-message">
                Chưa có sự kiện phù hợp. Thử đổi bộ lọc hoặc tạo sự kiện mới.
            </p>
            <div v-if="!busy && !error && result.content.length" class="table-scroll" tabindex="0"
                aria-label="Danh sách sự kiện">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th scope="col">Sự kiện</th>
                            <th scope="col">Thời gian</th>
                            <th scope="col">Trạng thái</th>
                            <th scope="col">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="event in sortedEvents" :key="event.id">
                            <td>
                                <RouterLink class="record-link" :to="`/calendar-events/${event.id}`">{{
                                    event.title
                                    }}</RouterLink><small class="cell-secondary">{{ eventTypes[event.type] || event.type
                                    }}</small>
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
                                <RouterLink class="text-button" :to="`/calendar-events/${event.id}`">Xem chi tiết
                                </RouterLink>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <PageControls :page="result.page" :pages="result.totalPages" :total="result.totalElements" :busy="busy"
                @change="load($event)" />
        </section>
    </div>
</template>

<style scoped src="@/assets/styles/calendar-management.css"></style>
