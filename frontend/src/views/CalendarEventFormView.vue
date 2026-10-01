<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ApiError } from '@/auth/api.js'
import { auth } from '@/auth/session.js'
import { calendarManagement } from '@/calendar/api.js'
import {
    calendarManagementRoles,
    eventTypes,
    holidayKinds,
    eventStatuses,
} from '@/calendar/constants.js'
import { displayEventRange } from '@/calendar/utils.js'
import {
    newEventForm,
    eventToForm,
    eventPayload,
    eventActions,
    calendarManagementError,
} from '@/calendar/management.js'

const props = defineProps({ id: { type: String, default: '' } })
const router = useRouter()
const form = reactive(newEventForm())
const baseline = ref(JSON.stringify(form))
const event = ref(null)
const loading = ref(!!props.id)
const loaded = ref(!props.id)
const busy = ref(false)
const error = ref('')
const success = ref('')
const blocked = ref(false)
const uncertain = ref(false)
const now = ref(Date.now())
const dialog = ref(null)
const action = ref('')
const cancelReason = ref('')
const dialogError = ref('')
const dirty = computed(() => JSON.stringify(form) !== baseline.value)
const allowed = computed(() => eventActions(event.value, now.value))
const canManage = computed(() => auth.hasRole(calendarManagementRoles))
const locked = computed(
    () => busy.value || loading.value || blocked.value || uncertain.value || !canManage.value,
)
const published = computed(() => event.value?.status === 'PUBLISHED')
const actionLabel = computed(
    () =>
        ({ publish: 'Công bố sự kiện', cancel: 'Hủy sự kiện', remove: 'Xóa bản nháp' })[action.value],
)
let timer

watch(
    () => form.type,
    (type) => {
        if (type === 'HOLIDAY') {
            form.allDay = true
            if (!form.holidayKind) form.holidayKind = 'PUBLIC_HOLIDAY'
        } else form.holidayKind = ''
    },
    { flush: 'sync' },
)

function accept(data) {
    event.value = data
    Object.assign(form, eventToForm(data))
    baseline.value = JSON.stringify(form)
    blocked.value = false
    uncertain.value = false
    now.value = Date.now()
}
async function load() {
    if (busy.value || (loading.value && loaded.value)) return
    if (dirty.value && !window.confirm('Tải lại sẽ bỏ các thay đổi chưa lưu. Tiếp tục?')) return
    loading.value = true
    error.value = ''
    success.value = ''
    try {
        accept(await calendarManagement.get(props.id))
        loaded.value = true
    } catch (cause) {
        error.value = calendarManagementError(cause)
        blocked.value = true
    } finally {
        loading.value = false
    }
}
function fail(cause, mutation = false) {
    error.value = calendarManagementError(cause)
    if (cause instanceof ApiError) {
        if ([401, 403, 404, 409, 412, 428].includes(cause.status)) blocked.value = true
        if (mutation && (cause.status === 0 || cause.status >= 500)) {
            uncertain.value = true
            error.value =
                'Chưa xác định được kết quả thao tác. Kiểm tra lại dữ liệu trước khi gửi yêu cầu khác.'
        }
    }
}
async function save() {
    if (locked.value || !allowed.value.edit) return
    error.value = ''
    success.value = ''
    let payload
    try {
        payload = eventPayload(form)
        if (published.value && !payload.reason)
            throw new Error('Vui lòng nhập lý do thay đổi sự kiện đã công bố.')
    } catch (cause) {
        fail(cause)
        return
    }
    busy.value = true
    try {
        const data = props.id
            ? await calendarManagement.update(props.id, event.value.version, payload)
            : await calendarManagement.create(payload)
        accept(data)
        if (!props.id) await router.replace(`/calendar-events/${data.id}`)
        else success.value = 'Đã lưu thay đổi.'
    } catch (cause) {
        fail(cause, true)
    } finally {
        busy.value = false
    }
}
async function openAction(value) {
    now.value = Date.now()
    if (locked.value || dirty.value || !allowed.value[value]) return
    action.value = value
    dialogError.value = ''
    cancelReason.value = ''
    error.value = ''
    await nextTick()
    dialog.value.showModal()
}
function closeDialog() {
    if (busy.value) return
    dialog.value.close()
    action.value = ''
}
async function performAction() {
    now.value = Date.now()
    if (locked.value || dirty.value) return
    if (!allowed.value[action.value]) {
        dialogError.value =
            'Thao tác không còn phù hợp với thời gian của sự kiện. Quay lại để kiểm tra.'
        return
    }
    const selectedAction = action.value
    if (selectedAction === 'cancel' && !cancelReason.value.trim()) {
        dialogError.value = 'Vui lòng nhập lý do hủy.'
        return
    }
    busy.value = true
    error.value = ''
    success.value = ''
    try {
        const data = await calendarManagement[selectedAction](
            props.id,
            event.value.version,
            cancelReason.value.trim(),
        )
        dialog.value.close()
        action.value = ''
        if (selectedAction === 'remove') {
            baseline.value = JSON.stringify(form)
            // Permit the successful navigation through the unsaved-change guard.
            busy.value = false
            await router.replace('/calendar-events')
        } else {
            accept(data)
            success.value =
                selectedAction === 'publish' ? 'Đã công bố sự kiện cho nhân viên.' : 'Đã hủy sự kiện.'
        }
    } catch (cause) {
        dialog.value.close()
        action.value = ''
        fail(cause, true)
    } finally {
        busy.value = false
    }
}
function beforeUnload(event) {
    if (dirty.value || busy.value) {
        event.preventDefault()
        event.returnValue = ''
    }
}
onBeforeRouteLeave(() => {
    // Creation has succeeded once the server event is accepted.
    if (busy.value && !(event.value && !props.id && !dirty.value)) return false
    return !dirty.value || window.confirm('Bạn có thay đổi chưa lưu. Rời trang và bỏ thay đổi?')
})
onMounted(() => {
    if (props.id) load()
    timer = setInterval(() => {
        now.value = Date.now()
    }, 30000)
    window.addEventListener('beforeunload', beforeUnload)
})
onUnmounted(() => {
    clearInterval(timer)
    window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
    <div class="calendar-management">
        <RouterLink class="back-link" to="/calendar-events">← Danh sách sự kiện</RouterLink>
        <div class="page-heading">
            <div>
                <p class="eyebrow">LỊCH CÔNG TY</p>
                <h1>{{ id ? 'Chi tiết sự kiện' : 'Tạo sự kiện' }}</h1>
                <p class="muted">
                    {{
                        id
                            ? 'Theo dõi và cập nhật thông tin sự kiện.'
                            : 'Lưu bản nháp trước khi công bố cho nhân viên.'
                    }}
                </p>
            </div>
            <span v-if="event" class="event-status" :class="`status-${event.status}`">{{
                eventStatuses[event.status]
                }}</span>
        </div>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <div v-if="success" class="alert alert-success" role="status">{{ success }}</div>
        <p v-if="loading" class="panel create-form" role="status">Đang tải sự kiện…</p>
        <div v-if="(blocked || uncertain || !loaded) && !loading" class="calendar-recovery">
            <button v-if="id" class="button button-secondary" :disabled="busy" @click="load">
                Tải lại sự kiện
            </button>
            <RouterLink v-else to="/calendar-events" class="button button-secondary">Kiểm tra danh sách sự kiện
            </RouterLink>
        </div>
        <div v-if="loaded && !loading" class="calendar-editor-layout">
            <form class="panel create-form" :aria-busy="busy" @submit.prevent="save">
                <h2>Thông tin sự kiện</h2>
                <p v-if="event && !allowed.edit" class="field-help">
                    {{
                        event.status === 'CANCELLED'
                            ? 'Sự kiện đã hủy chỉ được xem.'
                            : 'Sự kiện đã bắt đầu chỉ được xem.'
                    }}
                </p>
                <fieldset class="form-fields form-grid" :disabled="locked || !allowed.edit">
                    <div class="field span-all">
                        <label for="event-title">Tiêu đề *</label><input id="event-title" v-model="form.title" required
                            maxlength="255" pattern=".*\S.*" />
                    </div>
                    <div class="field">
                        <label for="event-type">Loại sự kiện *</label><select id="event-type" v-model="form.type"
                            :disabled="published">
                            <option v-for="(label, value) in eventTypes" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div v-if="form.type === 'HOLIDAY'" class="field">
                        <label for="event-holiday-kind">Loại ngày nghỉ *</label><select id="event-holiday-kind"
                            v-model="form.holidayKind" required :disabled="published">
                            <option v-for="(label, value) in holidayKinds" :key="value" :value="value">
                                {{ label }}
                            </option>
                        </select>
                    </div>
                    <div class="field span-all">
                        <label class="calendar-checkbox" for="event-all-day"><input id="event-all-day"
                                v-model="form.allDay" type="checkbox"
                                :disabled="published || form.type === 'HOLIDAY'" />Cả ngày</label>
                        <p class="field-help">Giờ Việt Nam (UTC+7). Ngày nghỉ luôn là sự kiện cả ngày.</p>
                    </div>
                    <template v-if="form.allDay">
                        <div class="field">
                            <label for="event-start-date">Ngày bắt đầu *</label><input id="event-start-date"
                                v-model="form.startDate" type="date" required />
                        </div>
                        <div class="field">
                            <label for="event-end-date">Ngày kết thúc *</label><input id="event-end-date"
                                v-model="form.endDate" type="date" :min="form.startDate" required />
                            <p class="field-help">Bao gồm cả ngày kết thúc.</p>
                        </div>
                    </template>
                    <template v-else>
                        <div class="field">
                            <label for="event-start-time">Bắt đầu lúc *</label><input id="event-start-time"
                                v-model="form.startTime" type="datetime-local" step="0.001" required />
                        </div>
                        <div class="field">
                            <label for="event-end-time">Kết thúc lúc *</label><input id="event-end-time"
                                v-model="form.endTime" type="datetime-local" step="0.001" :min="form.startTime"
                                required />
                        </div>
                    </template>
                    <div class="field span-all">
                        <label for="event-location">Địa điểm</label><input id="event-location" v-model="form.location"
                            maxlength="255" />
                    </div>
                    <div class="field span-all">
                        <label for="event-description">Mô tả</label><textarea id="event-description"
                            v-model="form.description" rows="5" maxlength="10000"></textarea>
                    </div>
                    <div v-if="published && allowed.edit" class="field span-all">
                        <label for="event-reason">Lý do thay đổi *</label><textarea id="event-reason"
                            v-model="form.reason" required maxlength="1000" rows="3"></textarea>
                        <p class="field-help">
                            Được lưu trong lịch sử quản lý, không hiển thị trên lịch nhân viên.
                        </p>
                    </div>
                    <div v-if="allowed.edit" class="form-footer span-all">
                        <span class="field-help">{{
                            dirty ? 'Có thay đổi chưa lưu.' : 'Thông tin đã được đồng bộ.'
                            }}</span><button class="button button-primary" type="submit" :disabled="!!id && !dirty">
                            {{ busy ? 'Đang xử lý…' : id ? 'Lưu thay đổi' : 'Lưu bản nháp' }}
                        </button>
                    </div>
                </fieldset>
            </form>
            <aside class="calendar-editor-aside">
                <section class="panel create-form">
                    <h2>Hiển thị trên lịch</h2>
                    <p class="muted">
                        {{
                            !event || event.status === 'DRAFT'
                                ? 'Bản nháp chỉ hiển thị trong trang quản lý. Công bố để nhân viên xem trên lịch.'
                                : event.status === 'PUBLISHED'
                                    ? 'Nhân viên đang xem được sự kiện này trên lịch.'
                                    : 'Sự kiện vẫn hiện trên lịch với nhãn Đã hủy và không còn hiệu lực.'
                        }}
                    </p>
                    <p v-if="event" class="small">{{ displayEventRange(event) }}</p>
                    <p class="field-help">Phạm vi: toàn thể nhân viên.</p>
                    <template v-if="event">
                        <p v-if="dirty" class="field-help">Lưu thay đổi trước khi thực hiện thao tác khác.</p>
                        <div class="calendar-lifecycle-actions">
                            <button v-if="event.status === 'DRAFT'" class="button button-primary"
                                :disabled="locked || dirty || !allowed.publish" @click="openAction('publish')">
                                Công bố
                            </button>
                            <p v-if="event.status === 'DRAFT' && !allowed.publish" class="field-help">
                                Cập nhật thời gian trước khi công bố sự kiện trong quá khứ.
                            </p>
                            <button v-if="allowed.cancel" class="button button-danger-outline"
                                :disabled="locked || dirty" @click="openAction('cancel')">
                                Hủy sự kiện
                            </button>
                            <button v-if="allowed.remove" class="button button-danger-outline"
                                :disabled="locked || dirty" @click="openAction('remove')">
                                Xóa bản nháp
                            </button>
                            <button class="button button-secondary" :disabled="busy" @click="load">
                                Tải lại dữ liệu
                            </button>
                        </div>
                    </template>
                </section>
            </aside>
        </div>
        <dialog ref="dialog" class="calendar-action-dialog" aria-labelledby="calendar-action-title"
            @cancel="busy ? $event.preventDefault() : (action = '')">
            <form @submit.prevent="performAction">
                <h2 id="calendar-action-title">{{ actionLabel }}</h2>
                <p v-if="dialogError" class="alert alert-error" role="alert">{{ dialogError }}</p>
                <p>
                    <strong>{{ event?.title }}</strong>
                </p>
                <p v-if="event" class="muted">{{ displayEventRange(event) }}</p>
                <p v-if="action === 'publish'">Sự kiện sẽ hiển thị trên lịch của toàn thể nhân viên.</p>
                <p v-else-if="action === 'remove'">
                    Bản nháp sẽ bị xóa và không thể khôi phục. Lịch sử thao tác vẫn được lưu.
                </p>
                <p v-else>Sự kiện sẽ được đánh dấu đã hủy trên lịch nhân viên.</p>
                <fieldset class="form-fields" :disabled="busy">
                    <div v-if="action === 'cancel'" class="field">
                        <label for="cancel-reason">Lý do hủy *</label><textarea id="cancel-reason"
                            v-model="cancelReason" required maxlength="1000" rows="3"></textarea>
                    </div>
                    <div class="form-footer">
                        <button type="button" class="button button-secondary" @click="closeDialog">
                            Quay lại</button><button type="submit" class="button"
                            :class="action === 'publish' ? 'button-primary' : 'button-danger'">
                            {{ busy ? 'Đang xử lý…' : actionLabel }}
                        </button>
                    </div>
                </fieldset>
            </form>
        </dialog>
    </div>
</template>

<style scoped src="@/assets/styles/calendar-management.css"></style>
